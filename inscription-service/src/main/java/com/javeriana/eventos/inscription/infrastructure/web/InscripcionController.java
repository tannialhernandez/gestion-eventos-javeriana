package com.javeriana.eventos.inscription.infrastructure.web;

import com.javeriana.eventos.inscription.domain.exceptions.ServicioExternoNoDisponibleException;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.in.CrearInscripcionUseCase;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort;
import com.javeriana.eventos.inscription.domain.port.out.InscripcionRepository;
import com.javeriana.eventos.inscription.domain.port.out.PaymentServicePort;
import com.javeriana.eventos.inscription.infrastructure.persistence.SinCuposDisponiblesException;
import com.javeriana.eventos.inscription.infrastructure.security.JwtPrincipal;
import com.javeriana.eventos.inscription.infrastructure.web.dto.CrearInscripcionRequest;
import com.javeriana.eventos.inscription.infrastructure.web.dto.InscripcionResponse;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller REST para inscripciones.
 *
 * Corrección C-02 (Prompt 12): el usuarioId ya NO proviene del header
 * "X-User-Id" (falsificable por cualquier cliente). Ahora se extrae del
 * JWT validado, disponible como {@code @AuthenticationPrincipal JwtPrincipal}.
 *
 * La firma RSA-256 del auth-service garantiza que el userId es auténtico
 * (ADR-010, Ley 1581 Art. 17: tratamiento con consentimiento verificado).
 */
@RestController
@RequestMapping("/api/v1/inscripciones")
public class InscripcionController {

    private static final String EVENT_SERVICE_CIRCUIT_BREAKER = "evento-service";

    private final CrearInscripcionUseCase crearInscripcion;
    private final InscripcionRepository inscripcionRepository;
    private final EventoServicePort eventoService;
    private final PaymentServicePort paymentService;
    private final CircuitBreaker eventServiceCircuitBreaker;

    public InscripcionController(CrearInscripcionUseCase crearInscripcion,
                                 InscripcionRepository inscripcionRepository,
                                 EventoServicePort eventoService,
                                 PaymentServicePort paymentService,
                                 CircuitBreakerRegistry circuitBreakerRegistry) {
        this.crearInscripcion = crearInscripcion;
        this.inscripcionRepository = inscripcionRepository;
        this.eventoService = eventoService;
        this.paymentService = paymentService;
        this.eventServiceCircuitBreaker =
            circuitBreakerRegistry.circuitBreaker(EVENT_SERVICE_CIRCUIT_BREAKER);
    }

    /**
     * POST /api/v1/inscripciones
     *
     * Requiere rol PARTICIPANTE, ORGANIZADOR o ADMIN (SecurityConfig).
     * El usuarioId se extrae del JWT — nunca del header X-User-Id.
     *
     * Respuestas:
     *   201 CREATED   — inscripción creada con checkout_url
     *   200 OK        — idempotente: ya existe para esta idempotencyKey
     *   401           — sin token o token inválido/expirado
     *   403           — rol insuficiente
     *   409 Conflict  — sin cupos disponibles
     *   422           — violación de regla de negocio
     *   503           — event-service o payment-service no disponibles
     */
    @PostMapping
    public ResponseEntity<InscripcionResponse> crear(
            @Valid @RequestBody CrearInscripcionRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {

        if (eventServiceCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            throw new ServicioExternoNoDisponibleException(
                "event-service",
                "circuit breaker abierto"
            );
        }

        CrearInscripcionUseCase.Command command = new CrearInscripcionUseCase.Command(
            principal.userId(),     // ← del JWT, no de un header HTTP falsificable
            request.eventoId(),
            request.tarifaId(),
            request.idempotencyKey()
        );

        CrearInscripcionUseCase.Result result = crearInscripcion.crear(command);
        Inscripcion inscripcion = result.inscripcion();

        InscripcionResponse response = new InscripcionResponse(
            inscripcion.getId(),
            inscripcion.getEventoId(),
            inscripcion.getTarifaId(),
            inscripcion.getEstado().name(),
            inscripcion.getFechaInscripcion().toString(),
            inscripcion.getFechaExpiracionPago() != null
                ? inscripcion.getFechaExpiracionPago().toString() : null,
            result.checkoutUrl(),
            result.expiraEnSegundos()
        );

        HttpStatus status = result.checkoutUrl() != null
            ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/mia")
    public ResponseEntity<InscripcionResponse> miInscripcion(
            @RequestParam UUID eventoId,
            @AuthenticationPrincipal JwtPrincipal principal) {

        return inscripcionRepository.buscarPorUsuarioIdYEventoId(principal.userId(), eventoId)
            .map(this::toResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/mias")
    public List<InscripcionResponse> misInscripciones(
            @AuthenticationPrincipal JwtPrincipal principal) {

        return inscripcionRepository.buscarPorUsuarioId(principal.userId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @PostMapping("/{inscripcionId}/cancelar")
    @Transactional
    public InscripcionResponse cancelar(
            @PathVariable UUID inscripcionId,
            @AuthenticationPrincipal JwtPrincipal principal) {

        Inscripcion inscripcion = inscripcionRepository.buscarPorId(inscripcionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscripción no encontrada"));

        if (!inscripcion.getUsuarioId().equals(principal.userId()) && !principal.esAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes cancelar esta inscripción");
        }

        if ("CANCELADA".equals(inscripcion.getEstado().name())) {
            return toResponse(inscripcion);
        }

        boolean ocupabaCupo = inscripcion.getEstado().name().equals("CONFIRMADA")
            || inscripcion.getEstado().name().equals("PENDIENTE_PAGO");

        if (inscripcion.getEstado().name().equals("CONFIRMADA")) {
            paymentService.reembolsar(inscripcion.getId());
        }

        inscripcion.cancelar();
        Inscripcion cancelada = inscripcionRepository.guardar(inscripcion);

        if (ocupabaCupo) {
            inscripcionRepository.liberarCupo(cancelada.getEventoId());
            eventoService.liberarCupo(cancelada.getEventoId());
        }

        return toResponse(cancelada);
    }

    private InscripcionResponse toResponse(Inscripcion inscripcion) {
        return new InscripcionResponse(
            inscripcion.getId(),
            inscripcion.getEventoId(),
            inscripcion.getTarifaId(),
            inscripcion.getEstado().name(),
            inscripcion.getFechaInscripcion().toString(),
            inscripcion.getFechaExpiracionPago() != null
                ? inscripcion.getFechaExpiracionPago().toString() : null,
            null,
            calcularSegundosRestantes(inscripcion)
        );
    }

    private long calcularSegundosRestantes(Inscripcion inscripcion) {
        if (inscripcion.getFechaExpiracionPago() == null) return 0;
        long restantes = inscripcion.getFechaExpiracionPago().getEpochSecond()
            - java.time.Instant.now().getEpochSecond();
        return Math.max(restantes, 0);
    }

    // ─── Exception handlers ───────────────────────────────────────────────────

    @ExceptionHandler(SinCuposDisponiblesException.class)
    public ResponseEntity<Map<String, String>> handleSinCupos(
            SinCuposDisponiblesException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "sin_cupos_disponibles",
                         "message", ex.getMessage()));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<Map<String, String>> handleBusinessRule(
            BusinessRuleViolationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(Map.of("error", ex.getRule(), "message", ex.getMessage()));
    }

    @ExceptionHandler(ServicioExternoNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> handleServicioNoDisponible(
            ServicioExternoNoDisponibleException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, "15")
            .body(Map.of("error", "servicio_no_disponible",
                         "servicio", ex.getServicio(),
                         "message", "El servicio está temporalmente no disponible. " +
                                    "Intente en unos momentos."));
    }
}
