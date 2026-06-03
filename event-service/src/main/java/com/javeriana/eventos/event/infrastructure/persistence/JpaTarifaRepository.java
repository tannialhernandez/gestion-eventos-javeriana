package com.javeriana.eventos.event.infrastructure.persistence;

import com.javeriana.eventos.event.domain.model.Tarifa;
import com.javeriana.eventos.event.domain.port.out.TarifaRepository;
import com.javeriana.eventos.event.infrastructure.persistence.entity.TarifaEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adaptador JPA para TarifaRepository.
 */
@Repository
public class JpaTarifaRepository implements TarifaRepository {

    private final SpringDataTarifaRepository springDataRepo;

    public JpaTarifaRepository(SpringDataTarifaRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public Optional<Tarifa> buscarPorId(UUID id) {
        return springDataRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<Tarifa> buscarPorEventoId(UUID eventoId) {
        return springDataRepo.findByEventoIdAndActivaTrue(eventoId)
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Tarifa guardar(Tarifa tarifa) {
        TarifaEntity saved = springDataRepo.save(toEntity(tarifa));
        return toDomain(saved);
    }

    private Tarifa toDomain(TarifaEntity e) {
        return new Tarifa(
            e.getId(), e.getEventoId(), e.getNombre(), e.getPrecio(),
            e.getMoneda(), e.getAplicaA(), e.getFechaInicioVigencia(),
            e.getFechaFinVigencia(), e.isActiva()
        );
    }

    private TarifaEntity toEntity(Tarifa t) {
        TarifaEntity e = new TarifaEntity();
        e.setId(t.getId());
        e.setEventoId(t.getEventoId());
        e.setNombre(t.getNombre());
        e.setPrecio(t.getPrecio());
        e.setMoneda(t.getMoneda());
        e.setAplicaA(t.getAplicaA());
        e.setFechaInicioVigencia(t.getFechaInicioVigencia());
        e.setFechaFinVigencia(t.getFechaFinVigencia());
        e.setActiva(t.isActiva());
        return e;
    }
}
