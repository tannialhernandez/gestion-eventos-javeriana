package com.javeriana.eventos.event.infrastructure.persistence;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.model.EstadoEvento;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import com.javeriana.eventos.event.infrastructure.persistence.entity.EventoEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adaptador de infraestructura: implementa EventoRepository usando Spring Data JPA.
 *
 * Esta clase implementa el port de salida definido en el dominio.
 * El dominio nunca la conoce directamente; Spring la inyecta como implementación
 * del interface EventoRepository.
 */
@Repository
public class JpaEventoRepository implements EventoRepository {

    private final SpringDataEventoRepository springDataRepo;
    private final EventoMapper mapper;

    public JpaEventoRepository(SpringDataEventoRepository springDataRepo, EventoMapper mapper) {
        this.springDataRepo = springDataRepo;
        this.mapper = mapper;
    }

    @Override
    public Evento guardar(Evento evento) {
        EventoEntity entity = mapper.toEntity(evento);
        EventoEntity saved = springDataRepo.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Evento> buscarPorId(UUID id) {
        return springDataRepo.findById(id)
            .map(mapper::toDomain);
    }

    @Override
    public List<Evento> buscarPorEstado(EstadoEvento estado) {
        return springDataRepo.findByEstado(estado).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Evento> buscarPublicados() {
        return buscarPorEstado(EstadoEvento.PUBLICADO);
    }

    @Override
    public void eliminar(UUID id) {
        springDataRepo.deleteById(id);
    }
}
