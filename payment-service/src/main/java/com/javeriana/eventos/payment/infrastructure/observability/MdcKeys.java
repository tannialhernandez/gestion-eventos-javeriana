package com.javeriana.eventos.payment.infrastructure.observability;

/**
 * Constantes de claves MDC para trazabilidad distribuida (SAD §8.3, Prompt 13).
 *
 * Espejo de inscription-service.MdcKeys para garantizar nombres consistentes
 * en logs de ambos servicios y permitir correlación en CloudWatch.
 */
public final class MdcKeys {

    public static final String CORRELATION_ID = "correlationId";
    public static final String USER_ID        = "userId";

    /** Header HTTP para propagación del correlationId (case-insensitive HTTP/1.1) */
    public static final String HTTP_HEADER = "X-Correlation-Id";
    /** Header AMQP para propagación del correlationId (lower-case por convención RabbitMQ) */
    public static final String AMQP_HEADER = "x-correlation-id";

    private MdcKeys() {}
}
