package com.javeriana.eventos.inscription.infrastructure.web;

import com.javeriana.eventos.inscription.application.AsistenciaService;
import com.javeriana.eventos.inscription.application.CertificadoGeneratorService;
import com.javeriana.eventos.inscription.domain.model.Asistencia;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort;
import com.javeriana.eventos.inscription.infrastructure.security.JwtPrincipal;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/certificados")
public class CertificadoController {

    private final AsistenciaService asistenciaService;
    private final CertificadoGeneratorService certificadoGenerator;
    private final EventoServicePort eventoService;

    public CertificadoController(AsistenciaService asistenciaService,
                                 CertificadoGeneratorService certificadoGenerator,
                                 EventoServicePort eventoService) {
        this.asistenciaService = asistenciaService;
        this.certificadoGenerator = certificadoGenerator;
        this.eventoService = eventoService;
    }

    @GetMapping("/{inscripcionId}")
    public ResponseEntity<byte[]> descargar(
            @PathVariable UUID inscripcionId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        Inscripcion inscripcion = asistenciaService.buscarInscripcion(inscripcionId);
        if (!inscripcion.getUsuarioId().equals(principal.userId())) {
            throw new SecurityException("Solo el participante titular puede descargar este certificado");
        }
        if (!asistenciaService.esInscripcionCertificable(inscripcion)) {
            throw new BusinessRuleViolationException(
                "RN-CERTIFICADO-01",
                "Solo inscripciones confirmadas pueden generar certificado"
            );
        }

        Asistencia asistencia = asistenciaService.buscarAsistencia(inscripcionId);
        if (asistencia == null || !asistencia.isAsistio()) {
            throw new IllegalArgumentException("Certificado no disponible. Asistencia no registrada.");
        }

        EventoServicePort.EventoInfo evento = eventoService.obtenerEvento(inscripcion.getEventoId());
        byte[] pdf = certificadoGenerator.generar(
            inscripcion,
            evento,
            new CertificadoGeneratorService.ParticipanteCertificado(principal.name(), principal.email())
        );

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"certificado-" + inscripcionId + ".pdf\"")
            .body(pdf);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "certificado_no_disponible", "message", ex.getMessage()));
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
