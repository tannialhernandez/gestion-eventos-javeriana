package com.javeriana.eventos.event.infrastructure.outbox;

import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;

public class OutboxEventMapper {

    private OutboxEventMapper() {}

    public static OutboxEvent toDomain(OutboxEventEntity e) {
        return new OutboxEvent(
            e.getId(),
            e.getAggregateType(),
            e.getAggregateId(),
            e.getEventType(),
            e.getPayload(),
            e.getEstado(),
            e.getIntentos(),
            e.getCreadoEn(),
            e.getEnviadoEn()
        );
    }

    public static OutboxEventEntity toEntity(OutboxEvent d) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(d.getId());
        entity.setAggregateType(d.getAggregateType());
        entity.setAggregateId(d.getAggregateId());
        entity.setEventType(d.getEventType());
        entity.setPayload(d.getPayload());
        entity.setEstado(d.getEstado());
        entity.setIntentos(d.getIntentos());
        entity.setCreadoEn(d.getCreadoEn());
        entity.setEnviadoEn(d.getEnviadoEn());
        return entity;
    }
}
