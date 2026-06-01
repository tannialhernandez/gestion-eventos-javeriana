package com.javeriana.eventos.inscription.infrastructure.idempotencia;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Clave compuesta de mensaje_procesado: messageId + consumerGrupo.
 *
 * La PK compuesta permite que el mismo messageId sea procesado por
 * diferentes consumers (ej: confirmar-inscripcion, liberar-cupo)
 * sin colisión de idempotencia entre ellos.
 */
@Embeddable
public class MensajeProcesadoId implements Serializable {

    @Column(name = "message_id", nullable = false, length = 255)
    private String messageId;

    @Column(name = "consumer_grupo", nullable = false, length = 100)
    private String consumerGrupo;

    public MensajeProcesadoId() {}

    public MensajeProcesadoId(String messageId, String consumerGrupo) {
        this.messageId     = messageId;
        this.consumerGrupo = consumerGrupo;
    }

    public String getMessageId()     { return messageId; }
    public String getConsumerGrupo() { return consumerGrupo; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MensajeProcesadoId that)) return false;
        return Objects.equals(messageId, that.messageId)
            && Objects.equals(consumerGrupo, that.consumerGrupo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, consumerGrupo);
    }
}
