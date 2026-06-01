package com.javeriana.eventos.payment.infrastructure.outbox;

import com.javeriana.eventos.shared.domain.outbox.EstadoOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringDataOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Obtiene el siguiente lote de eventos PENDIENTES con bloqueo pesimista de fila (ADR-012).
     *
     *  WHERE estado = 'PENDIENTE'   → solo eventos sin publicar
     *  ORDER BY creado_en ASC       → FIFO: los más antiguos primero
     *  LIMIT :limite                → batch acotado; evita leer toda la tabla (riesgo OOM)
     *  FOR UPDATE                   → bloqueo pesimista a nivel de fila
     *  SKIP LOCKED                  → dos pods en paralelo NO procesan el mismo evento
     */
    @Query(value = """
        SELECT * FROM outbox_events
        WHERE estado = 'PENDIENTE'
        ORDER BY creado_en ASC
        LIMIT :limite
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEventEntity> findPendingForUpdate(@Param("limite") int limite);

    /** Para OutboxMetrics.contarPendientes() — Gauge de Micrometer. */
    long countByEstado(EstadoOutbox estado);
}
