package com.javeriana.eventos.event.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record GuardarTarifaRequest(
    UUID eventoId,

    @NotBlank
    String descripcion,

    @NotNull
    @PositiveOrZero
    BigDecimal monto,

    @NotBlank
    String moneda
) {}
