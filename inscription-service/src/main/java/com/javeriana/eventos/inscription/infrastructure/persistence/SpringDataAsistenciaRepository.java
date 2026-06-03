package com.javeriana.eventos.inscription.infrastructure.persistence;

import com.javeriana.eventos.inscription.infrastructure.persistence.entity.AsistenciaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataAsistenciaRepository extends JpaRepository<AsistenciaEntity, UUID> {

    Optional<AsistenciaEntity> findByInscripcionId(UUID inscripcionId);

    List<AsistenciaEntity> findByInscripcionIdIn(Collection<UUID> inscripcionIds);
}
