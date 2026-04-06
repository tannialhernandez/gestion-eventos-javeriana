package com.javeriana.eventos.inscription.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CrearInscripcionRequest(
    @NotNull UUID eventoId,
    @NotNull UUID tarifaId,
    @NotNull UUID idempotencyKey   // Generado por el cliente para prevenir duplicados
) {}
