package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.events.CupoReservadoEvent;
import com.javeriana.eventos.event.domain.events.PayloadEventoDominio;
import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.port.in.ReservarCupoUseCase;
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
public class ReservarCupoService implements ReservarCupoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReservarCupoService.class);

    private final EventoRepository eventoRepository;
    private final EventoCachePort eventoCache;
    private final OutboxEventRepository outboxRepository;
    private final EventoSerializadorPort serializador;

    public ReservarCupoService(EventoRepository eventoRepository,
                                EventoCachePort eventoCache,
                                OutboxEventRepository outboxRepository,
                                EventoSerializadorPort serializador) {
        this.eventoRepository = eventoRepository;
        this.eventoCache      = eventoCache;
        this.outboxRepository = outboxRepository;
        this.serializador     = serializador;
    }

    @Override
    public void reservar(UUID eventoId) {
        Evento evento = eventoRepository.buscarPorId(eventoId)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado: " + eventoId));

        evento.reservarCupo();
        Evento actualizado = eventoRepository.guardar(evento);

        for (DomainEvent ev : evento.pullDomainEvents()) {
            if (ev instanceof CupoReservadoEvent reservado) {
                String payload = serializador.serializar(
                    PayloadEventoDominio.deCupoReservado(reservado));
                outboxRepository.guardar(new OutboxEvent(
                    "Evento", eventoId, ev.eventType(), payload));
            }
        }

        eventoCache.guardarEnCache(actualizado);
        eventoCache.invalidarCatalogoCompleto();

        log.info("Cupo reservado: eventoId={}, cupoDisponible={}/{}",
            eventoId, actualizado.getCupoDisponible(), actualizado.getCupoMaximo());
    }
}
