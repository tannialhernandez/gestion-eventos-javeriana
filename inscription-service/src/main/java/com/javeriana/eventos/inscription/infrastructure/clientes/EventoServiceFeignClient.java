package com.javeriana.eventos.inscription.infrastructure.clientes;

import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort.EventoInfo;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort.TarifaInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/**
 * Cliente Feign hacia event-service.
 *
 * Esta interfaz NO implementa el puerto de dominio (EventoServicePort)
 * para mantener la separación hexagonal: la infraestructura sabe de la
 * red, el dominio solo sabe de operaciones de negocio.
 *
 * El adaptador EventoServiceAdapter es el puente entre este cliente y el
 * puerto, añadiendo Circuit Breaker transparentemente.
 */
@FeignClient(name = "evento-service", url = "${services.event-service.url}")
public interface EventoServiceFeignClient {

    @GetMapping("/api/v1/eventos/{eventoId}")
    EventoInfo obtenerEvento(@PathVariable("eventoId") UUID eventoId);

    /**
     * Notifica a event-service que una inscripción fue expirada o cancelada,
     * para que incremente el cupo disponible en su propia BD.
     */
    @PostMapping("/api/v1/eventos/{eventoId}/cupos/liberar")
    void liberarCupo(@PathVariable("eventoId") UUID eventoId);

    @PostMapping("/api/v1/eventos/{eventoId}/cupos/reservar")
    void reservarCupo(@PathVariable("eventoId") UUID eventoId);

    /**
     * Obtiene los datos de precio de una tarifa (M-03: elimina monto hardcodeado).
     * La tarifa pertenece a un evento y define el precio en una moneda específica.
     */
    @GetMapping("/api/v1/tarifas/{tarifaId}")
    TarifaInfo obtenerTarifa(@PathVariable("tarifaId") UUID tarifaId);
}
