package com.javeriana.eventos.inscription.domain.exceptions;

/**
 * Señala que un servicio externo (event-service, payment-service) no está
 * disponible porque el Circuit Breaker está abierto o el servicio tardó
 * más de su SLA configurado.
 *
 * Producida por: EventoServiceAdapter, PaymentServiceAdapter cuando el
 * Circuit Breaker activa el método de fallback.
 *
 * El InscripcionController la mapea a HTTP 503 Service Unavailable.
 */
public class ServicioExternoNoDisponibleException extends RuntimeException {

    private final String servicio;

    public ServicioExternoNoDisponibleException(String servicio, Throwable causa) {
        super("Servicio externo no disponible: " + servicio
              + " (circuit breaker abierto o timeout)", causa);
        this.servicio = servicio;
    }

    public ServicioExternoNoDisponibleException(String servicio, String detalle) {
        super("Servicio externo no disponible: " + servicio + " — " + detalle);
        this.servicio = servicio;
    }

    public String getServicio() { return servicio; }
}
