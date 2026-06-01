package com.javeriana.eventos.event.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: liberar un cupo del evento.
 *
 * Llamado por inscription-service cuando una inscripción expira o se cancela.
 * Endpoint: POST /api/v1/eventos/{eventoId}/cupos/liberar (C-02 Prompt 16)
 */
public interface LiberarCupoUseCase {
    void liberar(UUID eventoId);
}
