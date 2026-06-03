package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.model.EstadoEvento;
import com.javeriana.eventos.event.domain.port.in.ActualizarEventoUseCase;
import com.javeriana.eventos.event.domain.port.out.EventoCachePort;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ActualizarEventoService implements ActualizarEventoUseCase {

    private final EventoRepository eventoRepository;
    private final EventoCachePort eventoCache;

    public ActualizarEventoService(EventoRepository eventoRepository, EventoCachePort eventoCache) {
        this.eventoRepository = eventoRepository;
        this.eventoCache = eventoCache;
    }

    @Override
    public Evento actualizar(Command command) {
        Evento evento = eventoRepository.buscarPorId(command.eventoId())
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado: " + command.eventoId()));

        if (!command.solicitanteAdmin() && !evento.getOrganizadorId().equals(command.solicitanteId())) {
            throw new SecurityException("Solo el organizador propietario o ADMIN puede editar este evento");
        }

        evento.actualizarDatos(
            command.titulo(),
            command.descripcion(),
            command.tipo(),
            command.modalidad(),
            command.fechaInicio(),
            command.fechaFin(),
            command.fechaLimiteInscripcion(),
            command.cupoMaximo()
        );
        if (command.estadoDeseado() == EstadoEvento.BORRADOR) {
            evento.volverABorrador();
        }

        Evento actualizado = eventoRepository.guardar(evento);
        eventoCache.invalidarEvento(command.eventoId());
        eventoCache.invalidarCatalogoCompleto();
        if (actualizado.getEstado().aceptaInscripciones()) {
            eventoCache.guardarEnCache(actualizado);
        }
        return actualizado;
    }
}
