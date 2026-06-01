package com.javeriana.eventos.payment.infrastructure.web.exception;

/**
 * Lanzada cuando la firma HMAC-SHA256 del Webhook no coincide.
 * El controller la mapea a HTTP 401 Unauthorized.
 */
public class WebhookFirmaInvalidaException extends RuntimeException {

    public WebhookFirmaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
