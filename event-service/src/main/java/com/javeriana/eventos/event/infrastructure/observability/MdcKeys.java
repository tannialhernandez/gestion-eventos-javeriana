package com.javeriana.eventos.event.infrastructure.observability;

/**
 * Constantes de claves MDC para trazabilidad distribuida (SAD §8.3, Prompt 21).
 */
public final class MdcKeys {
    public static final String CORRELATION_ID = "correlationId";
    public static final String USER_ID        = "userId";
    public static final String HTTP_HEADER    = "X-Correlation-Id";
    public static final String AMQP_HEADER    = "x-correlation-id";
    private MdcKeys() {}
}
