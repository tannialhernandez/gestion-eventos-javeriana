package com.javeriana.eventos.event.domain.port.out;

import com.javeriana.eventos.event.domain.model.Evento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de salida — Cache de catálogo de eventos (Redis).
 *
 * CQRS: El lado de lectura usa este port. Si hay miss, ConsultarCatalogoService
 * lee de EventoRepository y llama a guardarEnCache para repopular.
 * Cuando se modifica un evento, CrearEventoService llama a invalidarCache.
 */
public interface EventoCachePort {

    Optional<Evento> buscarPorId(UUID eventoId);

    List<Evento> listarPublicados();

    void guardarEnCache(Evento evento);

    void guardarListaEnCache(List<Evento> eventos);

    void invalidarEvento(UUID eventoId);

    void invalidarCatalogoCompleto();
}
