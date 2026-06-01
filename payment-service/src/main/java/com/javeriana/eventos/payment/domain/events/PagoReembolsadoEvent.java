package com.javeriana.eventos.payment.domain.events;

import com.javeriana.eventos.shared.domain.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: el pago fue reembolsado.
 *
 * Incluye monto y moneda para que los consumidores puedan registrar
 * el reembolso sin necesidad de consultar payment-service.
 */
public record PagoReembolsadoEvent(
    UUID eventId,
    UUID aggregateId,
    UUID inscripcionId,
    BigDecimal monto,
    String moneda,
    Instant fechaReembolso,
    Instant occurredAt
) implements DomainEvent {

    public PagoReembolsadoEvent(UUID pagoId, UUID inscripcionId,
                                BigDecimal monto, String moneda,
                                Instant fechaReembolso) {
        this(UUID.randomUUID(), pagoId, inscripcionId,
             monto, moneda, fechaReembolso, Instant.now());
    }

    @Override
    public String eventType() { return "PAGO_REEMBOLSADO"; }
}
