package com.javeriana.eventos.inscription.infrastructure.clientes;

import com.javeriana.eventos.inscription.domain.exceptions.ServicioExternoNoDisponibleException;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort.TarifaInfo;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adaptador que implementa EventoServicePort usando Feign + Circuit Breaker.
 *
 * Patrón de resiliencia (ADR-005, hallazgo C-03):
 *
 *   CLOSED (normal) → delega directamente al FeignClient.
 *
 *   OPEN (circuito abierto tras ≥50% de fallos en ventana de 10 llamadas):
 *     → obtenerEvento(): fallback lanza ServicioExternoNoDisponibleException.
 *       Resultado: la inscripción no se crea → HTTP 503 informativo al usuario.
 *     → liberarCupo(): fallback loguea y continúa sin propagar error.
 *       Razón: la inscripción ya está EXPIRADA en BD; no propagar mantiene
 *       la consistencia local. TODO: publicar evento de compensación (Prompt 14).
 *
 *   HALF_OPEN (3 llamadas de prueba tras 15s en OPEN):
 *     → Si pasan, transición → CLOSED.
 *     → Si fallan, transición → OPEN de nuevo.
 *
 * Se usa CircuitBreaker programático para evitar depender de AOP en el
 * camino crítico de degradación.
 */
@Component
public class EventoServiceAdapter implements EventoServicePort {

    private static final Logger log = LoggerFactory.getLogger(EventoServiceAdapter.class);
    private static final String CB_NAME = "evento-service";

    private final EventoServiceFeignClient feignClient;
    private final CircuitBreaker circuitBreaker;

    public EventoServiceAdapter(EventoServiceFeignClient feignClient,
                                CircuitBreakerRegistry circuitBreakerRegistry) {
        this.feignClient = feignClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CB_NAME);
    }

    @Override
    public EventoInfo obtenerEvento(UUID eventoId) {
        try {
            return circuitBreaker.executeSupplier(() -> feignClient.obtenerEvento(eventoId));
        } catch (Exception ex) {
            throw obtenerEventoFallback(eventoId, ex);
        }
    }

    @Override
    public void liberarCupo(UUID eventoId) {
        try {
            circuitBreaker.executeRunnable(() -> feignClient.liberarCupo(eventoId));
        } catch (Exception ex) {
            liberarCupoFallback(eventoId, ex);
        }
    }

    @Override
    public TarifaInfo obtenerTarifa(UUID tarifaId) {
        try {
            return circuitBreaker.executeSupplier(() -> feignClient.obtenerTarifa(tarifaId));
        } catch (Exception ex) {
            throw obtenerTarifaFallback(tarifaId, ex);
        }
    }

    // ─── Fallbacks (package-private para testabilidad directa) ───────────────

    /**
     * Circuito abierto para obtenerEvento: lanzar excepción para que el
     * controlador retorne 503 al cliente (no tiene sentido crear inscripción
     * si no podemos verificar que el evento existe y acepta inscripciones).
     *
     * Package-private para permitir tests unitarios directos sin Spring AOP.
     */
    ServicioExternoNoDisponibleException obtenerEventoFallback(UUID eventoId, Exception ex) {
        registrarFalloEventoService("obtener evento=" + eventoId, ex);
        throw new ServicioExternoNoDisponibleException("event-service", ex);
    }

    /**
     * Circuito abierto para liberarCupo: degradación controlada.
     * La inscripción ya fue marcada EXPIRADA en BD; perder esta llamada
     * solo significa que el cupo en event-service queda "reservado" hasta
     * que se aplique la compensación asíncrona (Prompt 14).
     *
     * Package-private para permitir tests unitarios directos sin Spring AOP.
     */
    void liberarCupoFallback(UUID eventoId, Exception ex) {
        registrarFalloEventoService(
            "liberar cupo para evento=" + eventoId + ". Pendiente compensación asíncrona",
            ex
        );
        // No propagamos: la inscripción ya expiró en BD.
        // TODO: publicar InscripcionCupoPendienteLiberacionEvent al outbox para compensación
    }

    /**
     * Circuito abierto para obtenerTarifa: lanzar excepción para que el controlador
     * retorne 503 al cliente (no crear inscripción sin precio de tarifa real).
     */
    ServicioExternoNoDisponibleException obtenerTarifaFallback(UUID tarifaId, Exception ex) {
        registrarFalloEventoService("obtener tarifa=" + tarifaId, ex);
        throw new ServicioExternoNoDisponibleException("event-service", ex);
    }

    private void registrarFalloEventoService(String operacion, Exception ex) {
        if (ex instanceof CallNotPermittedException) {
            log.debug("[circuit-breaker] event-service OPEN al {}: {}", operacion, ex.getMessage());
            return;
        }
        log.error("[circuit-breaker] event-service no disponible al {}: {}", operacion, ex.getMessage());
    }
}
