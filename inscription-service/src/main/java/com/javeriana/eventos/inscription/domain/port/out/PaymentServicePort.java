package com.javeriana.eventos.inscription.domain.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Port de salida hacia payment-service.
 *
 * Inscription-service delega la creación de la preferencia de pago
 * al payment-service, que es dueño de la integración con MercadoPago.
 */
public interface PaymentServicePort {

    PreferenciaPago crearPreferencia(UUID inscripcionId, BigDecimal monto,
                                     String moneda, UUID usuarioId);

    record PreferenciaPago(
        UUID pagoId,
        String checkoutUrl,
        String preferenciaExternaId
    ) {}
}
