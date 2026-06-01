package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.events.EventoPublicadoEvent;
import com.javeriana.eventos.event.domain.events.PayloadEventoDominio;
import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.port.in.PublicarEventoUseCase;
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
public class PublicarEventoService implements PublicarEventoUseCase {

    private final EventoRepository eventoRepository;
    private final EventoCachePort eventoCache;
    private final OutboxEventRepository outboxRepository;
    private final EventoSerializadorPort serializador;

    public PublicarEventoService(EventoRepository eventoRepository,
                                  EventoCachePort eventoCache,
                                  OutboxEventRepository outboxRepository,
                                  EventoSerializadorPort serializador) {
        this.eventoRepository = eventoRepository;
        this.eventoCache      = eventoCache;
        this.outboxRepository = outboxRepository;
        this.serializador     = serializador;
    }

    @Override
    public void publicar(UUID eventoId, UUID solicitanteId) {
        Evento evento = eventoRepository.buscarPorId(eventoId)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado: " + eventoId));

        evento.publicar();
        Evento actualizado = eventoRepository.guardar(evento);

        // Pull domain events del objeto ORIGINAL (igual que Prompt 15 — guardar devuelve objeto nuevo)
        for (DomainEvent ev : evento.pullDomainEvents()) {
            if (ev instanceof EventoPublicadoEvent publicado) {
                String payload = serializador.serializar(
                    PayloadEventoDominio.deEventoPublicado(publicado));
                outboxRepository.guardar(new OutboxEvent(
                    "Evento", actualizado.getId(), ev.eventType(), payload));
            }
        }

        eventoCache.guardarEnCache(actualizado);
        eventoCache.invalidarCatalogoCompleto();
    }
}
