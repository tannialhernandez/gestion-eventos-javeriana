package com.javeriana.eventos.inscription.infrastructure.persistence;

import com.javeriana.eventos.inscription.domain.model.Asistencia;
import com.javeriana.eventos.inscription.domain.port.out.AsistenciaRepository;
import com.javeriana.eventos.inscription.infrastructure.persistence.entity.AsistenciaEntity;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAsistenciaRepository implements AsistenciaRepository {

    private final SpringDataAsistenciaRepository repository;

    public JpaAsistenciaRepository(SpringDataAsistenciaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Asistencia guardar(Asistencia asistencia) {
        return toDomain(repository.save(toEntity(asistencia)));
    }

    @Override
    public Optional<Asistencia> buscarPorInscripcionId(UUID inscripcionId) {
        return repository.findByInscripcionId(inscripcionId).map(this::toDomain);
    }

    @Override
    public List<Asistencia> buscarPorInscripcionIds(Collection<UUID> inscripcionIds) {
        if (inscripcionIds == null || inscripcionIds.isEmpty()) return List.of();
        return repository.findByInscripcionIdIn(inscripcionIds)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private Asistencia toDomain(AsistenciaEntity entity) {
        return new Asistencia(
            entity.getId(),
            entity.getInscripcionId(),
            entity.isAsistio(),
            entity.getFechaRegistro(),
            entity.getRegistradoPor(),
            entity.getObservaciones()
        );
    }

    private AsistenciaEntity toEntity(Asistencia asistencia) {
        AsistenciaEntity entity = new AsistenciaEntity();
        entity.setId(asistencia.getId());
        entity.setInscripcionId(asistencia.getInscripcionId());
        entity.setAsistio(asistencia.isAsistio());
        entity.setFechaRegistro(asistencia.getFechaRegistro());
        entity.setRegistradoPor(asistencia.getRegistradoPor());
        entity.setObservaciones(asistencia.getObservaciones());
        return entity;
    }
}
