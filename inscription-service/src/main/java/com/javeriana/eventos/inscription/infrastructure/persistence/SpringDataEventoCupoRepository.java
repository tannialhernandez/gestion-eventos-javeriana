package com.javeriana.eventos.inscription.infrastructure.persistence;

import com.javeriana.eventos.inscription.infrastructure.persistence.entity.EventoCupoEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface SpringDataEventoCupoRepository extends JpaRepository<EventoCupoEntity, UUID> {

    /**
     * ADR-07: SELECT FOR UPDATE — Bloqueo pesimista.
     *
     * Esta query bloquea la fila del evento hasta que la transacción haga
     * COMMIT o ROLLBACK. Garantiza que dos hilos concurrentes que intenten
     * inscribirse al mismo evento no puedan ambos decrementar el mismo cupo.
     *
     * El segundo hilo queda bloqueado hasta que el primero libere el lock.
     * Cuando lo obtiene, lee cupo_disponible ya actualizado y decide si
     * hay cupo o retorna SinCuposDisponiblesException.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EventoCupoEntity e WHERE e.eventoId = :eventoId")
    Optional<EventoCupoEntity> findByEventoIdWithLock(UUID eventoId);
}
