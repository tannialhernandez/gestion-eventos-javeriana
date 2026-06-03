package com.javeriana.eventos.event.domain.port.out;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.model.EstadoEvento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de salida — Repository pattern para EVENTO.
 *
 * Interface definida en dominio, implementada en infrastructure/persistence.
 * El dominio nunca importa JPA ni Spring Data directamente.
 */
public interface EventoRepository {

    Evento guardar(Evento evento);

    Optional<Evento> buscarPorId(UUID id);

    List<Evento> buscarTodos();

    List<Evento> buscarPorOrganizadorId(UUID organizadorId);

    List<Evento> buscarPorEstado(EstadoEvento estado);

    List<Evento> buscarPublicados();

    void eliminar(UUID id);
}
