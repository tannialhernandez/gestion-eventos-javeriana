package com.javeriana.eventos.payment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Clave compuesta de mensaje_procesado: messageId + consumerGroup.
 * Permite que el mismo messageId sea procesado por diferentes consumers
 * sin colisión de idempotencia.
 */
@Embeddable
public class MensajeProcesadoId implements Serializable {

    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    @Column(name = "consumer_group", nullable = false, length = 100)
    private String consumerGroup;

    public MensajeProcesadoId() {}

    public MensajeProcesadoId(String messageId, String consumerGroup) {
        this.messageId     = messageId;
        this.consumerGroup = consumerGroup;
    }

    public String getMessageId()     { return messageId; }
    public String getConsumerGroup() { return consumerGroup; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MensajeProcesadoId that)) return false;
        return Objects.equals(messageId, that.messageId)
            && Objects.equals(consumerGroup, that.consumerGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, consumerGroup);
    }
}
