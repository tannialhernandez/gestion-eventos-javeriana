package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.events.EventoPublicadoEvent;
import com.javeriana.eventos.event.domain.events.PayloadEventoDominio;
import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.port.in.GestionarWorkflowEventoUseCase;
import com.javeriana.eventos.event.domain.port.out.EventoCachePort;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import com.javeriana.eventos.event.domain.port.out.EventoSerializadorPort;
import com.javeriana.eventos.event.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.shared.domain.DomainEvent;
import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class GestionarWorkflowEventoService implements GestionarWorkflowEventoUseCase {

    private final EventoRepository eventoRepository;
    private final EventoCachePort eventoCache;
    private final OutboxEventRepository outboxRepository;
    private final EventoSerializadorPort serializador;

    public GestionarWorkflowEventoService(EventoRepository eventoRepository,
                                          EventoCachePort eventoCache,
                                          OutboxEventRepository outboxRepository,
                                          EventoSerializadorPort serializador) {
        this.eventoRepository = eventoRepository;
        this.eventoCache = eventoCache;
        this.outboxRepository = outboxRepository;
        this.serializador = serializador;
    }

    @Override
    public Evento enviarARevision(UUID eventoId, UUID solicitanteId) {
        Evento evento = buscar(eventoId);
        if (!evento.getOrganizadorId().equals(solicitanteId)) {
            throw new SecurityException("Solo el organizador propietario puede enviar este evento a revision");
        }

        evento.enviarARevision();
        Evento actualizado = eventoRepository.guardar(evento);
        invalidarCache(actualizado);
        return actualizado;
    }

    @Override
    public Evento aprobar(UUID eventoId, UUID adminId) {
        Evento evento = buscar(eventoId);
        evento.aprobar();
        Evento actualizado = eventoRepository.guardar(evento);

        for (DomainEvent ev : evento.pullDomainEvents()) {
            if (ev instanceof EventoPublicadoEvent publicado) {
                String payload = serializador.serializar(PayloadEventoDominio.deEventoPublicado(publicado));
                outboxRepository.guardar(new OutboxEvent("Evento", actualizado.getId(), ev.eventType(), payload));
            }
        }

        eventoCache.guardarEnCache(actualizado);
        eventoCache.invalidarCatalogoCompleto();
        return actualizado;
    }

    @Override
    public Evento rechazar(UUID eventoId, UUID adminId, String motivo) {
        Evento evento = buscar(eventoId);
        evento.rechazar(motivo);
        Evento actualizado = eventoRepository.guardar(evento);
        invalidarCache(actualizado);
        return actualizado;
    }

    private Evento buscar(UUID eventoId) {
        return eventoRepository.buscarPorId(eventoId)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado: " + eventoId));
    }

    private void invalidarCache(Evento evento) {
        eventoCache.invalidarEvento(evento.getId());
        eventoCache.invalidarCatalogoCompleto();
    }
}
