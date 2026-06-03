package com.javeriana.eventos.event.infrastructure.web;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.model.Tarifa;
import com.javeriana.eventos.event.domain.port.in.ConsultarTarifaUseCase;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import com.javeriana.eventos.event.domain.port.out.TarifaRepository;
import com.javeriana.eventos.event.infrastructure.security.JwtPrincipal;
import com.javeriana.eventos.event.infrastructure.web.dto.GuardarTarifaRequest;
import com.javeriana.eventos.event.infrastructure.web.dto.TarifaResponse;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
    private final TarifaRepository tarifaRepository;
    private final EventoRepository eventoRepository;

    public TarifaController(ConsultarTarifaUseCase consultarTarifa,
                            TarifaRepository tarifaRepository,
                            EventoRepository eventoRepository) {
        this.consultarTarifa = consultarTarifa;
        this.tarifaRepository = tarifaRepository;
        this.eventoRepository = eventoRepository;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TarifaResponse crear(@Valid @RequestBody GuardarTarifaRequest request,
                                @AuthenticationPrincipal JwtPrincipal principal) {
        JwtPrincipal solicitante = requirePrincipal(principal);
        UUID eventoId = request.eventoId();
        if (eventoId == null) {
            throw new IllegalArgumentException("eventoId es obligatorio");
        }
        Evento evento = buscarEventoAutorizado(eventoId, solicitante);
        Tarifa tarifa = new Tarifa(
            UUID.randomUUID(),
            evento.getId(),
            request.descripcion(),
            request.monto(),
            request.moneda(),
            Tarifa.AplicaA.ESTUDIANTE_JAVERIANA,
            LocalDate.now().minusDays(1),
            evento.getFechaFin().plusDays(1),
            true
        );
        return TarifaResponse.from(tarifaRepository.guardar(tarifa));
    }

    @PutMapping("/{tarifaId}")
    public TarifaResponse actualizar(@PathVariable UUID tarifaId,
                                     @Valid @RequestBody GuardarTarifaRequest request,
                                     @AuthenticationPrincipal JwtPrincipal principal) {
        JwtPrincipal solicitante = requirePrincipal(principal);
        Tarifa tarifa = tarifaRepository.buscarPorId(tarifaId)
            .orElseThrow(() -> new IllegalArgumentException("Tarifa no encontrada: " + tarifaId));
        buscarEventoAutorizado(tarifa.getEventoId(), solicitante);
        tarifa.actualizar(request.descripcion(), request.monto(), request.moneda());
        return TarifaResponse.from(tarifaRepository.guardar(tarifa));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "parametro_invalido", "message", ex.getMessage()));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<Map<String, String>> handleBusinessRule(BusinessRuleViolationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(Map.of("error", ex.getRule(), "message", ex.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", "acceso_denegado", "message", ex.getMessage()));
    }

    private Evento buscarEventoAutorizado(UUID eventoId, JwtPrincipal solicitante) {
        Evento evento = eventoRepository.buscarPorId(eventoId)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado: " + eventoId));
        if (!solicitante.esAdmin() && !evento.getOrganizadorId().equals(solicitante.userId())) {
            throw new SecurityException("Solo el organizador propietario o ADMIN puede gestionar tarifas");
        }
        return evento;
    }

    private JwtPrincipal requirePrincipal(JwtPrincipal principal) {
        if (principal == null) {
            throw new SecurityException("Operacion requiere autenticacion");
        }
        return principal;
    }
}
