package com.javeriana.eventos.payment.security;

import com.javeriana.eventos.payment.infrastructure.security.WebhookSignatureValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios del validador HMAC-SHA256 para webhooks.
 * Sin Spring context — POJO puro con ReflectionTestUtils para inyectar el secret.
 */
@DisplayName("WebhookSignatureValidator — HMAC-SHA256")
class WebhookSignatureValidatorTest {

    private WebhookSignatureValidator validator;

    private static final String SECRET  = "test-secret-para-hmac-sha256";
    private static final String PAYLOAD = "{\"referencia_externa\":\"MP-001\",\"estado\":\"approved\"}";

    @BeforeEach
    void setUp() {
        validator = new WebhookSignatureValidator();
        ReflectionTestUtils.setField(validator, "webhookSecret", SECRET);
    }

    private String calcularHmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    // ─── Firma válida ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Firma válida")
    class FirmaValida {

        @Test
        @DisplayName("Firma correcta → isValid retorna true")
        void firmaCorrecta_retornaTrue() throws Exception {
            String firma = calcularHmac(PAYLOAD);
            assertThat(validator.isValid(PAYLOAD, firma)).isTrue();
        }

        @Test
        @DisplayName("Firma es idéntica con el mismo payload y secret")
        void mismaFirmaParaMismoPayloadYSecret() throws Exception {
            String firma1 = calcularHmac(PAYLOAD);
            String firma2 = calcularHmac(PAYLOAD);
            assertThat(firma1).isEqualTo(firma2);
            assertThat(validator.isValid(PAYLOAD, firma1)).isTrue();
            assertThat(validator.isValid(PAYLOAD, firma2)).isTrue();
        }
    }

    // ─── Firma inválida ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Firma inválida")
    class FirmaInvalida {

        @Test
        @DisplayName("Firma manipulada → isValid retorna false (C-01 anti-fraude)")
        void firmaManipulada_retornaFalse() throws Exception {
            String firmaReal = calcularHmac(PAYLOAD);
            String firmaFalsa = firmaReal.substring(0, firmaReal.length() - 4) + "DEAD";
            assertThat(validator.isValid(PAYLOAD, firmaFalsa)).isFalse();
        }

        @Test
        @DisplayName("Firma nula → isValid retorna false")
        void firmaNula_retornaFalse() {
            assertThat(validator.isValid(PAYLOAD, null)).isFalse();
        }

        @Test
        @DisplayName("Firma vacía → isValid retorna false")
        void firmaVacia_retornaFalse() {
            assertThat(validator.isValid(PAYLOAD, "")).isFalse();
        }

        @Test
        @DisplayName("Payload diferente con misma firma → isValid retorna false")
        void payloadAlterado_retornaFalse() throws Exception {
            String firmaOriginal = calcularHmac(PAYLOAD);
            String payloadAlterado = PAYLOAD.replace("approved", "rejected");
            assertThat(validator.isValid(payloadAlterado, firmaOriginal)).isFalse();
        }
    }

    // ─── Secret no configurado (modo dev/simulador) ───────────────────────────

    @Nested
    @DisplayName("Sin secret configurado (modo simulador/dev)")
    class SinSecretConfigurado {

        @Test
        @DisplayName("Sin secret → acepta sin validar (solo para dev)")
        void sinSecret_aceptaCualquierFirma() {
            ReflectionTestUtils.setField(validator, "webhookSecret", "");
            assertThat(validator.isValid(PAYLOAD, null)).isTrue();
            assertThat(validator.isValid(PAYLOAD, "cualquier-cosa")).isTrue();
        }
    }
}
