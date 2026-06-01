package com.javeriana.eventos.inscription.infrastructure.observability;

import com.javeriana.eventos.inscription.infrastructure.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios de CorrelationIdFilter.
 * Usa mocks de Servlet (spring-test) sin necesidad de contexto Spring.
 */
@DisplayName("CorrelationIdFilter — generación y propagación de correlationId")
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        chain  = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // ─── Generación de correlationId ──────────────────────────────────────

    @Nested
    @DisplayName("Generación del correlationId")
    class GeneracionCorrelationId {

        @Test
        @DisplayName("Request sin X-Correlation-Id → genera UUID nuevo")
        void sinHeader_debeGenerarUuidNuevo() throws Exception {
            MockHttpServletRequest request  = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            String enRespuesta = response.getHeader(MdcKeys.HTTP_HEADER);
            assertThat(enRespuesta)
                .as("Debe retornar un correlationId generado en la respuesta")
                .isNotNull()
                .isNotBlank();

            // Debe ser parseable como UUID
            assertThat(java.util.UUID.fromString(enRespuesta)).isNotNull();
        }

        @Test
        @DisplayName("Request con X-Correlation-Id → lo reutiliza")
        void conHeader_debeReutilizarElExistente() throws Exception {
            String correlationExistente = "abc-123-def-456";
            MockHttpServletRequest request  = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.addHeader(MdcKeys.HTTP_HEADER, correlationExistente);

            filter.doFilter(request, response, chain);

            assertThat(response.getHeader(MdcKeys.HTTP_HEADER))
                .isEqualTo(correlationExistente);
        }
    }

    // ─── Propagación al MDC ───────────────────────────────────────────────

    @Nested
    @DisplayName("Propagación al MDC durante el request")
    class PropagacionMdc {

        @Test
        @DisplayName("correlationId está en MDC durante la ejecución del filtro")
        void correlationIdEstaEnMdcDuranteEjecucion() throws Exception {
            String correlationId = "corr-test-123";
            MockHttpServletRequest request  = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.addHeader(MdcKeys.HTTP_HEADER, correlationId);

            final String[] mdcDuranteEjecucion = {null};
            MockFilterChain capturador = new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest req,
                                     jakarta.servlet.ServletResponse res)
                        throws java.io.IOException, jakarta.servlet.ServletException {
                    mdcDuranteEjecucion[0] = MDC.get(MdcKeys.CORRELATION_ID);
                }
            };

            filter.doFilter(request, response, capturador);

            assertThat(mdcDuranteEjecucion[0])
                .as("MDC debe tener correlationId durante el procesamiento del request")
                .isEqualTo(correlationId);
        }

        @Test
        @DisplayName("correlationId es eliminado del MDC después del request (no fuga de thread)")
        void correlationIdLimpiadoDelMdcTrasRequest() throws Exception {
            MockHttpServletRequest request  = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.addHeader(MdcKeys.HTTP_HEADER, "corr-para-limpiar");

            filter.doFilter(request, response, chain);

            assertThat(MDC.get(MdcKeys.CORRELATION_ID))
                .as("MDC debe estar limpio después del request para evitar fugas entre threads")
                .isNull();
        }
    }
}
