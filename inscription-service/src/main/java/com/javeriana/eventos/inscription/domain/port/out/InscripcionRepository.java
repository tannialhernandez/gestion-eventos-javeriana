package com.javeriana.eventos.inscription.domain.port.out;

import com.javeriana.eventos.inscription.domain.model.Inscripcion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InscripcionRepository {

    Inscripcion guardar(Inscripcion inscripcion);

    Optional<Inscripcion> buscarPorId(UUID id);

    Optional<Inscripcion> buscarPorIdempotencyKey(UUID idempotencyKey);

    Optional<Inscripcion> buscarPorUsuarioIdYEventoId(UUID usuarioId, UUID eventoId);

    List<Inscripcion> buscarPorUsuarioId(UUID usuarioId);

    /**
     * RN-01, ADR-07: Bloqueo pesimista (SELECT FOR UPDATE) sobre el evento
     * para reservar el cupo de forma atómica.
     *
     * Este método realiza en una única transacción:
     *  1. SELECT * FROM evento WHERE id = eventoId FOR UPDATE
     *  2. Valida cupo_disponible > 0
     *  3. UPDATE evento SET cupo_disponible = cupo_disponible - 1
     *  4. INSERT INTO inscripcion
     *
     * Lanza SinCuposDisponiblesException si cupo_disponible == 0.
     */
    Inscripcion guardarConReservaDeCupo(Inscripcion inscripcion, int cupoDisponibleInicial);

    void reservarCupo(UUID eventoId, int cupoDisponibleInicial);

    void liberarCupo(UUID eventoId);

    /**
     * Retorna inscripciones PENDIENTE_PAGO cuya fecha de expiración ya pasó.
     * Usada por el job scheduler cada minuto.
     */
    List<Inscripcion> buscarExpiradas();

    List<Inscripcion> buscarPorEventoId(UUID eventoId);
}
