package com.javeriana.eventos.inscription.infrastructure.clientes;

import com.javeriana.eventos.inscription.domain.exceptions.ServicioExternoNoDisponibleException;
import com.javeriana.eventos.inscription.domain.port.out.PaymentServicePort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adaptador que implementa PaymentServicePort usando Feign + Circuit Breaker.
 *
 * Patrón de resiliencia (ADR-005, hallazgo C-03):
 *
 *   CLOSED → delega al FeignClient.
 *   OPEN   → fallback lanza ServicioExternoNoDisponibleException.
 *
 * Si payment-service no está disponible, no podemos crear la preferencia de
 * pago → lanzar excepción es semánticamente correcto (no crear inscripción
 * sin flujo de pago disponible → mejor UX que crear y no poder cobrar).
 *
 * Métricas: Resilience4j expone automáticamente a Micrometer los contadores
 * de llamadas exitosas, fallidas y transiciones OPEN/CLOSED/HALF_OPEN bajo
 * el prefijo `resilience4j.circuitbreaker.payment-service.*`.
 */
@Component
public class PaymentServiceAdapter implements PaymentServicePort {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceAdapter.class);
    private static final String CB_NAME = "payment-service";

    private final PaymentServiceFeignClient feignClient;

    public PaymentServiceAdapter(PaymentServiceFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "crearPreferenciaFallback")
    public PreferenciaPago crearPreferencia(UUID inscripcionId, BigDecimal monto,
                                             String moneda, UUID usuarioId) {
        return feignClient.crearPreferencia(
            new PaymentServiceFeignClient.CrearPreferenciaRequest(
                inscripcionId, monto, moneda, usuarioId));
    }

    @Override
    public ReembolsoPago reembolsar(UUID inscripcionId) {
        try {
            return feignClient.reembolsar(inscripcionId);
        } catch (Exception ex) {
            log.error("[payment] no fue posible reembolsar inscripcion={}: {}", inscripcionId, ex.getMessage());
            throw new ServicioExternoNoDisponibleException("payment-service", ex);
        }
    }

    // ─── Fallback ─────────────────────────────────────────────────────────────

    /**
     * Package-private para permitir tests unitarios directos sin Spring AOP.
     */
    PreferenciaPago crearPreferenciaFallback(UUID inscripcionId,
                                             BigDecimal monto,
                                             String moneda,
                                             UUID usuarioId,
                                             Throwable ex) {
        log.error("[circuit-breaker] payment-service no disponible al crear preferencia " +
                  "para inscripcion={}: {}", inscripcionId, ex.getMessage());
        throw new ServicioExternoNoDisponibleException("payment-service", ex);
    }
}
