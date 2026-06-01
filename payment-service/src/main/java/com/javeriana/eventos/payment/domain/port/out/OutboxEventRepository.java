package com.javeriana.eventos.payment.domain.port.out;

import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de salida para el Outbox Pattern (ADR-011).
 *
 * Interface pura: sin anotaciones Spring ni dependencias JPA.
 * Los adaptadores en infrastructure implementan los detalles de persistencia.
 */
public interface OutboxEventRepository {

    /** Persiste un nuevo evento en outbox y devuelve el evento guardado. */
    OutboxEvent guardar(OutboxEvent evento);

    /** Lee hasta `limite` eventos en estado PENDIENTE, ordenados por creadoEn ASC. */
    List<OutboxEvent> buscarNoPublicados(int limite);

    /** Marca el evento como ENVIADO y registra enviadoEn = ahora. */
    void marcarProcesado(UUID id);

    /** Incrementa el contador de intentos fallidos. */
    void incrementarIntentos(UUID id);

    /** Marca el evento como FALLIDO (agotados los reintentos). */
    void marcarFallido(UUID id);

    /** Cuenta eventos en estado PENDIENTE. Usado por el Gauge de Micrometer. */
    long contarPendientes();
}
