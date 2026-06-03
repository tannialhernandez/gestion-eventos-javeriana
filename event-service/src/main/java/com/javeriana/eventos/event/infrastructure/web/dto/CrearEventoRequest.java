package com.javeriana.eventos.event.infrastructure.web.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CrearEventoRequest(
    @NotBlank @Size(max = 300)
    String titulo,

    @NotBlank
    String descripcion,

    @NotBlank
    String tipo,

    @NotBlank
    String modalidad,

    @NotNull
    LocalDate fechaInicio,

    @NotNull
    LocalDate fechaFin,

    @NotNull
    LocalDateTime fechaLimiteInscripcion,

    @Positive
    int cupoMaximo,

    String estado
) {}
