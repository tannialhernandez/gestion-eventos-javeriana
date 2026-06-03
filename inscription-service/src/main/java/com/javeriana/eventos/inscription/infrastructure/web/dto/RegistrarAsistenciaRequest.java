package com.javeriana.eventos.inscription.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RegistrarAsistenciaRequest(
    @NotNull UUID inscripcionId,
    boolean asistio,
    String observaciones
) {}
