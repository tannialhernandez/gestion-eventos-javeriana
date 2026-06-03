package com.javeriana.eventos.inscription.infrastructure.web;

import com.javeriana.eventos.inscription.application.AsistenciaService;
import com.javeriana.eventos.inscription.infrastructure.security.JwtPrincipal;
import com.javeriana.eventos.inscription.infrastructure.web.dto.AsistenciaResponse;
import com.javeriana.eventos.inscription.infrastructure.web.dto.RegistrarAsistenciaRequest;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/asistencias")
public class AsistenciaController {

    private final AsistenciaService asistenciaService;

    public AsistenciaController(AsistenciaService asistenciaService) {
        this.asistenciaService = asistenciaService;
    }

    @GetMapping("/eventos/{eventoId}")
    public List<AsistenciaResponse> listarPorEvento(
            @PathVariable UUID eventoId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return asistenciaService.listarPorEvento(eventoId, principal)
            .stream()
            .map(AsistenciaResponse::from)
            .toList();
    }

    @PostMapping("/eventos/{eventoId}")
    public AsistenciaResponse registrar(
            @PathVariable UUID eventoId,
            @Valid @RequestBody RegistrarAsistenciaRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        String registradoPor = principal.email() != null ? principal.email() : principal.userId().toString();
        return AsistenciaResponse.from(asistenciaService.registrar(
            eventoId,
            request.inscripcionId(),
            request.asistio(),
            registradoPor,
            request.observaciones(),
            principal
        ));
    }

    @GetMapping("/mia")
    public AsistenciaResponse consultarPropia(
            @RequestParam UUID eventoId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return AsistenciaResponse.from(asistenciaService.consultarPropiaPorEvento(eventoId, principal));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "no_encontrado", "message", ex.getMessage()));
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
}
