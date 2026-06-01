package com.javeriana.eventos.payment.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Valida la firma HMAC-SHA256 de los Webhooks entrantes de la pasarela.
 *
 * C-01 (Prompt 18/19): sin esta validación, cualquier actor puede enviar un
 * webhook falso con estado "approved" y confirmar pagos sin cobro real.
 *
 * Protocolo:
 *   - La pasarela (MercadoPago / Simulador) calcula HMAC-SHA256(body, secret).
 *   - Envía el digest en hexadecimal en el header "X-Signature".
 *   - Este validador recalcula el digest y compara con tiempo constante
 *     (MessageDigest.isEqual) para evitar ataques de timing.
 *
 * Configuración:
 *   payment.gateway.webhook-secret:
 *     - Local: cadena fija en application-local.yml (solo demos)
 *     - Prod: ${PAYMENT_WEBHOOK_SECRET} inyectado por AWS Secrets Manager
 *     - Test: cadena fija en application-test.yml
 *
 * Si el secret NO está configurado (vacío) → loguea advertencia y ACEPTA el
 * webhook sin validar (permite uso con simulador sin configuración adicional).
 * En producción, el secret siempre debe estar configurado.
 */
@Component
public class WebhookSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureValidator.class);

    @Value("${payment.gateway.webhook-secret:}")
    private String webhookSecret;

    /**
     * Verifica que la firma HMAC-SHA256 del payload coincide con el header.
     *
     * @param rawBody   cuerpo crudo del request (antes de parsear JSON)
     * @param signature valor del header "X-Signature" enviado por la pasarela
     * @return true si la firma es válida; false si es inválida o nula
     */
    public boolean isValid(String rawBody, String signature) {
        // Si no hay secret configurado → modo desarrollo/simulador: aceptar sin validar
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("[webhook-hmac] ADVERTENCIA: payment.gateway.webhook-secret no configurado. " +
                "Validación HMAC omitida. Configurar en producción.");
            return true;
        }

        if (signature == null || signature.isBlank()) {
            log.warn("[webhook-hmac] Header X-Signature ausente.");
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expectedBytes = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(expectedBytes);

            // Comparación en tiempo constante → previene ataques de timing
            return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.error("[webhook-hmac] Error calculando HMAC: {}", e.getMessage());
            return false;
        }
    }
}
