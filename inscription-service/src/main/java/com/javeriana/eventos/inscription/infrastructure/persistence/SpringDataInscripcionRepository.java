package com.javeriana.eventos.inscription.infrastructure.persistence;

import com.javeriana.eventos.inscription.domain.model.EstadoInscripcion;
import com.javeriana.eventos.inscription.infrastructure.persistence.entity.InscripcionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataInscripcionRepository extends JpaRepository<InscripcionEntity, UUID> {

    Optional<InscripcionEntity> findByIdempotencyKey(UUID idempotencyKey);

    Optional<InscripcionEntity> findByUsuarioIdAndEventoId(UUID usuarioId, UUID eventoId);

    List<InscripcionEntity> findByUsuarioId(UUID usuarioId);

    List<InscripcionEntity> findByEventoId(UUID eventoId);

    /**
     * Busca inscripciones que hayan superado el timeout de pago.
     * Ejecutada por el job scheduler cada minuto.
     */
    @Query("SELECT i FROM InscripcionEntity i " +
           "WHERE i.estado = :estado AND i.fechaExpiracionPago < :ahora")
    List<InscripcionEntity> findExpiradas(EstadoInscripcion estado, Instant ahora);
}
