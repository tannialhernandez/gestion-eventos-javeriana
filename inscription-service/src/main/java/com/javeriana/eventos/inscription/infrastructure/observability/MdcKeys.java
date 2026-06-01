package com.javeriana.eventos.inscription.infrastructure.observability;

/**
 * Constantes de claves MDC para trazabilidad distribuida (SAD §8.3).
 *
 * Todas las partes del sistema que enriquecen el MDC deben usar estas constantes
 * para garantizar nombres consistentes en logs y permitir correlación en CloudWatch.
 *
 * Flujo de propagación:
 *  HTTP entrante  → CorrelationIdFilter       → MDC[correlationId]
 *  JWT válido     → JwtAuthFilter             → MDC[userId]
 *  Caso de uso    → *Service                  → MDC[inscripcionId, eventoId]
 *  Feign saliente → CorrelationIdFeignIntercep → header X-Correlation-Id
 *  AMQP publish   → OutboxRelayService         → header x-correlation-id
 *  AMQP consume   → PagoConfirmadoConsumer     → MDC[correlationId]
 */
public final class MdcKeys {

    // ─── Claves MDC ──────────────────────────────────────────────────────────
    public static final String CORRELATION_ID = "correlationId";
    public static final String USER_ID        = "userId";
    public static final String INSCRIPCION_ID = "inscripcionId";
    public static final String EVENTO_ID      = "eventoId";
    public static final String MESSAGE_ID     = "messageId";

    // ─── Nombres de headers ───────────────────────────────────────────────────
    /** Header HTTP (case-insensitive en HTTP/1.1) */
    public static final String HTTP_HEADER  = "X-Correlation-Id";
    /** Header AMQP (lower-case por convención RabbitMQ) */
    public static final String AMQP_HEADER  = "x-correlation-id";

    private MdcKeys() {}
}
