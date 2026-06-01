package com.javeriana.eventos.payment.domain.events;

/**
 * Lanzada cuando no se puede serializar un DomainEvent a JSON para el Outbox.
 * Es RuntimeException porque la serialización no debería fallar en producción;
 * si falla, indica un problema de configuración o datos corruptos.
 */
public class SerializacionPayloadException extends RuntimeException {

    public SerializacionPayloadException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
