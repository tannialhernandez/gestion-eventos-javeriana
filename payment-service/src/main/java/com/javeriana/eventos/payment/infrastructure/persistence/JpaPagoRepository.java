package com.javeriana.eventos.payment.infrastructure.persistence;

import com.javeriana.eventos.payment.domain.model.Pago;
import com.javeriana.eventos.payment.domain.port.out.PagoRepository;
import com.javeriana.eventos.payment.infrastructure.persistence.entity.PagoEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA que implementa PagoRepository (puerto de salida del dominio).
 *
 * Mapea entre el agregado Pago (dominio puro) y PagoEntity (JPA).
 * Sigue el mismo patrón que JpaInscripcionRepository en inscription-service.
 */
@Repository
public class JpaPagoRepository implements PagoRepository {

    private final SpringDataPagoRepository springDataRepo;

    public JpaPagoRepository(SpringDataPagoRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public Pago guardar(Pago pago) {
        return toDomain(springDataRepo.save(toEntity(pago)));
    }

    @Override
    public Optional<Pago> buscarPorId(UUID id) {
        return springDataRepo.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Pago> buscarPorInscripcionId(UUID inscripcionId) {
        return springDataRepo.findFirstByInscripcionIdOrderByFechaCreacionDesc(inscripcionId).map(this::toDomain);
    }

    @Override
    public Optional<Pago> buscarPorReferenciaExterna(String referenciaExterna) {
        return springDataRepo.findByReferenciaExterna(referenciaExterna).map(this::toDomain);
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    private Pago toDomain(PagoEntity e) {
        return new Pago(
            e.getId(), e.getInscripcionId(), e.getMonto(), e.getMoneda(),
            e.getPasarela(), e.getReferenciaExterna(), e.getPreferenciaExternaId(),
            e.getEstado(), e.getFechaCreacion(), e.getFechaConfirmacion(),
            e.getFechaReembolso(), e.getIntentosCobro(), e.getMetadatosPasarela(),
            e.getVersion()
        );
    }

    private PagoEntity toEntity(Pago d) {
        PagoEntity e = new PagoEntity();
        e.setId(d.getId());
        e.setInscripcionId(d.getInscripcionId());
        e.setMonto(d.getMonto());
        e.setMoneda(d.getMoneda());
        e.setPasarela(d.getPasarela());
        e.setReferenciaExterna(d.getReferenciaExterna());
        e.setPreferenciaExternaId(d.getPreferenciaExternaId());
        e.setEstado(d.getEstado());
        e.setFechaCreacion(d.getFechaCreacion());
        e.setFechaConfirmacion(d.getFechaConfirmacion());
        e.setFechaReembolso(d.getFechaReembolso());
        e.setIntentosCobro(d.getIntentosCobro());
        e.setMetadatosPasarela(d.getMetadatosPasarela());
        e.setVersion(d.getVersion());
        return e;
    }
}
