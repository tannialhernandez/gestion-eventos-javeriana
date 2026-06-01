package com.javeriana.eventos.inscription.infrastructure.outbox;

import com.javeriana.eventos.inscription.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.shared.domain.outbox.EstadoOutbox;
import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador JPA para el puerto OutboxEventRepository (inscription-service).
 *
 * buscarNoPublicados usa SELECT FOR UPDATE SKIP LOCKED para garantizar que
 * instancias paralelas del relay no procesen el mismo evento (ADR-012).
 */
@Repository
public class JpaOutboxEventRepository implements OutboxEventRepository {

    private final SpringDataOutboxRepository springDataRepo;

    public JpaOutboxEventRepository(SpringDataOutboxRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public OutboxEvent guardar(OutboxEvent evento) {
        OutboxEventEntity entity = OutboxEventMapper.toEntity(evento);
        springDataRepo.save(entity);
        return evento;
    }

    /**
     * REQUIRED: el relay siempre llama desde una TX activa, heredando el lock.
     * REQUIRED permite también que los tests futuros llamen sin TX propia.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public List<OutboxEvent> buscarNoPublicados(int limite) {
        return springDataRepo.findPendingForUpdate(limite)
            .stream()
            .map(OutboxEventMapper::toDomain)
            .toList();
    }

    @Override
    public void marcarProcesado(UUID id) {
        springDataRepo.findById(id).ifPresent(entity -> {
            entity.setEstado(EstadoOutbox.ENVIADO);
            entity.setEnviadoEn(Instant.now());
            springDataRepo.save(entity);
        });
    }

    @Override
    public void incrementarIntentos(UUID id) {
        springDataRepo.findById(id).ifPresent(entity -> {
            entity.setIntentos(entity.getIntentos() + 1);
            springDataRepo.save(entity);
        });
    }

    @Override
    public void marcarFallido(UUID id) {
        springDataRepo.findById(id).ifPresent(entity -> {
            entity.setEstado(EstadoOutbox.FALLIDO);
            springDataRepo.save(entity);
        });
    }

    @Override
    public long contarPendientes() {
        return springDataRepo.countByEstado(EstadoOutbox.PENDIENTE);
    }
}
