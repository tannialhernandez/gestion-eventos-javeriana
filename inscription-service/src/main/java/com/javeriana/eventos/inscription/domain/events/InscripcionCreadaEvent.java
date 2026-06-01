package com.javeriana.eventos.inscription.domain.events;

import com.javeriana.eventos.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: una inscripción fue creada en estado PENDIENTE_PAGO.
 *
 * Permite a notification-service enviar un recordatorio de pago con el
 * checkout_url y el plazo de 15 minutos antes de que expire la reserva.
 *
 * Decisión arquitectónica (ADR-020, Prompt 15):
 *   Este evento se publicó intencionalmente en Entrega 3 para que
 *   notification-service pueda enviar recordatorios de pago. La alternativa
 *   de no publicarlo fue descartada (ver m-03 de la auditoría Prompt 8).
 */
public record InscripcionCreadaEvent(
    UUID eventId,
    UUID aggregateId,     // inscripcionId
    UUID usuarioId,
    UUID eventoId,
    UUID tarifaId,
    Instant expiraEn,     // fechaExpiracionPago = now() + 15min
    Instant occurredAt
) implements DomainEvent {

    public InscripcionCreadaEvent(UUID inscripcionId, UUID usuarioId,
                                   UUID eventoId, UUID tarifaId,
                                   Instant expiraEn) {
        this(UUID.randomUUID(), inscripcionId, usuarioId, eventoId,
             tarifaId, expiraEn, Instant.now());
    }

    @Override
    public String eventType() { return "INSCRIPCION_CREADA"; }
}
