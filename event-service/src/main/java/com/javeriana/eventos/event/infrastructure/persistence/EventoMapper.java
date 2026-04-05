package com.javeriana.eventos.event.infrastructure.persistence;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.infrastructure.persistence.entity.EventoEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper entre el agregado de dominio (Evento) y la entidad JPA (EventoEntity).
 *
 * En Hexagonal Architecture, la entidad JPA y el agregado de dominio son objetos
 * distintos. Este mapper es el único punto de conversión entre ambos mundos.
 */
@Component
public class EventoMapper {

    public Evento toDomain(EventoEntity entity) {
        return new Evento(
            entity.getId(),
            entity.getTitulo(),
            entity.getDescripcion(),
            entity.getTipo(),
            entity.getModalidad(),
            entity.getFechaInicio(),
            entity.getFechaFin(),
            entity.getFechaLimiteInscripcion(),
            entity.getCupoMaximo(),
            entity.getCupoDisponible(),
            entity.getUrlImagen(),
            entity.getUrlEventoVirtual(),
            entity.getEstado(),
            entity.getOrganizadorId(),
            entity.getFechaCreacion(),
            entity.getVersion()
        );
    }

    public EventoEntity toEntity(Evento domain) {
        EventoEntity entity = new EventoEntity();
        entity.setId(domain.getId());
        entity.setTitulo(domain.getTitulo());
        entity.setDescripcion(domain.getDescripcion());
        entity.setTipo(domain.getTipo());
        entity.setModalidad(domain.getModalidad());
        entity.setFechaInicio(domain.getFechaInicio());
        entity.setFechaFin(domain.getFechaFin());
        entity.setFechaLimiteInscripcion(domain.getFechaLimiteInscripcion());
        entity.setCupoMaximo(domain.getCupoMaximo());
        entity.setCupoDisponible(domain.getCupoDisponible());
        entity.setUrlImagen(domain.getUrlImagen());
        entity.setUrlEventoVirtual(domain.getUrlEventoVirtual());
        entity.setEstado(domain.getEstado());
        entity.setOrganizadorId(domain.getOrganizadorId());
        entity.setFechaCreacion(domain.getFechaCreacion());
        entity.setVersion(domain.getVersion());
        return entity;
    }
}
