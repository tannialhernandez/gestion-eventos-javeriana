package com.javeriana.eventos.event.infrastructure.web.dto;

import com.javeriana.eventos.event.domain.model.Evento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventoResponse(
    UUID id,
    String titulo,
    String descripcion,
    String tipo,
    String modalidad,
    LocalDate fechaInicio,
    LocalDate fechaFin,
    LocalDateTime fechaLimiteInscripcion,
    int cupoMaximo,
    int cupoDisponible,
    String urlImagen,
    String estado,
    UUID organizadorId
) {
    public static EventoResponse from(Evento evento) {
        return new EventoResponse(
            evento.getId(),
            evento.getTitulo(),
            evento.getDescripcion(),
            evento.getTipo().name(),
            evento.getModalidad().name(),
            evento.getFechaInicio(),
            evento.getFechaFin(),
            evento.getFechaLimiteInscripcion(),
            evento.getCupoMaximo(),
            evento.getCupoDisponible(),
            evento.getUrlImagen(),
            evento.getEstado().name(),
            evento.getOrganizadorId()
        );
    }
}
