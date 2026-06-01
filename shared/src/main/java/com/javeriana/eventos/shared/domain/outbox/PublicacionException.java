package com.javeriana.eventos.shared.domain.outbox;

/**
 * Señala que un evento del Outbox no pudo publicarse en el broker de mensajería.
 *
 * Causas posibles:
 *  - NACK del broker (mensaje rechazado por RabbitMQ)
 *  - Timeout esperando la confirmación del broker
 *  - Error de red transitorio
 *
 * El OutboxRelayService captura esta excepción para incrementar el contador
 * de intentos y, si se superó MAX_INTENTOS, marcar el evento como FALLIDO.
 */
public class PublicacionException extends Exception {

    public PublicacionException(String mensaje) {
        super(mensaje);
    }

    public PublicacionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
