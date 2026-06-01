package com.javeriana.eventos.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.core.Message;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test E2E — Flujo feliz completo.
 *
 * Flujo:
 *  1. Crear evento en event-service
 *  2. Publicar el evento
 *  3. Crear inscripción en inscription-service (→ llama a payment-service)
 *  4. Enviar webhook de pago confirmado a payment-service
 *  5. Verificar inscripción = CONFIRMADA
 *  6. Verificar mensaje pago.confirmado en RabbitMQ
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Flujo Feliz E2E: inscripción → pago → confirmación")
class FlujoFelizE2EIT extends E2ETestBase {

    private static final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private static final String COLA_PAGO_CONFIRMADO = "e2e.pago.confirmado";
    private static final String COLA_INS_CREADA      = "e2e.inscripcion.creada";

    @BeforeEach
    void setupColas() {
        declararColaTest(COLA_PAGO_CONFIRMADO, "pago.confirmado");
        declararColaTest(COLA_INS_CREADA, "inscripcion.creada");
    }

    @Test
    @DisplayName("Flujo completo: evento → inscripción → pago → CONFIRMADA")
    void flujoCompletoInscripcionPagoConfirmado() throws Exception {
        String token = JwtE2EHelper.tokenOrganizador();

        // ── 1. Crear evento ──────────────────────────────────────────────────
        log.info("[e2e] Paso 1: crear evento en event-service");
        String eventoBody = mapper.writeValueAsString(Map.of(
            "titulo",                  "Congreso E2E Test",
            "descripcion",             "Smoke test automatizado",
            "tipo",                    "CONGRESO",
            "modalidad",               "PRESENCIAL",
            "fechaInicio",             LocalDate.now().plusDays(30).toString(),
            "fechaFin",                LocalDate.now().plusDays(31).toString(),
            "fechaLimiteInscripcion",  LocalDateTime.now().plusDays(20).withNano(0).toString(),
            "cupoMaximo",              50
        ));

        JsonNode eventoResp = eventClient.post().uri("/api/v1/eventos")
            .header(HttpHeaders.AUTHORIZATION, JwtE2EHelper.bearer(token))
            .header("X-User-Id", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(eventoBody)
            .exchangeToMono(response -> {
                if (response.statusCode().isError()) {
                    return response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> reactor.core.publisher.Mono.error(
                            new WebClientResponseException(
                                "Crear evento fallo: " + body,
                                response.statusCode().value(),
                                response.statusCode().toString(),
                                response.headers().asHttpHeaders(),
                                body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                java.nio.charset.StandardCharsets.UTF_8)));
                }
                return response.bodyToMono(JsonNode.class);
            })
            .block();

        assertThat(eventoResp).isNotNull();
        UUID eventoId = UUID.fromString(eventoResp.get("id").asText());
        log.info("[e2e] Evento creado: {}", eventoId);

        // ── 2. Publicar evento ───────────────────────────────────────────────
        log.info("[e2e] Paso 2: publicar evento");
        eventClient.post().uri("/api/v1/eventos/{id}/publicar", eventoId)
            .header(HttpHeaders.AUTHORIZATION, JwtE2EHelper.bearer(token))
            .retrieve()
            .toBodilessEntity().block();

        // ── 3. Crear inscripción ─────────────────────────────────────────────
        log.info("[e2e] Paso 3: crear inscripción");
        String participanteToken = JwtE2EHelper.tokenParticipante();
        UUID tarifaId = crearTarifaParaEvento(eventoId);
        crearCupoLocalParaEvento(eventoId);

        String inscripcionBody = mapper.writeValueAsString(Map.of(
            "eventoId",       eventoId,
            "tarifaId",       tarifaId,
            "idempotencyKey", UUID.randomUUID()
        ));

        ClientResponse inscripcionResp = inscriptionClient.post()
            .uri("/api/v1/inscripciones")
            .header(HttpHeaders.AUTHORIZATION, JwtE2EHelper.bearer(participanteToken))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(inscripcionBody)
            .exchange()
            .block();

        // En función del mock de tarifas, puede ser 201 o 503 si event-service no tiene tarifas
        assertThat(inscripcionResp).isNotNull();
        int inscStatusCode = inscripcionResp.statusCode().value();
        log.info("[e2e] Inscripción resultado HTTP: {}", inscStatusCode);

        // Verificar al menos que el servicio responde (201 o 200)
        assertThat(inscStatusCode).isBetween(200, 299);

        JsonNode inscripcionJson = inscripcionResp.bodyToMono(JsonNode.class).block();
        UUID inscripcionId = UUID.fromString(inscripcionJson.get("inscripcionId").asText());
        log.info("[e2e] Inscripción creada: {} estado={}",
            inscripcionId, inscripcionJson.get("estado").asText());

        // ── 4. Verificar INSCRIPCION_CREADA en RabbitMQ ──────────────────────
        log.info("[e2e] Paso 4: verificar INSCRIPCION_CREADA en RabbitMQ");
        Message msgCreada = recibirMensaje(COLA_INS_CREADA, 15);
        assertThat(msgCreada.getMessageProperties().getHeader("eventType").toString())
            .isEqualTo("INSCRIPCION_CREADA");
        log.info("[e2e] ✅ INSCRIPCION_CREADA recibido con messageId={}",
            msgCreada.getMessageProperties().getMessageId());

        // ── 5. Enviar webhook de pago confirmado ─────────────────────────────
        log.info("[e2e] Paso 5: enviar webhook de pago aprobado");
        String referenciaExterna = "E2E-REF-" + System.currentTimeMillis();
        String webhookBody = mapper.writeValueAsString(Map.of(
            "referencia_externa", referenciaExterna,
            "inscripcion_id",     inscripcionId.toString(),
            "estado",             "approved",
            "metadata",           Map.of("monto", "150000", "moneda", "COP")
        ));

        // E2E configura PAYMENT_WEBHOOK_SECRET=e2e-secret; el webhook debe viajar firmado.
        ClientResponse webhookResp = paymentClient.post()
            .uri("/api/v1/webhooks/pagos")
            .header("X-Signature", calcularHmacSha256(webhookBody, "e2e-secret"))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(webhookBody)
            .exchange()
            .block();

        assertThat(webhookResp).isNotNull();
        assertThat(webhookResp.statusCode()).isEqualTo(HttpStatus.OK);
        log.info("[e2e] Webhook procesado: {}", webhookResp.statusCode());

        // ── 6. Verificar PAGO_CONFIRMADO en RabbitMQ ─────────────────────────
        log.info("[e2e] Paso 6: verificar PAGO_CONFIRMADO en RabbitMQ");
        Message msgPago = recibirMensaje(COLA_PAGO_CONFIRMADO, 15);

        assertThat(msgPago.getMessageProperties().getMessageId()).isNotNull();
        assertThat(msgPago.getMessageProperties().getHeader("eventType").toString())
            .isEqualTo("PAGO_CONFIRMADO");
        assertThat(msgPago.getMessageProperties().getHeader("x-schema-version").toString())
            .isEqualTo("v1");

        JsonNode pagoPayload = mapper.readTree(msgPago.getBody());
        assertThat(pagoPayload.has("inscripcionId")).isTrue();
        log.info("[e2e] ✅ PAGO_CONFIRMADO recibido. inscripcionId={}",
            pagoPayload.get("inscripcionId").asText());

        // ── 7. Verificar inscripción = CONFIRMADA en BD ───────────────────────
        log.info("[e2e] Paso 7: verificar estado final inscripción");
        Awaitility.await("inscripción CONFIRMADA").atMost(15, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> "CONFIRMADA".equals(consultarEstadoInscripcion(inscripcionId)));

        log.info("[e2e] ✅ FLUJO FELIZ COMPLETADO — inscripción {} = CONFIRMADA", inscripcionId);
    }

    private UUID crearTarifaParaEvento(UUID eventoId) throws Exception {
        UUID tarifaId = UUID.randomUUID();
        try (Connection connection = DB_EVENT.createConnection("");
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO tarifa (
                     id, evento_id, nombre, precio, moneda, aplica_a,
                     fecha_inicio_vigencia, fecha_fin_vigencia, activa
                 )
                 VALUES (
                     ?, ?, 'Tarifa E2E', 150000.00, 'COP', 'ESTUDIANTE_JAVERIANA',
                     CURRENT_DATE, CURRENT_DATE + 365, true
                 )
                 """)) {
            statement.setObject(1, tarifaId);
            statement.setObject(2, eventoId);
            statement.executeUpdate();
        }
        return tarifaId;
    }

    private void crearCupoLocalParaEvento(UUID eventoId) throws Exception {
        try (Connection connection = DB_INSCRIPTION.createConnection("");
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO evento_cupo (evento_id, cupo_disponible, cupo_maximo, version)
                 VALUES (?, 50, 50, 0)
                 """)) {
            statement.setObject(1, eventoId);
            statement.executeUpdate();
        }
    }

    private String consultarEstadoInscripcion(UUID inscripcionId) throws Exception {
        try (Connection connection = DB_INSCRIPTION.createConnection("");
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT estado FROM inscripcion WHERE id = ?")) {
            statement.setObject(1, inscripcionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("estado") : null;
            }
        }
    }

    private String calcularHmacSha256(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
