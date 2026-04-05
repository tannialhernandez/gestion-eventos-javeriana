package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.port.in.ConsultarCatalogoUseCase;
import com.javeriana.eventos.event.domain.port.out.EventoCachePort;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de aplicación: Consultar Catálogo — CQRS Query Side.
 *
 * Estrategia de lectura (Cache-Aside / Read-Through):
 *   1. Buscar en Redis (EventoCachePort)
 *   2. Si miss → leer de PostgreSQL (EventoRepository)
 *   3. Repoblar cache con el resultado
 *
 * Esta separación de comando/consulta (CQRS) garantiza que las lecturas
 * del catálogo no compiten con las escrituras de inscripciones.
 * El objetivo de latencia es < 300ms p95 (RNF-01).
 */
@Service
@Transactional(readOnly = true)
public class ConsultarCatalogoService implements ConsultarCatalogoUseCase {

    private final EventoRepository eventoRepository;
    private final EventoCachePort eventoCache;

    public ConsultarCatalogoService(EventoRepository eventoRepository, EventoCachePort eventoCache) {
        this.eventoRepository = eventoRepository;
        this.eventoCache = eventoCache;
    }

    @Override
    public List<Evento> listarPublicados(Filtros filtros) {
        // 1. Intentar desde cache
        List<Evento> cached = eventoCache.listarPublicados();

        if (!cached.isEmpty()) {
            return aplicarFiltros(cached, filtros);
        }

        // 2. Cache miss: leer de BD
        List<Evento> eventos = eventoRepository.buscarPublicados();

        // 3. Repoblar cache
        if (!eventos.isEmpty()) {
            eventoCache.guardarListaEnCache(eventos);
        }

        return aplicarFiltros(eventos, filtros);
    }

    @Override
    public Optional<Evento> buscarPorId(UUID eventoId) {
        // 1. Intentar desde cache
        Optional<Evento> cached = eventoCache.buscarPorId(eventoId);
        if (cached.isPresent()) {
            return cached;
        }

        // 2. Cache miss: leer de BD y repoblar
        Optional<Evento> evento = eventoRepository.buscarPorId(eventoId);
        evento.ifPresent(eventoCache::guardarEnCache);

        return evento;
    }

    private List<Evento> aplicarFiltros(List<Evento> eventos, Filtros filtros) {
        return eventos.stream()
            .filter(e -> filtros.tipo() == null || e.getTipo() == filtros.tipo())
            .filter(e -> filtros.modalidad() == null || e.getModalidad() == filtros.modalidad())
            .filter(e -> filtros.conCuposDisponibles() == null
                || !filtros.conCuposDisponibles()
                || e.getCupoDisponible() > 0)
            .filter(e -> filtros.textoBusqueda() == null
                || e.getTitulo().toLowerCase().contains(filtros.textoBusqueda().toLowerCase()))
            .skip((long) filtros.pagina() * filtros.tamano())
            .limit(filtros.tamano())
            .collect(Collectors.toList());
    }
}
