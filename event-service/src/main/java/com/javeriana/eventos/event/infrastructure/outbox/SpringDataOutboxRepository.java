package com.javeriana.eventos.event.infrastructure.outbox;

import com.javeriana.eventos.shared.domain.outbox.EstadoOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringDataOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(value = """
        SELECT * FROM outbox_events
        WHERE estado = 'PENDIENTE'
        ORDER BY creado_en ASC
        LIMIT :limite
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEventEntity> findPendingForUpdate(@Param("limite") int limite);

    long countByEstado(EstadoOutbox estado);
}
