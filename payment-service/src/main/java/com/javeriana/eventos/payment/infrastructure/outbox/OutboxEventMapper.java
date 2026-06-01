package com.javeriana.eventos.payment.infrastructure.outbox;

import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;

/**
 * Mapper estático entre OutboxEventEntity (JPA) y OutboxEvent (dominio).
 *
 * CRÍTICO: toDomain preserva el id de la entidad. Antes del hallazgo #1,
 * el adaptador usaba new OutboxEvent(aggregateId, ...) que generaba un
 * UUID.randomUUID() nuevo, rompiendo marcarProcesado() que buscaba por ese id.
 */
public class OutboxEventMapper {

    private OutboxEventMapper() {}

    public static OutboxEvent toDomain(OutboxEventEntity e) {
        return new OutboxEvent(
            e.getId(),            // id PRESERVADO — no se regenera
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
