package com.javeriana.eventos.payment.infrastructure.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA para la tabla outbox_events de payment-service.
 *
 * Outbox Pattern (ADR-11 del SAD): los eventos de dominio (PagoConfirmado,
 * PagoReembolsado) se persisten aquí dentro de la misma transacción de
 * negocio. El OutboxRelayService los publica a RabbitMQ asíncronamente.
 */
@Entity
@Table(name = "outbox_events",
    indexes = @Index(name = "idx_outbox_pending",
        columnList = "published,created_at"))
public class OutboxEventEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "uuid")
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAggregateId() { return aggregateId; }
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
}
