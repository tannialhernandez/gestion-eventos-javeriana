package com.javeriana.eventos.event.domain.events;

import com.javeriana.eventos.shared.domain.DomainEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EventoPublicadoEvent(
    UUID eventId,
    UUID aggregateId,
    String titulo,
    UUID organizadorId,
    String modalidad,
    LocalDate fechaInicio,
    int cupoMaximo,
    Instant occurredAt
) implements DomainEvent {

    public EventoPublicadoEvent(UUID eventoId, String titulo, UUID organizadorId,
                                 String modalidad, LocalDate fechaInicio, int cupoMaximo) {
        this(UUID.randomUUID(), eventoId, titulo, organizadorId,
             modalidad, fechaInicio, cupoMaximo, Instant.now());
    }

    @Override
    public String eventType() {
        return "EVENTO_PUBLICADO";
    }
}
