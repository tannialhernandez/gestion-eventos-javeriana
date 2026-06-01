package com.javeriana.eventos.event.domain.port.out;

import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de salida para el Outbox Pattern (ADR-011).
 * Replica exacta del puerto de inscription-service.
 */
public interface OutboxEventRepository {

    OutboxEvent guardar(OutboxEvent evento);

    /** Lee hasta {@code limite} eventos PENDIENTES con SELECT FOR UPDATE SKIP LOCKED. */
    List<OutboxEvent> buscarNoPublicados(int limite);

    void marcarProcesado(UUID id);

    void incrementarIntentos(UUID id);

    void marcarFallido(UUID id);

    long contarPendientes();
}
