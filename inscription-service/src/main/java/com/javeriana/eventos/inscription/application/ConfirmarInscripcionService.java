package com.javeriana.eventos.inscription.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.inscription.domain.model.EstadoInscripcion;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.in.ConfirmarInscripcionUseCase;
import com.javeriana.eventos.inscription.domain.port.out.InscripcionRepository;
import com.javeriana.eventos.inscription.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.shared.domain.DomainEvent;
import com.javeriana.eventos.shared.infrastructure.outbox.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Confirma una inscripción cuando payment-service notifica el pago exitoso.
 *
 * Este servicio es invocado por el InscripcionEventConsumer que escucha
 * la cola RabbitMQ "pago.confirmado".
 *
 * Garantías:
 * - Si la inscripción ya está CONFIRMADA → idempotente, no hace nada
 * - Si la inscripción está EXPIRADA → pago tardío, no confirma
 *   (payment-service ya emitió el reembolso)
 */
@Service
@Transactional
public class ConfirmarInscripcionService implements ConfirmarInscripcionUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmarInscripcionService.class);

    private final InscripcionRepository inscripcionRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ConfirmarInscripcionService(InscripcionRepository inscripcionRepository,
                                       OutboxEventRepository outboxRepository,
                                       ObjectMapper objectMapper) {
        this.inscripcionRepository = inscripcionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void confirmar(UUID inscripcionId, String referenciaExterna) {
        Inscripcion inscripcion = inscripcionRepository.buscarPorId(inscripcionId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Inscripción no encontrada: " + inscripcionId));

        // Idempotencia: si ya está confirmada, no hacer nada
        if (inscripcion.getEstado() == EstadoInscripcion.CONFIRMADA) {
            log.info("Inscripción {} ya está confirmada. Ignorando mensaje duplicado.", inscripcionId);
            return;
        }

        if (inscripcion.getEstado() == EstadoInscripcion.EXPIRADA) {
            log.warn("Pago recibido para inscripción EXPIRADA {}. " +
                "Payment-service debería emitir reembolso.", inscripcionId);
            return;
        }

        // Generar código QR para acceso físico
        String codigoQr = generarCodigoQr(inscripcionId);

        // Transición de estado en el agregado
        inscripcion.confirmar(codigoQr);
        inscripcionRepository.guardar(inscripcion);

        // Publicar eventos de dominio vía Outbox Pattern
        for (DomainEvent event : inscripcion.pullDomainEvents()) {
            outboxRepository.guardar(serializarEvento(event));
        }

        log.info("Inscripción {} confirmada. QR generado.", inscripcionId);
    }

    @Override
    public void manejarPagoTardio(UUID inscripcionId, String referenciaExterna) {
        // La inscripción ya expiró pero el pago llegó tarde
        // payment-service ya emitió el reembolso; solo logueamos
        log.warn("Pago tardío para inscripción {} (ref: {}). Reembolso manejado por payment-service.",
            inscripcionId, referenciaExterna);
    }

    private String generarCodigoQr(UUID inscripcionId) {
        // El token QR es un UUID firmado — en prod sería un JWT de corta duración
        return "QR-" + UUID.randomUUID() + "-" + inscripcionId.toString().substring(0, 8);
    }

    private OutboxEvent serializarEvento(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", event.eventId().toString(),
                "aggregateId", event.aggregateId().toString(),
                "eventType", event.eventType(),
                "occurredAt", event.occurredAt().toString()
            ));
            return new OutboxEvent(event.aggregateId(), event.eventType(), payload);
        } catch (Exception e) {
            throw new RuntimeException("Error serializando evento de dominio", e);
        }
    }
}
