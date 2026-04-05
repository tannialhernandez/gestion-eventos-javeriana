package com.javeriana.eventos.event.domain.events;

import com.javeriana.eventos.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record EventoPublicadoEvent(
    UUID eventId,
    UUID aggregateId,
    String titulo,
    Instant occurredAt
) implements DomainEvent {

    public EventoPublicadoEvent(UUID eventoId, String titulo) {
        this(UUID.randomUUID(), eventoId, titulo, Instant.now());
    }

    @Override
    public String eventType() {
        return "EVENTO_PUBLICADO";
    }
}
