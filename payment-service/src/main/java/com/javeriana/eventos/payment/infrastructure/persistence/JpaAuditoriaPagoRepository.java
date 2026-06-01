package com.javeriana.eventos.payment.infrastructure.persistence;

import com.javeriana.eventos.payment.domain.audit.EventoAuditoria;
import com.javeriana.eventos.payment.domain.port.out.AuditoriaPagoRepository;
import com.javeriana.eventos.payment.infrastructure.persistence.entity.PagoAuditEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JpaAuditoriaPagoRepository implements AuditoriaPagoRepository {

    private final SpringDataAuditoriaPagoRepository springData;

    public JpaAuditoriaPagoRepository(SpringDataAuditoriaPagoRepository springData) {
        this.springData = springData;
    }

    @Override
    public void registrar(EventoAuditoria evento) {
        springData.save(PagoAuditEntity.from(evento));
    }

    @Override
    public List<EventoAuditoria> buscarPorPago(UUID pagoId) {
        return springData.findByPagoIdOrderByOcurridoEnAsc(pagoId)
            .stream()
            .map(PagoAuditEntity::toDomain)
            .toList();
    }
}
