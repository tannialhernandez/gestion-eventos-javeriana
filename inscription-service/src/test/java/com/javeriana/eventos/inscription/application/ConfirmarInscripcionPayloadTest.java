package com.javeriana.eventos.inscription.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javeriana.eventos.inscription.domain.model.EstadoInscripcion;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.out.InscripcionRepository;
import com.javeriana.eventos.inscription.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.inscription.infrastructure.serialization.JacksonEventoSerializador;
import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios del payload de INSCRIPCION_CONFIRMADA.
 *
 * Valida el hallazgo M-01 de la auditoría: el payload debe incluir
 * eventoId, usuarioId, codigoQr, aggregateType y schemaVersion además
 * de los campos genéricos del evento.
 *
 * Sin Spring context — puro JUnit 5 + Mockito para velocidad de ejecución.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConfirmarInscripcionService — Payload INSCRIPCION_CONFIRMADA")
class ConfirmarInscripcionPayloadTest {

    @Mock private InscripcionRepository  inscripcionRepository;
    @Mock private OutboxEventRepository  outboxRepository;

    private ConfirmarInscripcionService servicio;
    private ObjectMapper objectMapper;

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private static final UUID INSCRIPCION_ID  = UUID.randomUUID();
    private static final UUID USUARIO_ID      = UUID.randomUUID();
    private static final UUID EVENTO_ID       = UUID.randomUUID();
    private static final UUID TARIFA_ID       = UUID.randomUUID();
    private static final UUID IDEMPOTENCY_KEY = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JacksonEventoSerializador serializador = new JacksonEventoSerializador(objectMapper);
        servicio = new ConfirmarInscripcionService(
            inscripcionRepository, outboxRepository, serializador);
    }

    private Inscripcion inscripcionPendiente() {
        // Usa constructor de RECONSTRUCCIÓN (no el de creación) para aislar el test.
        // En producción, ConfirmarInscripcionService carga la inscripción desde el
        // repositorio, que usa el constructor de reconstrucción sin registrar eventos.
        // El constructor de creación ahora registra InscripcionCreadaEvent, que
        // CrearInscripcionService ya procesó antes de que llegue la confirmación.
        return new Inscripcion(
            INSCRIPCION_ID, USUARIO_ID, EVENTO_ID, TARIFA_ID,
            com.javeriana.eventos.inscription.domain.model.EstadoInscripcion.PENDIENTE_PAGO,
            java.time.Instant.now().minusSeconds(60),   // fechaInscripcion
            java.time.Instant.now().plusSeconds(840),   // 14 min restantes
            null,                                        // codigoQr
            IDEMPOTENCY_KEY, 0                           // version
        );
    }

    // ─── Campos obligatorios del payload ─────────────────────────────────────

    @Nested
    @DisplayName("Dado una inscripción PENDIENTE_PAGO")
    class DadaInscripcionPendiente {

        @BeforeEach
        void configurarMocks() {
            Inscripcion inscripcion = inscripcionPendiente();
            when(inscripcionRepository.buscarPorId(INSCRIPCION_ID))
                .thenReturn(Optional.of(inscripcion));
            when(inscripcionRepository.guardar(any()))
                .thenAnswer(inv -> inv.getArgument(0));
            when(outboxRepository.guardar(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("El payload JSON incluye eventoId correcto (M-01)")
        void confirmar_payloadContieneEventoIdCorrecto() throws Exception {
            servicio.confirmar(INSCRIPCION_ID, "REF-TEST-001");

            JsonNode payload = capturarPayload();

            assertThat(payload.get("eventoId").asText())
                .as("eventoId debe ser el UUID del evento académico")
                .isEqualTo(EVENTO_ID.toString());
        }

        @Test
        @DisplayName("El payload JSON incluye usuarioId correcto (M-01)")
        void confirmar_payloadContieneUsuarioId() throws Exception {
            servicio.confirmar(INSCRIPCION_ID, "REF-TEST-001");

            JsonNode payload = capturarPayload();

            assertThat(payload.get("usuarioId").asText())
                .as("usuarioId debe ser el UUID del participante")
                .isEqualTo(USUARIO_ID.toString());
        }

        @Test
        @DisplayName("El payload JSON incluye codigoQr generado (M-01)")
        void confirmar_payloadContieneCodigoQr() throws Exception {
            servicio.confirmar(INSCRIPCION_ID, "REF-TEST-001");

            JsonNode payload = capturarPayload();

            assertThat(payload.has("codigoQr"))
                .as("codigoQr debe estar presente en INSCRIPCION_CONFIRMADA")
                .isTrue();
            assertThat(payload.get("codigoQr").asText())
                .as("codigoQr debe seguir el formato QR-{uuid}-{prefix}")
                .startsWith("QR-");
        }

        @Test
        @DisplayName("El payload JSON incluye aggregateType = Inscripcion")
        void confirmar_payloadContieneAggregateType() throws Exception {
            servicio.confirmar(INSCRIPCION_ID, "REF-TEST-001");

            JsonNode payload = capturarPayload();

            assertThat(payload.get("aggregateType").asText())
                .isEqualTo("Inscripcion");
        }

        @Test
        @DisplayName("El payload JSON incluye eventType = INSCRIPCION_CONFIRMADA")
        void confirmar_payloadContieneEventType() throws Exception {
            servicio.confirmar(INSCRIPCION_ID, "REF-TEST-001");

            JsonNode payload = capturarPayload();

            assertThat(payload.get("eventType").asText())
                .isEqualTo("INSCRIPCION_CONFIRMADA");
        }

        @Test
        @DisplayName("El payload JSON incluye schemaVersion = v1")
        void confirmar_payloadContieneSchemaVersion() throws Exception {
            servicio.confirmar(INSCRIPCION_ID, "REF-TEST-001");

            JsonNode payload = capturarPayload();

            assertThat(payload.get("schemaVersion").asText())
                .isEqualTo("v1");
        }

        @Test
        @DisplayName("El payload JSON incluye todos los campos de metadatos del evento")
        void confirmar_payloadContieneMetadatosCompletos() throws Exception {
            servicio.confirmar(INSCRIPCION_ID, "REF-TEST-001");

            JsonNode payload = capturarPayload();

            assertThat(payload.has("eventId")).as("eventId").isTrue();
            assertThat(payload.has("aggregateId")).as("aggregateId").isTrue();
            assertThat(payload.has("occurredAt")).as("occurredAt").isTrue();
        }

        @Test
        @DisplayName("aggregateId del payload coincide con el inscripcionId")
        void confirmar_aggregateIdEsInscripcionId() throws Exception {
            servicio.confirmar(INSCRIPCION_ID, "REF-TEST-001");

            JsonNode payload = capturarPayload();

            assertThat(payload.get("aggregateId").asText())
                .as("aggregateId debe ser el inscripcionId")
                .isEqualTo(INSCRIPCION_ID.toString());
        }

        @Test
        @DisplayName("El OutboxEvent tiene aggregateType=Inscripcion y eventType correcto")
        void confirmar_outboxEventConCamposCorrectos() {
            servicio.confirmar(INSCRIPCION_ID, "REF-TEST-001");

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).guardar(captor.capture());
            OutboxEvent outbox = captor.getValue();

            assertThat(outbox.getAggregateType()).isEqualTo("Inscripcion");
            assertThat(outbox.getEventType()).isEqualTo("INSCRIPCION_CONFIRMADA");
            assertThat(outbox.getAggregateId()).isEqualTo(INSCRIPCION_ID);
        }
    }

    // ─── Idempotencia ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotencia")
    class Idempotencia {

        @Test
        @DisplayName("Inscripción ya CONFIRMADA — no publica segundo evento al outbox")
        void confirmar_yaConfirmada_noPublicaEvento() {
            Inscripcion inscripcionConfirmada = new Inscripcion(
                INSCRIPCION_ID, USUARIO_ID, EVENTO_ID, TARIFA_ID,
                EstadoInscripcion.CONFIRMADA, null, null, "QR-existente",
                IDEMPOTENCY_KEY, 1);

            when(inscripcionRepository.buscarPorId(INSCRIPCION_ID))
                .thenReturn(Optional.of(inscripcionConfirmada));

            servicio.confirmar(INSCRIPCION_ID, "REF-DUPLICADO");

            verify(outboxRepository, never()).guardar(any());
        }

        @Test
        @DisplayName("Inscripción EXPIRADA — no publica evento al outbox")
        void confirmar_yaExpirada_noPublicaEvento() {
            Inscripcion inscripcionExpirada = new Inscripcion(
                INSCRIPCION_ID, USUARIO_ID, EVENTO_ID, TARIFA_ID,
                EstadoInscripcion.EXPIRADA, null, null, null,
                IDEMPOTENCY_KEY, 1);

            when(inscripcionRepository.buscarPorId(INSCRIPCION_ID))
                .thenReturn(Optional.of(inscripcionExpirada));

            servicio.confirmar(INSCRIPCION_ID, "REF-TARDIO");

            verify(outboxRepository, never()).guardar(any());
        }
    }

    // ─── Helper de captura ───────────────────────────────────────────────────

    private JsonNode capturarPayload() throws Exception {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).guardar(captor.capture());
        return objectMapper.readTree(captor.getValue().getPayload());
    }
}
