package com.javeriana.eventos.payment.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface SpringDataOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.published = false ORDER BY o.createdAt ASC")
    List<OutboxEventEntity> findPendingEvents();
}
