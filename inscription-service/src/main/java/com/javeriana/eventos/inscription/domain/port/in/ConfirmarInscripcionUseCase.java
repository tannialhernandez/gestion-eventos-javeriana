package com.javeriana.eventos.inscription.domain.port.in;

import java.util.UUID;

/**
 * Invocado por el WebhookController cuando payment-service notifica
 * que un pago fue confirmado o reembolsado.
 */
public interface ConfirmarInscripcionUseCase {

    void confirmar(UUID inscripcionId, String referenciaExterna);

    void manejarPagoTardio(UUID inscripcionId, String referenciaExterna);
}
