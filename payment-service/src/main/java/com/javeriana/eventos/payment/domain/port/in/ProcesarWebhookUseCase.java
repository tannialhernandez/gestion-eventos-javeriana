package com.javeriana.eventos.payment.domain.port.in;

public interface ProcesarWebhookUseCase {

    /**
     * Procesa la notificación de la pasarela de pago.
     * Garantiza idempotencia: si referencia_externa ya existe → HTTP 200 sin reprocessing.
     */
    ResultadoWebhook procesar(WebhookPayload payload);

    record WebhookPayload(
        String referenciaExterna,   // payment_id de MercadoPago
        String inscripcionId,        // external_reference enviado al crear la preferencia
        String estado,               // "approved", "rejected", "refunded"
        String metadatosJson         // payload completo para auditoría
    ) {}

    enum ResultadoWebhook {
        CONFIRMADO,         // Pago confirmado, inscripción notificada
        DUPLICADO,          // Ya procesado antes — idempotente
        INSCRIPCION_EXPIRADA, // Pago tardío — reembolso emitido
        RECHAZADO           // Pago rechazado por la pasarela
    }
}
