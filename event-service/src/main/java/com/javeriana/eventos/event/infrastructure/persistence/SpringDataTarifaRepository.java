package com.javeriana.eventos.event.infrastructure.persistence;

import com.javeriana.eventos.event.infrastructure.persistence.entity.TarifaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataTarifaRepository extends JpaRepository<TarifaEntity, UUID> {

    List<TarifaEntity> findByEventoIdAndActivaTrue(UUID eventoId);
}
