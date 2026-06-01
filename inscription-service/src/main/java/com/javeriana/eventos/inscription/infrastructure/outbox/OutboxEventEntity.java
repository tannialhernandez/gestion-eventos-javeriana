package com.javeriana.eventos.inscription.infrastructure.outbox;

import com.javeriana.eventos.shared.domain.outbox.EstadoOutbox;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA para la tabla outbox_events de inscription-service.
 *
 * Outbox Pattern (ADR-011): los eventos de dominio (InscripcionConfirmada,
 * InscripcionExpirada) se persisten aquí dentro de la misma transacción
 * de negocio. El OutboxRelayService los publica a RabbitMQ cada 5s.
 */
@Entity
@Table(name = "outbox_events",
    indexes = @Index(name = "idx_outbox_estado_creado",
        columnList = "estado,creado_en"))
public class OutboxEventEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "uuid")
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOutbox estado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "enviado_en")
    private Instant enviadoEn;

    @Column(name = "intentos", nullable = false)
    private int intentos = 0;

    public UUID getId()                   { return id; }
    public void setId(UUID id)            { this.id = id; }
    public String getAggregateType()      { return aggregateType; }
    public void setAggregateType(String t){ this.aggregateType = t; }
    public UUID getAggregateId()          { return aggregateId; }
    public void setAggregateId(UUID a)    { this.aggregateId = a; }
    public String getEventType()          { return eventType; }
    public void setEventType(String et)   { this.eventType = et; }
    public String getPayload()            { return payload; }
    public void setPayload(String p)      { this.payload = p; }
    public EstadoOutbox getEstado()       { return estado; }
    public void setEstado(EstadoOutbox e) { this.estado = e; }
    public Instant getCreadoEn()          { return creadoEn; }
    public void setCreadoEn(Instant c)    { this.creadoEn = c; }
    public Instant getEnviadoEn()         { return enviadoEn; }
    public void setEnviadoEn(Instant e)   { this.enviadoEn = e; }
    public int getIntentos()              { return intentos; }
    public void setIntentos(int i)        { this.intentos = i; }
}
