package com.javeriana.eventos.shared.infrastructure.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Representa un evento pendiente de publicar en la cola de mensajes.
 *
 * Outbox Pattern: en vez de publicar directamente a RabbitMQ (lo cual
 * rompería la atomicidad con la transacción de BD), se persiste este
 * registro en la misma transacción. Un proceso relay (OutboxRelayService)
 * lo lee periódicamente y lo publica en RabbitMQ de forma asíncrona.
 *
 * Esto garantiza que NUNCA se pierde un evento aunque el servicio se caiga
 * justo después del COMMIT y antes de publicar en la cola.
 */
public class OutboxEvent {

    private UUID id;
    private UUID aggregateId;
    private String eventType;
    private String payload;        // JSON serializado del evento
    private boolean published;
    private Instant createdAt;
    private Instant publishedAt;

    public OutboxEvent() {}

    public OutboxEvent(UUID aggregateId, String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.published = false;
        this.createdAt = Instant.now();
    }

    public void markAsPublished() {
        this.published = true;
        this.publishedAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public boolean isPublished() { return published; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }

    // Setters para JPA
    public void setId(UUID id) { this.id = id; }
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setPayload(String payload) { this.payload = payload; }
    public void setPublished(boolean published) { this.published = published; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
