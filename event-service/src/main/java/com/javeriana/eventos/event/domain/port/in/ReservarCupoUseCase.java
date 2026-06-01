package com.javeriana.eventos.event.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: reservar un cupo del evento.
 *
 * Expuesto para futuras integraciones. Actualmente inscription-service
 * gestiona la reserva localmente con SELECT FOR UPDATE sobre evento_cupo.
 */
public interface ReservarCupoUseCase {
    void reservar(UUID eventoId);
}
