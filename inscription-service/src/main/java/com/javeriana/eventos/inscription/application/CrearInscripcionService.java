package com.javeriana.eventos.inscription.application;

import com.javeriana.eventos.inscription.domain.events.InscripcionCreadaEvent;
import com.javeriana.eventos.inscription.domain.events.PayloadEventoDominio;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.in.CrearInscripcionUseCase;
import com.javeriana.eventos.inscription.domain.port.out.*;
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
        Inscripcion guardada = inscripcionRepository.guardarConReservaDeCupo(inscripcion);

        MDC.put(MdcKeys.INSCRIPCION_ID, guardada.getId().toString());
        MDC.put(MdcKeys.EVENTO_ID,      guardada.getEventoId().toString());

        // 6. Solicitar preferencia de pago con precio real
        PaymentServicePort.PreferenciaPago preferencia = paymentService.crearPreferencia(
            guardada.getId(), tarifa.monto(), tarifa.moneda(), command.usuarioId());

        // 7. Persistir InscripcionCreadaEvent con contexto completo (monto, checkoutUrl)
        // Usamos 'inscripcion' (original con eventos) no 'guardada' (reconstruida, sin eventos)
        for (DomainEvent event : inscripcion.pullDomainEvents()) {
            outboxRepository.guardar(
                crearOutboxEventCreada(event, tarifa, preferencia.checkoutUrl()));
        }

        log.info("Inscripción {} creada en PENDIENTE_PAGO. Monto: {} {}. Expira: {}",
            guardada.getId(), tarifa.monto(), tarifa.moneda(), guardada.getFechaExpiracionPago());

        return new Result(guardada, preferencia.checkoutUrl(), 900L);
    }

    private long calcularSegundosRestantes(Inscripcion inscripcion) {
        if (inscripcion.getFechaExpiracionPago() == null) return 0;
        long restantes = inscripcion.getFechaExpiracionPago().getEpochSecond()
            - java.time.Instant.now().getEpochSecond();
        return Math.max(restantes, 0);
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
}
