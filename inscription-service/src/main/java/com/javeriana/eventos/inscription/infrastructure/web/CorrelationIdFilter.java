package com.javeriana.eventos.inscription.infrastructure.web;

import com.javeriana.eventos.inscription.infrastructure.observability.MdcKeys;
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
 * Filtro HTTP que establece el Correlation-ID para la trazabilidad E2E (SAD §8.3).
 *
 * Orden: HIGHEST_PRECEDENCE — debe ejecutarse ANTES de JwtAuthFilter y cualquier
 * otro filtro que use el MDC, para que todos los logs del request tengan correlationId.
 *
 * Comportamiento:
 *  - Si el request incluye "X-Correlation-Id" → lo reutiliza (cliente ya tiene el ID).
 *  - Si no → genera un UUID nuevo (inscripción creada directamente o desde API gateway).
 *  - Siempre retorna el correlationId en el header de respuesta para que el cliente
 *    pueda rastrearlo en sus propios sistemas.
 *  - Limpia el MDC al final del request para evitar fugas entre threads (pool Tomcat).
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
            // Limpiar solo correlationId; userId y otros los limpia su filtro/servicio
            MDC.remove(MdcKeys.CORRELATION_ID);
        }
    }
}
