package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.port.in.CrearEventoUseCase;
import com.javeriana.eventos.event.domain.port.out.EventoCachePort;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Servicio de aplicación: Crear Evento.
 *
 * Orquesta: crea el agregado → persiste → invalida cache del catálogo.
 * El cache se invalida (no actualiza) porque un evento recién creado está
 * en BORRADOR y no aparece en el catálogo público todavía.
 */
@Service
@Transactional
public class CrearEventoService implements CrearEventoUseCase {

    private final EventoRepository eventoRepository;
    private final EventoCachePort eventoCache;

    public CrearEventoService(EventoRepository eventoRepository, EventoCachePort eventoCache) {
        this.eventoRepository = eventoRepository;
        this.eventoCache = eventoCache;
    }

    @Override
    public Evento crear(Command command) {
        Evento evento = new Evento(
            UUID.randomUUID(),
            command.titulo(),
            command.descripcion(),
            command.tipo(),
            command.modalidad(),
            command.fechaInicio(),
            command.fechaFin(),
            command.fechaLimiteInscripcion(),
            command.cupoMaximo(),
            command.organizadorId()
        );

        Evento guardado = eventoRepository.guardar(evento);

        // El catálogo no necesita invalidarse porque el evento está en BORRADOR
        // y no aparece en las consultas públicas.

        return guardado;
    }
}
