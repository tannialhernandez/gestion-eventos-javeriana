package com.javeriana.eventos.payment.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase.ResultadoWebhook;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase.WebhookPayload;
import com.javeriana.eventos.payment.infrastructure.observability.MdcKeys;
import com.javeriana.eventos.payment.infrastructure.security.WebhookSignatureValidator;
import com.javeriana.eventos.payment.infrastructure.web.dto.WebhookPagoRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoint que recibe notificaciones (Webhooks) de la pasarela de pago.
 *
 * Prompt 18/19 — Mejoras de seguridad y robustez:
 *
 * C-01: Validación HMAC-SHA256 del cuerpo crudo del request antes de cualquier
 *       procesamiento. Sin esto, cualquier actor puede confirmar pagos falsos.
 *
 * m-01: DTO tipado (WebhookPagoRequest) con validación de campos requeridos.
 *       Evita que UUID.fromString("") lance IllegalArgumentException → HTTP 500
 *       → reintento infinito de la pasarela.
 *
 * M-03: Propagación del Correlation-ID del header HTTP al MDC para trazabilidad
 *       E2E entre inscription-service → payment-service → inscription-service.
 *       El CorrelationIdFilter ya lo establece; aquí solo lo logueamos.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ProcesarWebhookUseCase     procesarWebhook;
    private final WebhookSignatureValidator  signatureValidator;
    private final ObjectMapper               objectMapper;

    public WebhookController(ProcesarWebhookUseCase procesarWebhook,
                             WebhookSignatureValidator signatureValidator,
                             ObjectMapper objectMapper) {
        this.procesarWebhook    = procesarWebhook;
        this.signatureValidator = signatureValidator;
        this.objectMapper       = objectMapper;
    }

    /**
     * POST /api/v1/webhooks/pagos
     *
     * Recibe la notificación de la pasarela cuando el pago se completa/rechaza.
     * Debe responder HTTP 200 dentro de 5 segundos para evitar reintento.
     *
     * Payload esperado (snake_case):
     * {
     *   "referencia_externa": "MP-123",
     *   "inscripcion_id":     "uuid",
     *   "estado":             "approved|rejected",
     *   "metadata":           { ... }
     * }
     */
    @PostMapping("/pagos")
    public ResponseEntity<Map<String, String>> procesarPago(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Signature",     required = false) String signature,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        // M-03: MDC ya tiene correlationId del CorrelationIdFilter.
        // Si el webhook viene de la pasarela sin el header, el filtro generó un UUID.
        String corrId = MDC.get(MdcKeys.CORRELATION_ID);
        if (corrId == null || corrId.isBlank()) {
            corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
            MDC.put(MdcKeys.CORRELATION_ID, corrId);
        }

        try {
            // ── C-01: Validar firma HMAC-SHA256 ──────────────────────────────────
            if (!signatureValidator.isValid(rawBody, signature)) {
                log.warn("[webhook] Firma HMAC inválida rechazada. corrId={}", corrId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "firma_invalida",
                                 "message", "Firma del webhook no coincide"));
            }

            // ── m-01: Parsear DTO defensivamente ─────────────────────────────────
            WebhookPagoRequest request;
            try {
                request = objectMapper.readValue(rawBody, WebhookPagoRequest.class);
            } catch (Exception e) {
                log.error("[webhook] JSON malformado — rechazando sin reintento: {}", e.getMessage());
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "payload_invalido", "message", e.getMessage()));
            }

            // ── m-01: Validar campos requeridos ───────────────────────────────────
            if (!request.camposRequeridosPresentes()) {
                log.error("[webhook] Campos requeridos ausentes: referencia_externa={} " +
                          "inscripcion_id={} estado={}",
                    request.referenciaExterna(), request.inscripcionId(), request.estado());
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "campos_requeridos_ausentes",
                                 "message", "referencia_externa, inscripcion_id y estado son obligatorios"));
            }

            log.info("[webhook] Recibido: ref={} inscripcion={} estado={} corrId={}",
                request.referenciaExterna(), request.inscripcionId(), request.estado(), corrId);

            // ── Procesar ──────────────────────────────────────────────────────────
            WebhookPayload payload = new WebhookPayload(
                request.referenciaExterna(), request.inscripcionId(),
                request.estado(), rawBody);

            ResultadoWebhook resultado = procesarWebhook.procesar(payload);

            log.info("[webhook] Resultado: {} para ref={} corrId={}",
                resultado, request.referenciaExterna(), corrId);

            return ResponseEntity.ok(Map.of("resultado", resultado.name()));

        } catch (IllegalArgumentException e) {
            // UUID malformado u otros datos inválidos — rechazar sin reintento
            log.error("[webhook] Dato inválido: {} corrId={}", e.getMessage(), corrId);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "dato_invalido", "message", e.getMessage()));
        }
    }
}
