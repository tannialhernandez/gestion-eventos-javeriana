package com.javeriana.eventos.inscription.infrastructure.clientes;

import com.javeriana.eventos.inscription.domain.exceptions.ServicioExternoNoDisponibleException;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort.EventoInfo;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de EventoServiceAdapter.
 *
 * NOTA: El adapter usa CircuitBreaker programático para que el camino OPEN
 * no dependa de proxies AOP.
 *
 * Probamos tanto los fallback directos como el comportamiento fail-fast
 * cuando el circuito está OPEN.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventoServiceAdapter — Circuit Breaker y fallbacks")
class EventoServiceAdapterTest {

    @Mock private EventoServiceFeignClient feignClient;

    private EventoServiceAdapter adapter;

    private static final UUID EVENTO_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowSize(2)
            .minimumNumberOfCalls(1)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .build();
        adapter = new EventoServiceAdapter(
            feignClient,
            CircuitBreakerRegistry.of(config));
    }

    // ─── obtenerEvento: camino feliz ─────────────────────────────────────────

    @Nested
    @DisplayName("obtenerEvento — camino feliz")
    class ObtenerEventoCaminoFeliz {

        @Test
        @DisplayName("Delega al FeignClient y retorna el EventoInfo")
        void debeRetornarEventoInfoDesdeFeign() {
            EventoInfo eventoEsperado = new EventoInfo(
                EVENTO_ID, "Conferencia Cloud", "ACTIVO", 50, true);
            when(feignClient.obtenerEvento(EVENTO_ID)).thenReturn(eventoEsperado);

            EventoInfo resultado = adapter.obtenerEvento(EVENTO_ID);

            assertThat(resultado).isEqualTo(eventoEsperado);
            assertThat(resultado.aceptaInscripciones()).isTrue();
            verify(feignClient).obtenerEvento(EVENTO_ID);
        }
    }

    // ─── obtenerEventoFallback: probado directamente ─────────────────────────
    // Probamos el fallback directamente (es package-private).

    @Nested
    @DisplayName("obtenerEventoFallback — método de fallback del circuit breaker")
    class ObtenerEventoFallback {

        @Test
        @DisplayName("Fallback lanza ServicioExternoNoDisponibleException")
        void fallback_debeLanzarServicioExternoNoDisponible() {
            RuntimeException causaFeign = new RuntimeException("Connection refused");

            assertThatThrownBy(() ->
                adapter.obtenerEventoFallback(EVENTO_ID, causaFeign))
                .isInstanceOf(ServicioExternoNoDisponibleException.class)
                .hasMessageContaining("event-service");
        }

        @Test
        @DisplayName("Fallback expone 'event-service' como nombre del servicio")
        void fallback_exponeNombreDelServicio() {
            RuntimeException causa = new RuntimeException("timeout");

            try {
                adapter.obtenerEventoFallback(EVENTO_ID, causa);
            } catch (ServicioExternoNoDisponibleException ex) {
                assertThat(ex.getServicio()).isEqualTo("event-service");
            }
        }

        @Test
        @DisplayName("Fallback preserva la causa original del fallo")
        void fallback_preservaCausaOriginal() {
            RuntimeException causaOriginal = new RuntimeException("503 Service Unavailable");

            assertThatThrownBy(() ->
                adapter.obtenerEventoFallback(EVENTO_ID, causaOriginal))
                .isInstanceOf(ServicioExternoNoDisponibleException.class)
                .hasCause(causaOriginal);
        }
    }

    @Nested
    @DisplayName("obtenerEvento — circuit breaker programático")
    class ObtenerEventoCircuitBreaker {

        @Test
        @DisplayName("Cuando Feign falla, responde con ServicioExternoNoDisponibleException")
        void cuandoFeignFalla_debeLanzarServicioNoDisponible() {
            when(feignClient.obtenerEvento(EVENTO_ID))
                .thenThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> adapter.obtenerEvento(EVENTO_ID))
                .isInstanceOf(ServicioExternoNoDisponibleException.class)
                .hasMessageContaining("event-service");
        }

        @Test
        @DisplayName("Cuando el circuito abre, no vuelve a llamar al FeignClient")
        void cuandoCircuitoAbierto_debeFallarRapidoSinFeign() {
            when(feignClient.obtenerEvento(EVENTO_ID))
                .thenThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> adapter.obtenerEvento(EVENTO_ID))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
            clearInvocations(feignClient);

            assertThatThrownBy(() -> adapter.obtenerEvento(EVENTO_ID))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);

            verifyNoInteractions(feignClient);
        }
    }

    // ─── liberarCupo ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("liberarCupo — delegación al FeignClient")
    class LiberarCupo {

        @Test
        @DisplayName("Feign exitoso: delega la llamada y retorna sin excepción")
        void cuandoFeignExitoso_debeLiberarSinExcepcion() {
            doNothing().when(feignClient).liberarCupo(EVENTO_ID);

            assertThatNoException()
                .isThrownBy(() -> adapter.liberarCupo(EVENTO_ID));

            verify(feignClient).liberarCupo(EVENTO_ID);
        }

    }

    // ─── liberarCupoFallback: probado directamente ────────────────────────────

    @Nested
    @DisplayName("liberarCupoFallback — degradación controlada (directo)")
    class LiberarCupoFallback {

        @Test
        @DisplayName("Fallback NO propaga excepción (degradación silenciosa)")
        void fallback_noDebePropagarExcepcion() {
            RuntimeException causaCircuito = new RuntimeException("event-service OPEN");

            assertThatNoException()
                .isThrownBy(() ->
                    adapter.liberarCupoFallback(EVENTO_ID, causaCircuito));
        }
    }
}
