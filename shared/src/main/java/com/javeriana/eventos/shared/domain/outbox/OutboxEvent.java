package com.javeriana.eventos.shared.domain.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * POJO de dominio del Outbox Pattern (ADR-011).
 *
 * Representa un evento de dominio pendiente de publicar en RabbitMQ.
 * En vez de publicar directamente desde la transacción de negocio,
 * se persiste este registro en la misma TX. Un proceso relay
 * (OutboxRelayService) lo lee periódicamente y lo publica de forma asíncrona.
 *
 * Garantía: si el servicio cae después del COMMIT y antes de publicar,
 * el evento permanece PENDIENTE y se publicará al reiniciar.
 *
 * Ubicado en domain (no en infrastructure) porque modela un concepto
 * del dominio de mensajería, no un detalle técnico de persistencia.
 */
public class OutboxEvent {

    private final UUID id;
    private final String aggregateType;
    private final UUID aggregateId;
    private final String eventType;
    private final String payload;
    private EstadoOutbox estado;
    private int intentos;
    private final Instant creadoEn;
    private Instant enviadoEn;

    /**
     * Constructor de CREACIÓN — para producir un nuevo evento de dominio.
     * Genera el UUID y establece estado PENDIENTE automáticamente.
     */
    public OutboxEvent(String aggregateType, UUID aggregateId,
                       String eventType, String payload) {
        this.id            = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId   = aggregateId;
        this.eventType     = eventType;
        this.payload       = payload;
        this.estado        = EstadoOutbox.PENDIENTE;
        this.intentos      = 0;
        this.creadoEn      = Instant.now();
        this.enviadoEn     = null;
    }

    /**
     * Constructor de RECONSTRUCCIÓN — usado por los adaptadores JPA al mapear
     * desde la entidad persistida. PRESERVA el id original de la BD; no genera
     * uno nuevo (corrige bug hallazgo #1 de la auditoría).
     */
    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId,
                       String eventType, String payload, EstadoOutbox estado,
                       int intentos, Instant creadoEn, Instant enviadoEn) {
        this.id            = id;
        this.aggregateType = aggregateType;
        this.aggregateId   = aggregateId;
        this.eventType     = eventType;
        this.payload       = payload;
        this.estado        = estado;
        this.intentos      = intentos;
        this.creadoEn      = creadoEn;
        this.enviadoEn     = enviadoEn;
    }

    // ─── Métodos de dominio ────────────────────────────────────────────────

    public void marcarProcesado() {
        this.estado    = EstadoOutbox.ENVIADO;
        this.enviadoEn = Instant.now();
    }

    public void incrementarIntentos() {
        this.intentos++;
    }

    public void marcarFallido() {
        this.estado = EstadoOutbox.FALLIDO;
    }

    public boolean estaPendiente() {
        return estado == EstadoOutbox.PENDIENTE;
    }

    public boolean puedeReintentar(int maxIntentos) {
        return intentos < maxIntentos && estado == EstadoOutbox.PENDIENTE;
    }

    // ─── Getters (no hay setters — inmutable salvo métodos de dominio) ─────

    public UUID getId()             { return id; }
    public String getAggregateType(){ return aggregateType; }
    public UUID getAggregateId()    { return aggregateId; }
    public String getEventType()    { return eventType; }
    public String getPayload()      { return payload; }
    public EstadoOutbox getEstado() { return estado; }
    public int getIntentos()        { return intentos; }
    public Instant getCreadoEn()    { return creadoEn; }
    public Instant getEnviadoEn()   { return enviadoEn; }
}
