package com.javeriana.eventos.inscription.infrastructure.web;

import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.in.CrearInscripcionUseCase;
import com.javeriana.eventos.inscription.infrastructure.persistence.SinCuposDisponiblesException;
import com.javeriana.eventos.inscription.infrastructure.web.dto.CrearInscripcionRequest;
import com.javeriana.eventos.inscription.infrastructure.web.dto.InscripcionResponse;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inscripciones")
public class InscripcionController {

    private final CrearInscripcionUseCase crearInscripcion;

    public InscripcionController(CrearInscripcionUseCase crearInscripcion) {
        this.crearInscripcion = crearInscripcion;
    }

    /**
     * POST /api/v1/inscripciones
     *
     * Crea una inscripción y retorna el checkout_url para que el usuario pague.
     * Si no hay cupos → 409 Conflict.
     * Si idempotencyKey ya existe → 200 OK con la inscripción existente.
     */
    @PostMapping
    public ResponseEntity<InscripcionResponse> crear(
            @Valid @RequestBody CrearInscripcionRequest request,
            @RequestHeader("X-User-Id") UUID usuarioId) {

        CrearInscripcionUseCase.Command command = new CrearInscripcionUseCase.Command(
            usuarioId,
            request.eventoId(),
            request.tarifaId(),
            request.idempotencyKey()
        );

        CrearInscripcionUseCase.Result result = crearInscripcion.crear(command);
        Inscripcion inscripcion = result.inscripcion();

        InscripcionResponse response = new InscripcionResponse(
            inscripcion.getId(),
            inscripcion.getEventoId(),
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

    @ExceptionHandler(SinCuposDisponiblesException.class)
    public ResponseEntity<Map<String, String>> handleSinCupos(SinCuposDisponiblesException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of(
                "error", "sin_cupos_disponibles",
                "message", ex.getMessage()
            ));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<Map<String, String>> handleBusinessRule(BusinessRuleViolationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(Map.of(
                "error", ex.getRule(),
                "message", ex.getMessage()
            ));
    }
}
