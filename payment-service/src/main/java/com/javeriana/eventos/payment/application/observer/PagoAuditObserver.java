package com.javeriana.eventos.payment.application.observer;

import com.javeriana.eventos.payment.domain.audit.EventoAuditoria;
import com.javeriana.eventos.payment.domain.model.Pago;
import com.javeriana.eventos.payment.domain.port.out.AuditoriaPagoRepository;
import org.springframework.stereotype.Component;

/**
 * Observer que registra transiciones de estado de Pago en el audit log.
 * Implementa el patrón Observer (GoF) — ProcesarWebhookService y
 * CrearPreferenciaService lo invocan explícitamente dentro de la misma TX.
 *
 * La tabla pago_audit tiene trigger SQL que bloquea UPDATE/DELETE
 * (Ley 1581 Art. 11 — datos financieros irretroactivos).
 */
@Component
public class PagoAuditObserver {

    private final AuditoriaPagoRepository auditoriaPagoRepository;

    public PagoAuditObserver(AuditoriaPagoRepository auditoriaPagoRepository) {
        this.auditoriaPagoRepository = auditoriaPagoRepository;
    }

    public void registrarTransicion(Pago pago, String estadoAnterior, String actor, String motivo) {
        EventoAuditoria evento = EventoAuditoria.of(
            pago.getId(),
            pago.getInscripcionId(),
            estadoAnterior,
            pago.getEstado().name(),
            actor,
            motivo
        );
        auditoriaPagoRepository.registrar(evento);
    }
}
