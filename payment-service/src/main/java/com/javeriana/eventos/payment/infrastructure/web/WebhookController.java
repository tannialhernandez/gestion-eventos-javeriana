package com.javeriana.eventos.payment.infrastructure.web;

import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase.ResultadoWebhook;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase.WebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ProcesarWebhookUseCase procesarWebhook;
    private final ObjectMapper objectMapper;

    public WebhookController(ProcesarWebhookUseCase procesarWebhook,
                             ObjectMapper objectMapper) {
        this.procesarWebhook = procesarWebhook;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /api/v1/webhooks/pagos
     *
     * Endpoint que recibe notificaciones de la pasarela de pago.
     * Debe responder HTTP 200 dentro de 5 segundos para evitar reintento de la pasarela.
     *
     * Idempotencia (ADR-09, RN-03): si el mismo webhook llega dos veces,
     * el segundo retorna 200 sin reprocessing.
     *
     * Payload esperado:
     * {
     *   "referencia_externa": "MP-123",
     *   "inscripcion_id":     "uuid",
     *   "estado":             "approved|rejected",
     *   "metadata":           { ... }  ← opcional
     * }
     */
    @PostMapping("/pagos")
    public ResponseEntity<Map<String, String>> procesarPago(
            @RequestBody Map<String, Object> rawPayload) {

        String referenciaExterna = getString(rawPayload, "referencia_externa");
        String inscripcionId     = getString(rawPayload, "inscripcion_id");
        String estado            = getString(rawPayload, "estado");

        String metadatosJson;
        try {
            metadatosJson = objectMapper.writeValueAsString(rawPayload);
        } catch (Exception e) {
            metadatosJson = "{}";
        }

        log.info("[webhook] Recibido pago: referenciaExterna={} inscripcionId={} estado={}",
            referenciaExterna, inscripcionId, estado);

        WebhookPayload payload = new WebhookPayload(
            referenciaExterna, inscripcionId, estado, metadatosJson
        );

        ResultadoWebhook resultado = procesarWebhook.procesar(payload);

        log.info("[webhook] Resultado: {} para referencia={}", resultado, referenciaExterna);

        return ResponseEntity.ok(Map.of("resultado", resultado.name()));
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }
}
