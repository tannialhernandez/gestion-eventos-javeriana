package com.javeriana.eventos.inscription.infrastructure.clientes;

import com.javeriana.eventos.inscription.infrastructure.observability.MdcKeys;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Interceptor Feign que propaga el Correlation-ID a los servicios externos (SAD §8.3).
 *
 * Cuando inscription-service llama a event-service o payment-service, incluye el
 * correlationId del MDC en el header "X-Correlation-Id". Esto permite correlacionar
 * logs entre los tres servicios para una sola solicitud de inscripción.
 *
 * Ejemplo de flujo:
 *   POST /api/v1/inscripciones (correlationId: abc-123)
 *     → Feign GET /api/v1/eventos/{id}   [X-Correlation-Id: abc-123]
 *     → Feign POST /api/v1/pagos/prefer. [X-Correlation-Id: abc-123]
 *
 * Si no hay correlationId en el MDC (ej: llamadas internas sin HTTP context),
 * el interceptor no añade el header (no forzamos un ID incorrecto).
 */
@Component
public class CorrelationIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        if (correlationId != null && !correlationId.isBlank()) {
            template.header(MdcKeys.HTTP_HEADER, correlationId);
        }
    }
}
