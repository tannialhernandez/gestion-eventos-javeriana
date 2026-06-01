package com.javeriana.eventos.payment.infrastructure.web;

import com.javeriana.eventos.payment.application.PasarelaNoDisponibleException;
import com.javeriana.eventos.payment.domain.port.in.CrearPreferenciaUseCase;
import com.javeriana.eventos.payment.infrastructure.web.dto.CrearPreferenciaRequest;
import com.javeriana.eventos.payment.infrastructure.web.dto.PreferenciaResponse;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pagos")
public class PagoController {

    private final CrearPreferenciaUseCase crearPreferencia;

    public PagoController(CrearPreferenciaUseCase crearPreferencia) {
        this.crearPreferencia = crearPreferencia;
    }

    /**
     * POST /api/v1/pagos
     *
     * C-02 (Prompt 18/19): path corregido a /preferencias para alinear con el
     * contrato del Feign client de inscription-service:
     *   PaymentServiceFeignClient → POST /api/v1/pagos/preferencias
     *
     * Crea un pago en INICIADO y retorna el checkout URL.
     * Circuit Breaker activo: si la pasarela no responde → HTTP 503.
     */
    @PostMapping("/preferencias")
    public ResponseEntity<PreferenciaResponse> crear(
            @Valid @RequestBody CrearPreferenciaRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID usuarioId) {

        CrearPreferenciaUseCase.Command command = new CrearPreferenciaUseCase.Command(
            request.inscripcionId(),
            request.monto(),
            request.moneda(),
            usuarioId != null ? usuarioId : UUID.randomUUID()
        );

        CrearPreferenciaUseCase.Result result = crearPreferencia.crear(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(
            new PreferenciaResponse(result.pagoId(), result.checkoutUrl(), result.preferenciaId())
        );
    }

    @ExceptionHandler(PasarelaNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> handlePasarelaNoDisponible(PasarelaNoDisponibleException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "payment_service_unavailable",
                "message", ex.getMessage(),
                "retry_after", "30"
            ));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<Map<String, String>> handleBusinessRule(BusinessRuleViolationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(Map.of("error", ex.getRule(), "message", ex.getMessage()));
    }
}
