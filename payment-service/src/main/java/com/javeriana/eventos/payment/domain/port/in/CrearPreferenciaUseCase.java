package com.javeriana.eventos.payment.domain.port.in;

import java.math.BigDecimal;
import java.util.UUID;

public interface CrearPreferenciaUseCase {

    Result crear(Command command);

    record Command(
        UUID inscripcionId,
        BigDecimal monto,
        String moneda,
        UUID usuarioId
    ) {}

    record Result(
        UUID pagoId,
        String checkoutUrl,
        String preferenciaId
    ) {}
}
