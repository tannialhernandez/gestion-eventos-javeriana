package com.javeriana.eventos.payment.infrastructure.outbox;

import com.javeriana.eventos.payment.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.shared.infrastructure.outbox.OutboxEvent;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Adaptador JPA que implementa OutboxEventRepository (puerto de salida del dominio).
 *
 * Espeja la implementación de inscription-service para mantener consistencia
 * del Outbox Pattern entre productores de eventos.
 */
@Repository
public class JpaOutboxEventRepository implements OutboxEventRepository {

    private final SpringDataOutboxRepository springDataRepo;

    public JpaOutboxEventRepository(SpringDataOutboxRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public void guardar(OutboxEvent event) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(event.getId());
        entity.setAggregateId(event.getAggregateId());
        entity.setEventType(event.getEventType());
        entity.setPayload(event.getPayload());
        entity.setPublished(false);
        entity.setCreatedAt(event.getCreatedAt());
        springDataRepo.save(entity);
    }

    @Override
    public List<OutboxEvent> buscarNoPublicados() {
        return springDataRepo.findPendingEvents()
            .stream()
            .map(e -> new OutboxEvent(e.getAggregateId(), e.getEventType(), e.getPayload()))
            .collect(Collectors.toList());
    }

    @Override
    public void marcarComoPublicado(OutboxEvent event) {
        springDataRepo.findById(event.getId()).ifPresent(entity -> {
            entity.setPublished(true);
            entity.setPublishedAt(event.getPublishedAt());
            springDataRepo.save(entity);
        });
    }
}
