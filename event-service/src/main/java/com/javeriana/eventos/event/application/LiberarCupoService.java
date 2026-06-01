package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.events.CupoLiberadoEvent;
import com.javeriana.eventos.event.domain.events.PayloadEventoDominio;
import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.port.in.LiberarCupoUseCase;
import com.javeriana.eventos.event.domain.port.out.EventoCachePort;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import com.javeriana.eventos.event.domain.port.out.EventoSerializadorPort;
import com.javeriana.eventos.event.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.shared.domain.DomainEvent;
import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class LiberarCupoService implements LiberarCupoUseCase {

    private static final Logger log = LoggerFactory.getLogger(LiberarCupoService.class);

    private final EventoRepository eventoRepository;
    private final EventoCachePort eventoCache;
    private final OutboxEventRepository outboxRepository;
    private final EventoSerializadorPort serializador;

    public LiberarCupoService(EventoRepository eventoRepository,
                               EventoCachePort eventoCache,
                               OutboxEventRepository outboxRepository,
                               EventoSerializadorPort serializador) {
        this.eventoRepository = eventoRepository;
        this.eventoCache      = eventoCache;
        this.outboxRepository = outboxRepository;
        this.serializador     = serializador;
    }

    @Override
    public void liberar(UUID eventoId) {
        Evento evento = eventoRepository.buscarPorId(eventoId)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado: " + eventoId));

        evento.liberarCupo();
        Evento actualizado = eventoRepository.guardar(evento);

        for (DomainEvent ev : evento.pullDomainEvents()) {
            if (ev instanceof CupoLiberadoEvent liberado) {
                String payload = serializador.serializar(
                    PayloadEventoDominio.deCupoLiberado(liberado));
                outboxRepository.guardar(new OutboxEvent(
                    "Evento", eventoId, ev.eventType(), payload));
            }
        }

        eventoCache.guardarEnCache(actualizado);
        eventoCache.invalidarCatalogoCompleto();

        log.info("Cupo liberado: eventoId={}, cupoDisponible={}/{}",
            eventoId, actualizado.getCupoDisponible(), actualizado.getCupoMaximo());
    }
}
