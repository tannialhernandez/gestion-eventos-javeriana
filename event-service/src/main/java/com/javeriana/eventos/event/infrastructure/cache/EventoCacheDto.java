package com.javeriana.eventos.event.infrastructure.cache;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.javeriana.eventos.event.domain.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de serialización para Redis. Separa el modelo de cache del dominio,
 * permitiendo evolucionar ambos de forma independiente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventoCacheDto {

    public UUID id;
    public String titulo;
    public String descripcion;
    public String tipo;
    public String modalidad;
    public LocalDate fechaInicio;
    public LocalDate fechaFin;
    public LocalDateTime fechaLimiteInscripcion;
    public int cupoMaximo;
    public int cupoDisponible;
    public String urlImagen;
    public String urlEventoVirtual;
    public String estado;
    public UUID organizadorId;
    public LocalDateTime fechaCreacion;
    public int version;

    public static EventoCacheDto from(Evento evento) {
        EventoCacheDto dto = new EventoCacheDto();
        dto.id = evento.getId();
        dto.titulo = evento.getTitulo();
        dto.descripcion = evento.getDescripcion();
        dto.tipo = evento.getTipo().name();
        dto.modalidad = evento.getModalidad().name();
        dto.fechaInicio = evento.getFechaInicio();
        dto.fechaFin = evento.getFechaFin();
        dto.fechaLimiteInscripcion = evento.getFechaLimiteInscripcion();
        dto.cupoMaximo = evento.getCupoMaximo();
        dto.cupoDisponible = evento.getCupoDisponible();
        dto.urlImagen = evento.getUrlImagen();
        dto.urlEventoVirtual = evento.getUrlEventoVirtual();
        dto.estado = evento.getEstado().name();
        dto.organizadorId = evento.getOrganizadorId();
        dto.fechaCreacion = evento.getFechaCreacion();
        dto.version = evento.getVersion();
        return dto;
    }

    public Evento toDomain() {
        return new Evento(
            id, titulo, descripcion,
            TipoEvento.valueOf(tipo),
            ModalidadEvento.valueOf(modalidad),
            fechaInicio, fechaFin, fechaLimiteInscripcion,
            cupoMaximo, cupoDisponible,
            urlImagen, urlEventoVirtual,
            EstadoEvento.valueOf(estado),
            organizadorId, fechaCreacion, version
        );
    }
}
