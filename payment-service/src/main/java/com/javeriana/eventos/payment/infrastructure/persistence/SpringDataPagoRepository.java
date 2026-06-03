package com.javeriana.eventos.payment.infrastructure.persistence;

import com.javeriana.eventos.payment.infrastructure.persistence.entity.PagoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataPagoRepository extends JpaRepository<PagoEntity, UUID> {

    Optional<PagoEntity> findFirstByInscripcionIdOrderByFechaCreacionDesc(UUID inscripcionId);

    /**
     * Idempotencia de webhooks (RN-03, ADR-09):
     * Si ya existe un pago con esta referencia_externa → webhook duplicado.
     */
    Optional<PagoEntity> findByReferenciaExterna(String referenciaExterna);
}
