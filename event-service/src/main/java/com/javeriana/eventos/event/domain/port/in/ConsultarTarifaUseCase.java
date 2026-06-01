package com.javeriana.eventos.event.domain.port.in;

import com.javeriana.eventos.event.domain.model.Tarifa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de entrada: consultar tarifas de un evento.
 *
 * Endpoint consumido por inscription-service:
 *   GET /api/v1/tarifas/{tarifaId}  (C-01 Prompt 16)
 */
public interface ConsultarTarifaUseCase {

    Optional<Tarifa> buscarTarifa(UUID tarifaId);

    List<Tarifa> listarTarifasDeEvento(UUID eventoId);
}
