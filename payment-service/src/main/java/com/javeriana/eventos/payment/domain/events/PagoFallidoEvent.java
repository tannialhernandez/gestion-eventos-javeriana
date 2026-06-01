package com.javeriana.eventos.payment.domain.events;

import com.javeriana.eventos.shared.domain.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: el pago fue rechazado por la pasarela.
 *
 * Permite que inscription-service reaccione inmediatamente al rechazo
 * (en lugar de esperar al job de expiración ~15 minutos después).
 * Con este evento, inscription-service puede liberar el cupo y notificar
 * al usuario en segundos.
 */
public record PagoFallidoEvent(
    UUID eventId,
    UUID aggregateId,         // pagoId
    UUID inscripcionId,
    String motivoRechazo,     // código estructurado: "RECHAZADO_POR_PASARELA"
    String descripcionMotivo, // texto legible: "Pago rechazado por la pasarela"
    BigDecimal monto,
    String moneda,            // "COP", "USD"
    Instant occurredAt
) implements DomainEvent {

    public PagoFallidoEvent(UUID aggregateId, UUID inscripcionId,
                            String motivoRechazo, String descripcionMotivo,
                            BigDecimal monto, String moneda) {
        this(UUID.randomUUID(), aggregateId, inscripcionId,
             motivoRechazo, descripcionMotivo, monto, moneda, Instant.now());
    }

    @Override
    public String eventType() { return "PAGO_FALLIDO"; }
}
