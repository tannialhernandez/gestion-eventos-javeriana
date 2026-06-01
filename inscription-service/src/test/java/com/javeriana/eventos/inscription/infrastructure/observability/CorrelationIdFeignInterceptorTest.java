package com.javeriana.eventos.inscription.infrastructure.observability;

import com.javeriana.eventos.inscription.infrastructure.clientes.CorrelationIdFeignInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios del interceptor Feign de trazabilidad.
 * Sin Spring context — puro JUnit 5.
 */
@DisplayName("CorrelationIdFeignInterceptor — propagación al header Feign")
class CorrelationIdFeignInterceptorTest {

    private CorrelationIdFeignInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new CorrelationIdFeignInterceptor();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("MDC tiene correlationId → lo inyecta en el header X-Correlation-Id")
    void conCorrelationIdEnMdc_inyectaHeader() {
        String correlationId = "corr-feign-test-789";
        MDC.put(MdcKeys.CORRELATION_ID, correlationId);

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers())
            .as("El header X-Correlation-Id debe estar presente en el request Feign")
            .containsKey(MdcKeys.HTTP_HEADER);
        assertThat(template.headers().get(MdcKeys.HTTP_HEADER))
            .contains(correlationId);
    }

    @Test
    @DisplayName("MDC sin correlationId → NO inyecta el header (no fuerza ID incorrecto)")
    void sinCorrelationIdEnMdc_noInyectaHeader() {
        // MDC vacío
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers())
            .as("Sin correlationId en MDC, el header no debe añadirse")
            .doesNotContainKey(MdcKeys.HTTP_HEADER);
    }

    @Test
    @DisplayName("Cada request Feign recibe el correlationId actual del MDC")
    void cadaRequestUsaCorrelationIdActualDelMdc() {
        MDC.put(MdcKeys.CORRELATION_ID, "primer-request");
        RequestTemplate template1 = new RequestTemplate();
        interceptor.apply(template1);

        MDC.put(MdcKeys.CORRELATION_ID, "segundo-request");
        RequestTemplate template2 = new RequestTemplate();
        interceptor.apply(template2);

        assertThat(template1.headers().get(MdcKeys.HTTP_HEADER)).contains("primer-request");
        assertThat(template2.headers().get(MdcKeys.HTTP_HEADER)).contains("segundo-request");
    }
}
