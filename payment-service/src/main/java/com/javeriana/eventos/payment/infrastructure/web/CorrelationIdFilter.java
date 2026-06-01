package com.javeriana.eventos.payment.infrastructure.web;

import com.javeriana.eventos.payment.infrastructure.observability.MdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro HTTP que establece el Correlation-ID para trazabilidad E2E (SAD §8.3).
 *
 * M-03 (Prompt 18/19): payment-service no tenía este filtro, rompiendo la
 * cadena de correlación inscription → payment → inscription.
 *
 * Equivalente al CorrelationIdFilter de inscription-service (Prompt 13).
 * Orden HIGHEST_PRECEDENCE: debe ejecutarse ANTES de cualquier otro filtro.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(MdcKeys.HTTP_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MdcKeys.CORRELATION_ID, correlationId);
        response.setHeader(MdcKeys.HTTP_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.CORRELATION_ID);
        }
    }
}
