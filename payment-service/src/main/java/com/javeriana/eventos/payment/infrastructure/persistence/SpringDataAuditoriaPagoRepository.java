package com.javeriana.eventos.payment.infrastructure.persistence;

import com.javeriana.eventos.payment.infrastructure.persistence.entity.PagoAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataAuditoriaPagoRepository
        extends JpaRepository<PagoAuditEntity, UUID> {

    List<PagoAuditEntity> findByPagoIdOrderByOcurridoEnAsc(UUID pagoId);
}
