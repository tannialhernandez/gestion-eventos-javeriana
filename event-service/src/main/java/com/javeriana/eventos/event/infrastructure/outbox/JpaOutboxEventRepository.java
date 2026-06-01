package com.javeriana.eventos.event.infrastructure.outbox;

import com.javeriana.eventos.event.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.shared.domain.outbox.EstadoOutbox;
import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JpaOutboxEventRepository implements OutboxEventRepository {

    private final SpringDataOutboxRepository springDataRepo;

    public JpaOutboxEventRepository(SpringDataOutboxRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public OutboxEvent guardar(OutboxEvent evento) {
        springDataRepo.save(OutboxEventMapper.toEntity(evento));
        return evento;
    }

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
