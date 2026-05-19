package com.javeriana.eventos.payment.infrastructure.web.dto;

import java.util.UUID;

public record PreferenciaResponse(
    UUID pagoId,
    String checkoutUrl,
    String preferenciaId
) {}
