package com.javeriana.eventos.inscription.domain.events;

import com.javeriana.eventos.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record InscripcionConfirmadaEvent(
    UUID eventId,
    UUID aggregateId,
    UUID usuarioId,
    UUID eventoId,
    Instant occurredAt
) implements DomainEvent {

    public InscripcionConfirmadaEvent(UUID inscripcionId, UUID usuarioId, UUID eventoId) {
        this(UUID.randomUUID(), inscripcionId, usuarioId, eventoId, Instant.now());
    }

    @Override
    public String eventType() {
        return "INSCRIPCION_CONFIRMADA";
    }
}
