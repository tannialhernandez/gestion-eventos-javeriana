package com.javeriana.eventos.inscription.application;

import com.javeriana.eventos.inscription.domain.events.InscripcionCreadaEvent;
import com.javeriana.eventos.inscription.domain.events.InscripcionConfirmadaEvent;
import com.javeriana.eventos.inscription.domain.events.PayloadEventoDominio;
import com.javeriana.eventos.inscription.domain.model.EstadoInscripcion;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.in.CrearInscripcionUseCase;
import com.javeriana.eventos.inscription.domain.port.out.*;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import com.javeriana.eventos.inscription.infrastructure.observability.MdcKeys;
import com.javeriana.eventos.shared.domain.DomainEvent;
import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta la creación de una inscripción.
 *
 * Flujo (T=0):
 *  1. Verificar idempotencia
 *  2. Verificar que el evento acepta inscripciones
 *  3. Obtener precio real de la tarifa (M-03 cerrado en Prompt 14)
 *  4. Crear el agregado → registra InscripcionCreadaEvent (ADR-020)
 *  5. Persistir con SELECT FOR UPDATE (reserva atómica de cupo)
 *  6. Solicitar preferencia de pago
 *  7. Persistir InscripcionCreadaEvent en outbox con checkoutUrl y monto
 *  8. Retornar checkout_url al cliente
 *
 * M-04 (ADR-001): sin Jackson en esta capa; serialización via EventoSerializadorPort.
 */
@Service
@Transactional
public class CrearInscripcionService implements CrearInscripcionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CrearInscripcionService.class);

    private final InscripcionRepository  inscripcionRepository;
    private final EventoServicePort      eventoService;
    private final PaymentServicePort     paymentService;
    private final OutboxEventRepository  outboxRepository;
    private final EventoSerializadorPort serializador;

    public CrearInscripcionService(InscripcionRepository inscripcionRepository,
                                   EventoServicePort eventoService,
                                   PaymentServicePort paymentService,
                                   OutboxEventRepository outboxRepository,
                                   EventoSerializadorPort serializador) {
        this.inscripcionRepository = inscripcionRepository;
        this.eventoService         = eventoService;
        this.paymentService        = paymentService;
        this.outboxRepository      = outboxRepository;
        this.serializador          = serializador;
    }

    @Override
    public Result crear(Command command) {
        // 1. Idempotencia
        Optional<Inscripcion> existente =
            inscripcionRepository.buscarPorIdempotencyKey(command.idempotencyKey());
        if (existente.isPresent()) {
            log.info("Inscripción duplicada para idempotencyKey={}", command.idempotencyKey());
            return new Result(existente.get(), null, calcularSegundosRestantes(existente.get()));
        }

        Optional<Inscripcion> existentePorEvento =
            inscripcionRepository.buscarPorUsuarioIdYEventoId(command.usuarioId(), command.eventoId());
        if (existentePorEvento.isPresent()) {
            return continuarPagoExistente(
                existentePorEvento.get(), command.tarifaId(), command.idempotencyKey());
        }

        // 2. Verificar que el evento acepta inscripciones
        EventoServicePort.EventoInfo evento = eventoService.obtenerEvento(command.eventoId());
        if (!evento.aceptaInscripciones()) {
            throw new IllegalStateException(
                "El evento '" + evento.titulo() + "' no acepta inscripciones. Estado: " + evento.estado());
        }

        // 3. Obtener precio real de la tarifa (M-03)
        EventoServicePort.TarifaInfo tarifa = eventoService.obtenerTarifa(command.tarifaId());

        // 4. Crear el agregado — registra InscripcionCreadaEvent internamente
        Inscripcion inscripcion = new Inscripcion(
            UUID.randomUUID(),
            command.usuarioId(),
            command.eventoId(),
            command.tarifaId(),
            command.idempotencyKey()
        );

        // 5. Persistir con SELECT FOR UPDATE (reserva atómica de cupo)
        // NOTA: guardarConReservaDeCupo retorna una NUEVA instancia via toDomain()
        // (constructor de reconstrucción sin eventos). Los eventos de dominio están
        // en el objeto 'inscripcion' original, por eso usamos ese para pullDomainEvents().
        Inscripcion guardada = inscripcionRepository.guardarConReservaDeCupo(inscripcion, evento.cupoDisponible());

        MDC.put(MdcKeys.INSCRIPCION_ID, guardada.getId().toString());
        MDC.put(MdcKeys.EVENTO_ID,      guardada.getEventoId().toString());

        if (esTarifaLibre(tarifa)) {
            eventoService.reservarCupo(guardada.getEventoId());
            guardarEventosCreada(inscripcion, tarifa, null);
            guardada = confirmarEntradaLibre(guardada);

            log.info("Inscripción {} confirmada automáticamente por tarifa libre. Monto: {} {}.",
                guardada.getId(), tarifa.monto(), tarifa.moneda());

            return new Result(guardada, null, 0L);
        }

        // 6. Solicitar preferencia de pago con precio real
        PaymentServicePort.PreferenciaPago preferencia = paymentService.crearPreferencia(
            guardada.getId(), tarifa.monto(), tarifa.moneda(), command.usuarioId());

        eventoService.reservarCupo(guardada.getEventoId());

        // 7. Persistir InscripcionCreadaEvent con contexto completo (monto, checkoutUrl)
        // Usamos 'inscripcion' (original con eventos) no 'guardada' (reconstruida, sin eventos)
        guardarEventosCreada(inscripcion, tarifa, preferencia.checkoutUrl());

        log.info("Inscripción {} creada en PENDIENTE_PAGO. Monto: {} {}. Expira: {}",
            guardada.getId(), tarifa.monto(), tarifa.moneda(), guardada.getFechaExpiracionPago());

        return new Result(guardada, preferencia.checkoutUrl(), 900L);
    }

    private Result continuarPagoExistente(Inscripcion existente,
                                          UUID tarifaSolicitadaId,
                                          UUID idempotencyKey) {
        if (existente.getEstado().estaActiva()) {
            return new Result(existente, null, 0L);
        }

        boolean cupoReservado = false;

        if (existente.getEstado() == EstadoInscripcion.EXPIRADA
            || existente.getEstado() == EstadoInscripcion.CANCELADA) {
            EventoServicePort.EventoInfo evento = eventoService.obtenerEvento(existente.getEventoId());
            if (!evento.aceptaInscripciones()) {
                throw new IllegalStateException(
                    "El evento '" + evento.titulo() + "' no acepta inscripciones. Estado: " + evento.estado());
            }
            inscripcionRepository.reservarCupo(existente.getEventoId(), evento.cupoDisponible());
            cupoReservado = true;
            if (existente.getEstado() == EstadoInscripcion.EXPIRADA) {
                existente.reabrirParaPago(tarifaSolicitadaId);
            } else {
                existente.reabrirDesdeCancelacion(tarifaSolicitadaId, idempotencyKey);
            }
            existente = inscripcionRepository.guardar(existente);
        } else if (existente.haExpirado()) {
            existente.renovarVentanaPago();
            existente = inscripcionRepository.guardar(existente);
        } else if (!existente.getEstado().aceptaPago()) {
            throw new BusinessRuleViolationException(
                "RN-INSCRIPCION-04",
                "Ya existe una inscripción para este evento en estado " + existente.getEstado()
            );
        }

        EventoServicePort.TarifaInfo tarifa = eventoService.obtenerTarifa(existente.getTarifaId());

        if (esTarifaLibre(tarifa)) {
            if (cupoReservado) {
                eventoService.reservarCupo(existente.getEventoId());
            }
            existente = confirmarEntradaLibre(existente);

            log.info("Inscripción {} existente confirmada automáticamente por tarifa libre.",
                existente.getId());

            return new Result(existente, null, 0L);
        }

        PaymentServicePort.PreferenciaPago preferencia = paymentService.crearPreferencia(
            existente.getId(), tarifa.monto(), tarifa.moneda(), existente.getUsuarioId());

        if (cupoReservado) {
            eventoService.reservarCupo(existente.getEventoId());
        }

        log.info("Inscripción {} existente en PENDIENTE_PAGO. Se generó nueva preferencia de pago.",
            existente.getId());

        return new Result(existente, preferencia.checkoutUrl(), calcularSegundosRestantes(existente));
    }

    private long calcularSegundosRestantes(Inscripcion inscripcion) {
        if (inscripcion.getFechaExpiracionPago() == null) return 0;
        long restantes = inscripcion.getFechaExpiracionPago().getEpochSecond()
            - java.time.Instant.now().getEpochSecond();
        return Math.max(restantes, 0);
    }

    private boolean esTarifaLibre(EventoServicePort.TarifaInfo tarifa) {
        return tarifa.monto() == null || tarifa.monto().signum() <= 0;
    }

    private Inscripcion confirmarEntradaLibre(Inscripcion inscripcion) {
        String codigoQr = "QR-" + UUID.randomUUID() + "-" + inscripcion.getId().toString().substring(0, 8);
        inscripcion.confirmar(codigoQr);
        Inscripcion confirmada = inscripcionRepository.guardar(inscripcion);

        for (DomainEvent event : inscripcion.pullDomainEvents()) {
            outboxRepository.guardar(crearOutboxEventConfirmada(event, confirmada));
        }

        return confirmada;
    }

    private void guardarEventosCreada(Inscripcion inscripcion,
                                      EventoServicePort.TarifaInfo tarifa,
                                      String checkoutUrl) {
        for (DomainEvent event : inscripcion.pullDomainEvents()) {
            outboxRepository.guardar(
                crearOutboxEventCreada(event, tarifa, checkoutUrl));
        }
    }

    /**
     * Construye el OutboxEvent para INSCRIPCION_CREADA enriquecido con monto y checkoutUrl,
     * que el agregado no conoce en el momento de emitir el evento.
     */
    private OutboxEvent crearOutboxEventCreada(DomainEvent event,
                                                EventoServicePort.TarifaInfo tarifa,
                                                String checkoutUrl) {
        if (!(event instanceof InscripcionCreadaEvent creada)) {
            throw new IllegalArgumentException(
                "CrearInscripcionService solo procesa InscripcionCreadaEvent, " +
                "recibió: " + event.getClass().getSimpleName());
        }
        PayloadEventoDominio payload = PayloadEventoDominio.deCreada(
            creada, tarifa.monto(), tarifa.moneda(), checkoutUrl);
        String json = serializador.serializar(payload);
        return new OutboxEvent("Inscripcion", event.aggregateId(), event.eventType(), json);
    }

    private OutboxEvent crearOutboxEventConfirmada(DomainEvent event,
                                                   Inscripcion inscripcion) {
        if (!(event instanceof InscripcionConfirmadaEvent confirmada)) {
            throw new IllegalArgumentException(
                "CrearInscripcionService solo procesa InscripcionConfirmadaEvent, " +
                "recibió: " + event.getClass().getSimpleName());
        }
        PayloadEventoDominio payload = PayloadEventoDominio.deConfirmada(
            confirmada, inscripcion.getCodigoQr());
        String json = serializador.serializar(payload);
        return new OutboxEvent("Inscripcion", event.aggregateId(), event.eventType(), json);
    }
}
