package com.javeriana.eventos.event.infrastructure.persistence;

import com.javeriana.eventos.event.domain.model.EstadoEvento;
import com.javeriana.eventos.event.infrastructure.persistence.entity.EventoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA interno.
 * Solo lo usa JpaEventoRepository — no se expone fuera de infrastructure.
 */
interface SpringDataEventoRepository extends JpaRepository<EventoEntity, UUID> {

    List<EventoEntity> findByEstado(EstadoEvento estado);

    List<EventoEntity> findByOrganizadorId(UUID organizadorId);
}
