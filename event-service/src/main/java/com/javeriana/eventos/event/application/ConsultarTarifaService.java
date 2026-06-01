package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.model.Tarifa;
import com.javeriana.eventos.event.domain.port.in.ConsultarTarifaUseCase;
import com.javeriana.eventos.event.domain.port.out.TarifaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta tarifas de eventos.
 *
 * C-01 (Prompt 16): expone el puerto que permite a inscription-service
 * obtener el precio real de una tarifa antes de crear la preferencia de pago.
 */
@Service
@Transactional(readOnly = true)
public class ConsultarTarifaService implements ConsultarTarifaUseCase {

    private final TarifaRepository tarifaRepository;

    public ConsultarTarifaService(TarifaRepository tarifaRepository) {
        this.tarifaRepository = tarifaRepository;
    }

    @Override
    public Optional<Tarifa> buscarTarifa(UUID tarifaId) {
        return tarifaRepository.buscarPorId(tarifaId);
    }

    @Override
    public List<Tarifa> listarTarifasDeEvento(UUID eventoId) {
        return tarifaRepository.buscarPorEventoId(eventoId);
    }
}
