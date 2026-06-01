package com.javeriana.eventos.payment.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Entidad JPA para la tabla mensaje_procesado (V3 migration).
 *
 * Almacena qué mensajes AMQP ya fueron procesados, por qué consumer group,
 * para garantizar idempotencia entrante ante redeliveries del broker.
 */
@Entity
@Table(name = "mensaje_procesado")
public class MensajeProcesadoEntity {

    @EmbeddedId
    private MensajeProcesadoId id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public MensajeProcesadoEntity() {}

    public MensajeProcesadoEntity(String messageId, String consumerGroup,
                                   String eventType) {
        this.id          = new MensajeProcesadoId(messageId, consumerGroup);
        this.eventType   = eventType;
        this.processedAt = Instant.now();
    }

    public MensajeProcesadoId getId()      { return id; }
    public String getEventType()           { return eventType; }
    public Instant getProcessedAt()        { return processedAt; }
}
