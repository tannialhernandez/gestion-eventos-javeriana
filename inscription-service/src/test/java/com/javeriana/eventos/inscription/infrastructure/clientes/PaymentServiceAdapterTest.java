package com.javeriana.eventos.inscription.infrastructure.clientes;

import com.javeriana.eventos.inscription.domain.exceptions.ServicioExternoNoDisponibleException;
import com.javeriana.eventos.inscription.domain.port.out.PaymentServicePort.PreferenciaPago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceAdapter — Circuit Breaker y delegación a Feign")
class PaymentServiceAdapterTest {

    @Mock private PaymentServiceFeignClient feignClient;

    private PaymentServiceAdapter adapter;

    private static final UUID          INSCRIPCION_ID = UUID.randomUUID();
    private static final UUID          USUARIO_ID     = UUID.randomUUID();
    private static final BigDecimal    MONTO          = new BigDecimal("150000.00");
    private static final String        MONEDA         = "COP";

    @BeforeEach
    void setUp() {
        adapter = new PaymentServiceAdapter(feignClient);
    }

    // ─── Camino feliz ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("crearPreferencia — camino feliz")
    class CaminoFeliz {

        @Test
        @DisplayName("Delega al FeignClient con los parámetros correctos")
        void debeCrearPreferenciaYRetornarResultado() {
            PreferenciaPago preferenciaEsperada = new PreferenciaPago(
                UUID.randomUUID(), "https://pagos.test/checkout", "PREF-001");
            when(feignClient.crearPreferencia(any())).thenReturn(preferenciaEsperada);

            PreferenciaPago resultado = adapter.crearPreferencia(
                INSCRIPCION_ID, MONTO, MONEDA, USUARIO_ID);

            assertThat(resultado).isEqualTo(preferenciaEsperada);
            assertThat(resultado.checkoutUrl()).contains("https://");
        }

        @Test
        @DisplayName("El request enviado al FeignClient contiene todos los campos correctos")
        void requestFeignContieneTodosLosCamposCorrectos() {
            when(feignClient.crearPreferencia(any()))
                .thenReturn(new PreferenciaPago(UUID.randomUUID(), "url", "pref"));

            adapter.crearPreferencia(INSCRIPCION_ID, MONTO, MONEDA, USUARIO_ID);

            ArgumentCaptor<PaymentServiceFeignClient.CrearPreferenciaRequest> captor =
                ArgumentCaptor.forClass(PaymentServiceFeignClient.CrearPreferenciaRequest.class);
            verify(feignClient).crearPreferencia(captor.capture());

            PaymentServiceFeignClient.CrearPreferenciaRequest req = captor.getValue();
            assertThat(req.inscripcionId()).isEqualTo(INSCRIPCION_ID);
            assertThat(req.monto()).isEqualByComparingTo(MONTO);
            assertThat(req.moneda()).isEqualTo(MONEDA);
            assertThat(req.usuarioId()).isEqualTo(USUARIO_ID);
        }
    }

    // ─── crearPreferenciaFallback: probado directamente ─────────────────────
    // NOTA: en tests sin Spring AOP, @CircuitBreaker no intercepta.
    // Probamos el fallback directamente (es package-private).

    @Nested
    @DisplayName("crearPreferenciaFallback — método de fallback del circuit breaker")
    class FallbackDirecto {

        @Test
        @DisplayName("Fallback lanza ServicioExternoNoDisponibleException")
        void fallback_debeLanzarServicioExternoNoDisponible() {
            RuntimeException causaCircuito = new RuntimeException("payment-service unreachable");

            assertThatThrownBy(() ->
                adapter.crearPreferenciaFallback(
                    INSCRIPCION_ID, MONTO, MONEDA, USUARIO_ID, causaCircuito))
                .isInstanceOf(ServicioExternoNoDisponibleException.class)
                .hasMessageContaining("payment-service");
        }

        @Test
        @DisplayName("Fallback expone 'payment-service' como nombre del servicio")
        void fallback_exponeNombreDelServicio() {
            RuntimeException causa = new RuntimeException("timeout");

            try {
                adapter.crearPreferenciaFallback(
                    INSCRIPCION_ID, MONTO, MONEDA, USUARIO_ID, causa);
            } catch (ServicioExternoNoDisponibleException ex) {
                assertThat(ex.getServicio()).isEqualTo("payment-service");
            }
        }

        @Test
        @DisplayName("Fallback preserva la causa raíz del fallo de Feign")
        void fallback_preservaCausaRaizDeFeign() {
            RuntimeException causaOriginal = new RuntimeException("503 Service Unavailable");

            assertThatThrownBy(() ->
                adapter.crearPreferenciaFallback(
                    INSCRIPCION_ID, MONTO, MONEDA, USUARIO_ID, causaOriginal))
                .isInstanceOf(ServicioExternoNoDisponibleException.class)
                .hasCause(causaOriginal);
        }
    }
}
