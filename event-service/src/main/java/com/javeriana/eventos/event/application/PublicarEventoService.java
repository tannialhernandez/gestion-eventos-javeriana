package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.port.in.PublicarEventoUseCase;
import com.javeriana.eventos.event.domain.port.out.EventoCachePort;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Servicio de aplicación: Publicar Evento.
 *
 * Al publicar: persiste el nuevo estado → actualiza cache (ahora sí es visible
 * en el catálogo público).
 */
@Service
@Transactional
public class PublicarEventoService implements PublicarEventoUseCase {

    private final EventoRepository eventoRepository;
    private final EventoCachePort eventoCache;

    public PublicarEventoService(EventoRepository eventoRepository, EventoCachePort eventoCache) {
        this.eventoRepository = eventoRepository;
        this.eventoCache = eventoCache;
    }

    @Override
    public void publicar(UUID eventoId, UUID solicitanteId) {
        Evento evento = eventoRepository.buscarPorId(eventoId)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado: " + eventoId));

        // La validación de que el solicitante puede publicar ocurre en el security layer
        evento.publicar();

        Evento actualizado = eventoRepository.guardar(evento);

        // Ahora el evento es PUBLICADO: actualizar cache del catálogo
        eventoCache.guardarEnCache(actualizado);
        eventoCache.invalidarCatalogoCompleto(); // Invalida la lista para forzar repoblación
    }
}
