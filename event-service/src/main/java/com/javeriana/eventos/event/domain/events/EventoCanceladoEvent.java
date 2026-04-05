package com.javeriana.eventos.event.domain.events;

import com.javeriana.eventos.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record EventoCanceladoEvent(
    UUID eventId,
    UUID aggregateId,
    String titulo,
    String motivo,
    Instant occurredAt
) implements DomainEvent {

    public EventoCanceladoEvent(UUID eventoId, String titulo, String motivo) {
        this(UUID.randomUUID(), eventoId, titulo, motivo, Instant.now());
    }

    @Override
    public String eventType() {
        return "EVENTO_CANCELADO";
    }
}
