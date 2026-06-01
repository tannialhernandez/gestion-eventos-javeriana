package com.javeriana.eventos.inscription.domain.port.out;

/**
 * Puerto de salida para idempotencia de consumidores AMQP.
 *
 * Garantiza que cada mensaje sea procesado exactamente una vez a nivel
 * de lógica de negocio, incluso ante reentregas del broker (at-least-once).
 *
 * Uso en un @RabbitListener:
 *  1. Llamar estaProcesado(messageId, consumerGrupo) al recibir el mensaje.
 *  2. Si true → descartar (ya procesado), retornar sin error.
 *  3. Si false → ejecutar lógica de negocio.
 *  4. Llamar registrarProcesado(...) DENTRO de la misma TX que el negocio.
 *     Si la TX hace rollback → el registro también se revierte → próximo
 *     intento lo procesará de nuevo (correcto).
 *
 * Interface pura: sin anotaciones Spring ni dependencias JPA.
 */
public interface MensajeProcesadoRepository {

    /**
     * Retorna true si el messageId ya fue procesado por este consumerGrupo.
     *
     * @param messageId    valor del header AMQP "messageId"
     * @param consumerGrupo nombre del consumer (ej: "confirmar-inscripcion")
     */
    boolean estaProcesado(String messageId, String consumerGrupo);

    /**
     * Registra el mensaje como procesado. Llamar DENTRO de la transacción
     * del caso de uso para garantizar atomicidad.
     *
     * @param messageId    valor del header AMQP "messageId"
     * @param consumerGrupo nombre del consumer
     * @param tipoMensaje  tipo del evento recibido (ej: "PAGO_CONFIRMADO")
     */
    void registrarProcesado(String messageId, String consumerGrupo, String tipoMensaje);
}
