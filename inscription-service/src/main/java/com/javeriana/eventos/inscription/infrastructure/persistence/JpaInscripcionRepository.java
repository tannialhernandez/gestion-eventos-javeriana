package com.javeriana.eventos.inscription.infrastructure.persistence;

import com.javeriana.eventos.inscription.domain.model.EstadoInscripcion;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.out.InscripcionRepository;
import com.javeriana.eventos.inscription.infrastructure.persistence.entity.EventoCupoEntity;
import com.javeriana.eventos.inscription.infrastructure.persistence.entity.InscripcionEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adaptador JPA que implementa InscripcionRepository.
 *
 * El método guardarConReservaDeCupo es el corazón del patrón de bloqueo pesimista.
 * Debe ejecutarse dentro de una transacción activa (la del servicio de aplicación).
 */
@Repository
public class JpaInscripcionRepository implements InscripcionRepository {

    private final SpringDataInscripcionRepository inscripcionRepo;
    private final SpringDataEventoCupoRepository eventoCupoRepo;

    public JpaInscripcionRepository(SpringDataInscripcionRepository inscripcionRepo,
                                    SpringDataEventoCupoRepository eventoCupoRepo) {
        this.inscripcionRepo = inscripcionRepo;
        this.eventoCupoRepo = eventoCupoRepo;
    }

    @Override
    public Inscripcion guardar(Inscripcion inscripcion) {
        InscripcionEntity entity = toEntity(inscripcion);
        return toDomain(inscripcionRepo.save(entity));
    }

    @Override
    public Optional<Inscripcion> buscarPorId(UUID id) {
        return inscripcionRepo.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Inscripcion> buscarPorIdempotencyKey(UUID idempotencyKey) {
        return inscripcionRepo.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public Optional<Inscripcion> buscarPorUsuarioIdYEventoId(UUID usuarioId, UUID eventoId) {
        return inscripcionRepo.findByUsuarioIdAndEventoId(usuarioId, eventoId).map(this::toDomain);
    }

    @Override
    public List<Inscripcion> buscarPorUsuarioId(UUID usuarioId) {
        return inscripcionRepo.findByUsuarioId(usuarioId)
            .stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * ADR-07: Bloqueo pesimista para reserva de cupo.
     *
     * Flujo dentro de la transacción del servicio de aplicación:
     *
     * 1. findByEventoIdWithLock() → SELECT * FROM evento_cupo WHERE evento_id = ? FOR UPDATE
     *    - El hilo A obtiene el lock y continúa
     *    - El hilo B queda BLOQUEADO aquí hasta que A haga COMMIT
     *
     * 2. Verifica cupo_disponible > 0
     *    - Si es 0 → lanza SinCuposDisponiblesException → rollback automático
     *
     * 3. Decrementa cupo_disponible (UPDATE)
     *
     * 4. Inserta la inscripción (INSERT)
     *
     * 5. Al COMMIT del método padre (@Transactional) → lock liberado
     *    - El hilo B se desbloquea, lee cupo = 0, lanza excepción
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Inscripcion guardarConReservaDeCupo(Inscripcion inscripcion, int cupoDisponibleInicial) {
        reservarCupo(inscripcion.getEventoId(), cupoDisponibleInicial);

        // Paso 4: Insertar inscripción
        InscripcionEntity entity = toEntity(inscripcion);
        return toDomain(inscripcionRepo.save(entity));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reservarCupo(UUID eventoId, int cupoDisponibleInicial) {
        EventoCupoEntity cupo = obtenerCupoConLock(eventoId, cupoDisponibleInicial);

        if (cupo.getCupoDisponible() <= 0) {
            throw new SinCuposDisponiblesException(eventoId);
        }

        cupo.setCupoDisponible(cupo.getCupoDisponible() - 1);
        eventoCupoRepo.save(cupo);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void liberarCupo(UUID eventoId) {
        eventoCupoRepo.findByEventoIdWithLock(eventoId).ifPresent((cupo) -> {
            if (cupo.getCupoDisponible() < cupo.getCupoMaximo()) {
                cupo.setCupoDisponible(cupo.getCupoDisponible() + 1);
                eventoCupoRepo.save(cupo);
            }
        });
    }

    private EventoCupoEntity obtenerCupoConLock(UUID eventoId, int cupoDisponibleInicial) {
        int cupoDisponible = Math.max(cupoDisponibleInicial, 0);
        eventoCupoRepo.insertIfAbsent(eventoId, cupoDisponible);
        return eventoCupoRepo.findByEventoIdWithLock(eventoId)
            .orElseThrow(() -> new IllegalStateException(
                "No fue posible inicializar cupos para evento " + eventoId));
    }

    @Override
    public List<Inscripcion> buscarExpiradas() {
        return inscripcionRepo
            .findExpiradas(EstadoInscripcion.PENDIENTE_PAGO, Instant.now())
            .stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Inscripcion> buscarPorEventoId(UUID eventoId) {
        return inscripcionRepo.findByEventoId(eventoId)
            .stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    private Inscripcion toDomain(InscripcionEntity e) {
        return new Inscripcion(
            e.getId(), e.getUsuarioId(), e.getEventoId(), e.getTarifaId(),
            e.getEstado(), e.getFechaInscripcion(), e.getFechaExpiracionPago(),
            e.getCodigoQr(), e.getIdempotencyKey(), e.getVersion()
        );
    }

    private InscripcionEntity toEntity(Inscripcion d) {
        InscripcionEntity e = new InscripcionEntity();
        e.setId(d.getId());
        e.setUsuarioId(d.getUsuarioId());
        e.setEventoId(d.getEventoId());
        e.setTarifaId(d.getTarifaId());
        e.setEstado(d.getEstado());
        e.setFechaInscripcion(d.getFechaInscripcion());
        e.setFechaExpiracionPago(d.getFechaExpiracionPago());
        e.setCodigoQr(d.getCodigoQr());
        e.setIdempotencyKey(d.getIdempotencyKey());
        e.setVersion(d.getVersion());
        return e;
    }
}
