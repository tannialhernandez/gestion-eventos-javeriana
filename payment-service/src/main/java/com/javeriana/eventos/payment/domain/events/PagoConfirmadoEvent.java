package com.javeriana.eventos.payment.domain.events;

import com.javeriana.eventos.shared.domain.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: el pago fue confirmado por la pasarela.
 *
 * Contiene monto y moneda para que inscription-service NO necesite
 * consultar de vuelta a payment-service para conocer el monto confirmado
 * — principio de independencia entre microservicios.
 */
public record PagoConfirmadoEvent(
    UUID eventId,
    UUID aggregateId,         // pagoId
    UUID inscripcionId,
    String referenciaExterna,
    BigDecimal monto,
    String moneda,            // "COP", "USD"
    Instant fechaConfirmacion,
    Instant occurredAt
) implements DomainEvent {

    public PagoConfirmadoEvent(UUID pagoId, UUID inscripcionId,
                               String referenciaExterna,
                               BigDecimal monto, String moneda,
                               Instant fechaConfirmacion) {
        this(UUID.randomUUID(), pagoId, inscripcionId, referenciaExterna,
             monto, moneda, fechaConfirmacion, Instant.now());
    }

    @Override
    public String eventType() { return "PAGO_CONFIRMADO"; }
}
