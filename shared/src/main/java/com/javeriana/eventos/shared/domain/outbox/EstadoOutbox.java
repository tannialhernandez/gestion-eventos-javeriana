package com.javeriana.eventos.shared.domain.outbox;

/**
 * Estados del ciclo de vida de un evento en el Outbox Pattern (ADR-011).
 *
 * PENDIENTE → ENVIADO  (camino feliz: relay publicó en RabbitMQ)
 * PENDIENTE → FALLIDO  (agotados los intentos de publicación)
 */
public enum EstadoOutbox {
    PENDIENTE,
    ENVIADO,
    FALLIDO
}
