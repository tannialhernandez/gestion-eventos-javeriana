package com.javeriana.eventos.payment.domain.events;

import com.javeriana.eventos.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PagoReembolsadoEvent(
    UUID eventId,
    UUID aggregateId,
    UUID inscripcionId,
    Instant occurredAt
) implements DomainEvent {

    public PagoReembolsadoEvent(UUID pagoId, UUID inscripcionId) {
        this(UUID.randomUUID(), pagoId, inscripcionId, Instant.now());
    }

    @Override
    public String eventType() { return "PAGO_REEMBOLSADO"; }
}
