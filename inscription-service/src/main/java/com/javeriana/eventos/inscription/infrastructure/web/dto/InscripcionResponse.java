package com.javeriana.eventos.inscription.infrastructure.web.dto;

import java.util.UUID;

public record InscripcionResponse(
    UUID inscripcionId,
    UUID eventoId,
    UUID tarifaId,
    String estado,
    String fechaInscripcion,
    String fechaExpiracionPago,
    String checkoutUrl,
    long expiraEnSegundos
) {}
