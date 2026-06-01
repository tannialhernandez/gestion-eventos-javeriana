package com.javeriana.eventos.payment.domain.port.out;

import com.javeriana.eventos.payment.domain.audit.EventoAuditoria;

import java.util.List;
import java.util.UUID;

public interface AuditoriaPagoRepository {
    void registrar(EventoAuditoria evento);
    List<EventoAuditoria> buscarPorPago(UUID pagoId);
}
