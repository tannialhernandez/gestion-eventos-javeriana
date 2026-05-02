package com.javeriana.eventos.payment.application;

/**
 * Lanzada por el fallback del Circuit Breaker.
 * El controller la mapea a HTTP 503 con retry_after.
 */
public class PasarelaNoDisponibleException extends RuntimeException {

    public PasarelaNoDisponibleException(String message) {
        super(message);
    }
}
