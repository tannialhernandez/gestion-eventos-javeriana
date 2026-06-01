package com.javeriana.eventos.event.domain.port.out;

import com.javeriana.eventos.event.domain.model.Tarifa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida — Repository pattern para TARIFA.
 */
public interface TarifaRepository {

    Optional<Tarifa> buscarPorId(UUID id);

    List<Tarifa> buscarPorEventoId(UUID eventoId);

    Tarifa guardar(Tarifa tarifa);
}
