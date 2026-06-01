package com.javeriana.eventos.event.domain.events;

import com.javeriana.eventos.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record CupoLiberadoEvent(
    UUID eventId,
    UUID aggregateId,
    int cupoDisponible,
    Instant occurredAt
) implements DomainEvent {

    public CupoLiberadoEvent(UUID eventoId, int cupoDisponible) {
        this(UUID.randomUUID(), eventoId, cupoDisponible, Instant.now());
    }

    @Override
    public String eventType() {
        return "CUPO_LIBERADO";
    }
}
