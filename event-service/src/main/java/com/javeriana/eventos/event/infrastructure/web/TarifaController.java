package com.javeriana.eventos.event.infrastructure.web;

import com.javeriana.eventos.event.domain.port.in.ConsultarTarifaUseCase;
import com.javeriana.eventos.event.infrastructure.web.dto.TarifaResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de tarifas.
 *
 * C-01 (Prompt 16): inscription-service llama a
 *   GET /api/v1/tarifas/{tarifaId}
 * para obtener el precio real antes de crear la preferencia de pago.
 */
@RestController
@RequestMapping("/api/v1/tarifas")
public class TarifaController {

    private final ConsultarTarifaUseCase consultarTarifa;

    public TarifaController(ConsultarTarifaUseCase consultarTarifa) {
        this.consultarTarifa = consultarTarifa;
    }

    /**
     * GET /api/v1/tarifas/{tarifaId}
     * Usado por inscription-service.EventoServiceFeignClient.obtenerTarifa()
     */
    @GetMapping("/{tarifaId}")
    public ResponseEntity<TarifaResponse> obtenerTarifa(@PathVariable UUID tarifaId) {
        return consultarTarifa.buscarTarifa(tarifaId)
            .map(t -> ResponseEntity.ok(TarifaResponse.from(t)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/tarifas?eventoId={eventoId}
     * Útil para listar las tarifas activas de un evento (uso futuro por UI).
     */
    @GetMapping
    public List<TarifaResponse> listarPorEvento(@RequestParam UUID eventoId) {
        return consultarTarifa.listarTarifasDeEvento(eventoId)
            .stream().map(TarifaResponse::from).toList();
    }
}
