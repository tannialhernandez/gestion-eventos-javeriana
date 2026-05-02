package com.javeriana.eventos.payment.domain.port.out;

import com.javeriana.eventos.payment.domain.model.Pago;

import java.util.Optional;
import java.util.UUID;

public interface PagoRepository {

    Pago guardar(Pago pago);

    Optional<Pago> buscarPorId(UUID id);

    Optional<Pago> buscarPorInscripcionId(UUID inscripcionId);

    /**
     * ADR-09: Idempotencia de webhooks.
     * Si ya existe un pago con esta referencia_externa → webhook duplicado.
     */
    Optional<Pago> buscarPorReferenciaExterna(String referenciaExterna);
}
