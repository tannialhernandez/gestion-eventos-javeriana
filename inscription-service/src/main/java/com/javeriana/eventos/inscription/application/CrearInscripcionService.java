package com.javeriana.eventos.inscription.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.in.CrearInscripcionUseCase;
import com.javeriana.eventos.inscription.domain.port.out.*;
import com.javeriana.eventos.shared.infrastructure.outbox.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta la creación de una inscripción.
 *
 * Flujo (T=0):
 *  1. Verificar idempotencia: si ya existe una inscripción con ese idempotencyKey → retornar
 *  2. Verificar que el evento acepta inscripciones (Feign → event-service)
 *  3. Crear el agregado Inscripcion en PENDIENTE_PAGO
 *  4. Persistir con SELECT FOR UPDATE (reserva atómica de cupo)
 *  5. Solicitar preferencia de pago a payment-service
 *  6. Retornar checkout_url al cliente
 *
 * Nota: los pasos 4 y 5 están en la misma transacción. Si la llamada a payment-service
 * falla, la transacción hace rollback y el cupo no se descuenta.
 */
@Service
@Transactional
public class CrearInscripcionService implements CrearInscripcionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CrearInscripcionService.class);

    private final InscripcionRepository inscripcionRepository;
    private final EventoServicePort eventoService;
    private final PaymentServicePort paymentService;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public CrearInscripcionService(InscripcionRepository inscripcionRepository,
                                   EventoServicePort eventoService,
                                   PaymentServicePort paymentService,
                                   OutboxEventRepository outboxRepository,
                                   ObjectMapper objectMapper) {
        this.inscripcionRepository = inscripcionRepository;
        this.eventoService = eventoService;
        this.paymentService = paymentService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Result crear(Command command) {
        // 1. Idempotencia: misma clave → retornar inscripción existente
        Optional<Inscripcion> existente =
            inscripcionRepository.buscarPorIdempotencyKey(command.idempotencyKey());
        if (existente.isPresent()) {
            log.info("Inscripción duplicada detectada para idempotencyKey={}",
                command.idempotencyKey());
            // No podemos retornar checkout_url aquí ya que no lo persistimos
            // El cliente debe reintentar si necesita el URL
            return new Result(existente.get(), null, calcularSegundosRestantes(existente.get()));
        }

        // 2. Verificar que el evento acepta inscripciones
        EventoServicePort.EventoInfo evento = eventoService.obtenerEvento(command.eventoId());
        if (!evento.aceptaInscripciones()) {
            throw new IllegalStateException(
                "El evento '" + evento.titulo() + "' no acepta inscripciones. Estado: " + evento.estado()
            );
        }

        // 3. Crear el agregado
        Inscripcion inscripcion = new Inscripcion(
            UUID.randomUUID(),
            command.usuarioId(),
            command.eventoId(),
            command.tarifaId(),
            command.idempotencyKey()
        );

        // 4. Persistir con SELECT FOR UPDATE (reserva atómica de cupo en la misma transacción)
        // Si no hay cupos → lanza SinCuposDisponiblesException → rollback automático
        Inscripcion guardada = inscripcionRepository.guardarConReservaDeCupo(inscripcion);

        // 5. Solicitar preferencia de pago a payment-service
        // TODO: obtener monto real desde tarifa (simplificado aquí)
        PaymentServicePort.PreferenciaPago preferencia = paymentService.crearPreferencia(
            guardada.getId(),
            BigDecimal.valueOf(100_000),  // Monto placeholder — en prod viene de Tarifa
            "COP",
            command.usuarioId()
        );

        // 6. Guardar evento de dominio en outbox (dentro de la misma transacción)
        // No hay evento de dominio aquí — InscripcionCreada no se publica externamente
        // (solo InscripcionConfirmada, cuando el pago sea exitoso)

        log.info("Inscripción {} creada en PENDIENTE_PAGO. Expira: {}",
            guardada.getId(), guardada.getFechaExpiracionPago());

        return new Result(guardada, preferencia.checkoutUrl(), 900L);
    }

    private long calcularSegundosRestantes(Inscripcion inscripcion) {
        if (inscripcion.getFechaExpiracionPago() == null) return 0;
        long restantes = inscripcion.getFechaExpiracionPago().getEpochSecond()
            - java.time.Instant.now().getEpochSecond();
        return Math.max(restantes, 0);
    }
}
