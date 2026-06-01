package com.javeriana.eventos.payment.domain.port.out;

/**
 * Puerto de salida para idempotencia entrante de mensajes AMQP.
 *
 * Cuando payment-service consuma eventos externos (ej. InscripcionCreadaEvent),
 * el listener debe:
 *  1. Llamar estaProcesado(messageId) antes de procesar
 *  2. Si true  → descartar el mensaje (ya procesado)
 *  3. Si false → procesar el mensaje y llamar registrarProcesado(...)
 *
 * El messageId debe provenir del header AMQP "messageId" (seteado por el
 * OutboxRelayService del servicio emisor como UUID del evento de dominio).
 *
 * Interface pura: sin anotaciones Spring ni dependencias JPA.
 */
public interface MensajeProcesadoRepository {

    /** Retorna true si el messageId ya fue procesado por este consumerGroup. */
    boolean estaProcesado(String messageId, String consumerGroup);

    /**
     * Registra que el mensaje fue procesado exitosamente.
     * Debe llamarse DENTRO de la misma transacción del procesamiento
     * para garantizar atomicidad: si el procesamiento falla (rollback),
     * el registro de idempotencia también se revierte.
     */
    void registrarProcesado(String messageId, String consumerGroup, String eventType);
}
