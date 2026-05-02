package com.javeriana.eventos.payment.domain.events;

import com.javeriana.eventos.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PagoConfirmadoEvent(
    UUID eventId,
    UUID aggregateId,     // pagoId
    UUID inscripcionId,
    String referenciaExterna,
    Instant occurredAt
) implements DomainEvent {

    public PagoConfirmadoEvent(UUID pagoId, UUID inscripcionId, String referenciaExterna) {
        this(UUID.randomUUID(), pagoId, inscripcionId, referenciaExterna, Instant.now());
    }

    @Override
    public String eventType() { return "PAGO_CONFIRMADO"; }
}
