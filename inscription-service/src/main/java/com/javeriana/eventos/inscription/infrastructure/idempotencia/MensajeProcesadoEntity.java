package com.javeriana.eventos.inscription.infrastructure.idempotencia;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Entidad JPA para la tabla mensaje_procesado (V3 migration).
 *
 * Registra qué mensajes AMQP ya procesó cada consumer, previniendo
 * doble-procesamiento ante reentregas del broker (at-least-once delivery).
 */
@Entity
@Table(name = "mensaje_procesado",
    indexes = @Index(name = "idx_mensaje_procesado_procesado_en",
        columnList = "procesado_en"))
public class MensajeProcesadoEntity {

    @EmbeddedId
    private MensajeProcesadoId id;

    @Column(name = "tipo_mensaje", nullable = false, length = 100)
    private String tipoMensaje;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    public MensajeProcesadoEntity() {}

    public MensajeProcesadoEntity(String messageId, String consumerGrupo,
                                   String tipoMensaje) {
        this.id          = new MensajeProcesadoId(messageId, consumerGrupo);
        this.tipoMensaje = tipoMensaje;
        this.procesadoEn = Instant.now();
    }

    public MensajeProcesadoId getId()     { return id; }
    public String getTipoMensaje()        { return tipoMensaje; }
    public Instant getProcesadoEn()       { return procesadoEn; }
}
