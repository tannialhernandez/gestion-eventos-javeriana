package com.javeriana.eventos.payment.domain.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Value Object inmutable que representa una entrada en el audit log.
 * Ley 1581 (Colombia) Art. 11 — los datos de pagos son irretroactivos.
 */
public record EventoAuditoria(
    UUID pagoId,
    UUID inscripcionId,
    String estadoAnterior,
    String estadoNuevo,
    String actor,
    String motivo,
    String metadatos,
    Instant ocurridoEn,
    String ipOrigen
) {
    public static EventoAuditoria of(UUID pagoId, UUID inscripcionId,
                                     String estadoAnterior, String estadoNuevo,
                                     String actor, String motivo) {
        return new EventoAuditoria(pagoId, inscripcionId,
            estadoAnterior, estadoNuevo, actor, motivo,
            null, Instant.now(), null);
    }

    public static EventoAuditoria of(UUID pagoId, UUID inscripcionId,
                                     String estadoAnterior, String estadoNuevo,
                                     String actor, String motivo,
                                     String metadatos, String ipOrigen) {
        return new EventoAuditoria(pagoId, inscripcionId,
            estadoAnterior, estadoNuevo, actor, motivo,
            metadatos, Instant.now(), ipOrigen);
    }
}
