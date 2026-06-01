package com.javeriana.eventos.event.domain.events;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO canónico del payload publicado al exchange AMQP para eventos de event-service.
 *
 * Contrato documentado en ADR-020 y docs/asyncapi/event-service.yml.
 * Routing keys: evento.publicado | evento.cancelado | evento.cupo.liberado | evento.cupo.reservado
 *
 * @JsonInclude(NON_NULL): excluye campos opcionales nulos para compactar el payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PayloadEventoDominio(
    String eventId,
    String aggregateId,
    String aggregateType,
    String eventType,
    String eventoId,
    String titulo,
    String organizadorId,
    String modalidad,
    String fechaInicio,
    Integer cupoMaximo,
    Integer cupoDisponible,
    String motivo,
    String occurredAt,
    String schemaVersion
) {
    private static final String SCHEMA_V1      = "v1";
    private static final String AGGREGATE_TYPE = "Evento";

    public static PayloadEventoDominio deEventoPublicado(EventoPublicadoEvent ev) {
        return new PayloadEventoDominio(
            ev.eventId().toString(),
            ev.aggregateId().toString(),
            AGGREGATE_TYPE,
            ev.eventType(),
            ev.aggregateId().toString(),
            ev.titulo(),
            ev.organizadorId().toString(),
            ev.modalidad(),
            ev.fechaInicio().toString(),
            ev.cupoMaximo(),
            null, null,
            ev.occurredAt().toString(),
            SCHEMA_V1
        );
    }

    public static PayloadEventoDominio deEventoCancelado(EventoCanceladoEvent ev) {
        return new PayloadEventoDominio(
            ev.eventId().toString(),
            ev.aggregateId().toString(),
            AGGREGATE_TYPE,
            ev.eventType(),
            ev.aggregateId().toString(),
            ev.titulo(),
            null, null, null, null, null,
            ev.motivo(),
            ev.occurredAt().toString(),
            SCHEMA_V1
        );
    }

    public static PayloadEventoDominio deCupoLiberado(CupoLiberadoEvent ev) {
        return new PayloadEventoDominio(
            ev.eventId().toString(),
            ev.aggregateId().toString(),
            AGGREGATE_TYPE,
            ev.eventType(),
            ev.aggregateId().toString(),
            null, null, null, null, null,
            ev.cupoDisponible(),
            null,
            ev.occurredAt().toString(),
            SCHEMA_V1
        );
    }

    public static PayloadEventoDominio deCupoReservado(CupoReservadoEvent ev) {
        return new PayloadEventoDominio(
            ev.eventId().toString(),
            ev.aggregateId().toString(),
            AGGREGATE_TYPE,
            ev.eventType(),
            ev.aggregateId().toString(),
            null, null, null, null, null,
            ev.cupoDisponible(),
            null,
            ev.occurredAt().toString(),
            SCHEMA_V1
        );
    }
}
