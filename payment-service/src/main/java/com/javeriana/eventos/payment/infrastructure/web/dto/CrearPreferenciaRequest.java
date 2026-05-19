package com.javeriana.eventos.payment.infrastructure.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CrearPreferenciaRequest(
    @NotNull UUID inscripcionId,
    @NotNull @DecimalMin("1.00") BigDecimal monto,
    @NotBlank String moneda
) {}
