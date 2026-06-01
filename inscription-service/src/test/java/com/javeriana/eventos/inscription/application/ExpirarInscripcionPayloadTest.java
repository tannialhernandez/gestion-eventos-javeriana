package com.javeriana.eventos.inscription.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.inscription.infrastructure.serialization.JacksonEventoSerializador;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort;
import com.javeriana.eventos.inscription.domain.port.out.InscripcionRepository;
import com.javeriana.eventos.inscription.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios del payload de INSCRIPCION_EXPIRADA.
 *
 * Valida el hallazgo C-01 de la auditoría (CRÍTICO): el campo eventoId
 * del payload debe ser el UUID del evento académico, NO la cadena
 * "INSCRIPCION_EXPIRADA" que era lo que se publicaba antes del bug.
 *
 * Sin Spring context — puro JUnit 5 + Mockito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExpirarInscripcionesService — Payload INSCRIPCION_EXPIRADA")
class ExpirarInscripcionPayloadTest {

    @Mock private InscripcionRepository inscripcionRepository;
    @Mock private EventoServicePort     eventoService;
    @Mock private OutboxEventRepository outboxRepository;

    private ExpirarInscripcionesService servicio;
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
        servicio = new ExpirarInscripcionesService(
            inscripcionRepository, eventoService, outboxRepository, serializador);
    }

    /**
     * Crea una inscripción con fecha de expiración en el pasado para que
     * sea detectada por buscarExpiradas().
     */
    private Inscripcion inscripcionExpirada() {
        return new Inscripcion(
            INSCRIPCION_ID, USUARIO_ID, EVENTO_ID, TARIFA_ID,
            com.javeriana.eventos.inscription.domain.model.EstadoInscripcion.PENDIENTE_PAGO,
            Instant.now().minusSeconds(3600),                // fecha inscripción pasada
            Instant.now().minusSeconds(1800),                // ya expiró hace 30 minutos
            null, IDEMPOTENCY_KEY, 0
        );
    }

    // ─── Corrección C-01: eventoId no debe ser el eventType ──────────────────

    @Nested
    @DisplayName("Corrección C-01 — eventoId debe ser UUID, no el string del eventType")
    class CorreccionC01 {

        @BeforeEach
        void configurarMocks() {
            when(inscripcionRepository.buscarExpiradas())
                .thenReturn(List.of(inscripcionExpirada()));
            when(inscripcionRepository.guardar(any()))
                .thenAnswer(inv -> inv.getArgument(0));
            when(outboxRepository.guardar(any()))
                .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(eventoService).liberarCupo(any());
        }

        @Test
        @DisplayName("eventoId NO es el string 'INSCRIPCION_EXPIRADA' (regresión del bug C-01)")
        void expirar_eventoIdNoEsEventTypeCadena() throws Exception {
            servicio.expirarVencidas();

            JsonNode payload = capturarPayload();

            assertThat(payload.get("eventoId").asText())
                .as("eventoId NO debe ser el string del eventType (bug C-01)")
                .isNotEqualTo("INSCRIPCION_EXPIRADA");
        }

        @Test
        @DisplayName("eventoId ES el UUID del evento académico (valor correcto)")
        void expirar_eventoIdEsUuidDelEventoAcademico() throws Exception {
            servicio.expirarVencidas();

            JsonNode payload = capturarPayload();

            assertThat(payload.get("eventoId").asText())
                .as("eventoId debe ser el UUID del evento académico")
                .isEqualTo(EVENTO_ID.toString());
        }

        @Test
        @DisplayName("eventoId es parseable como UUID sin excepción")
        void expirar_eventoIdEsParseable() throws Exception {
            servicio.expirarVencidas();

            JsonNode payload = capturarPayload();
            String eventoIdStr = payload.get("eventoId").asText();

            // Debe parsear sin lanzar excepción
            UUID parsed = UUID.fromString(eventoIdStr);
            assertThat(parsed).isEqualTo(EVENTO_ID);
        }
    }

    // ─── Campos del payload ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Estructura completa del payload")
    class EstructuraPayload {

        @BeforeEach
        void configurarMocks() {
            when(inscripcionRepository.buscarExpiradas())
                .thenReturn(List.of(inscripcionExpirada()));
            when(inscripcionRepository.guardar(any()))
                .thenAnswer(inv -> inv.getArgument(0));
            when(outboxRepository.guardar(any()))
                .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(eventoService).liberarCupo(any());
        }

        @Test
        @DisplayName("El payload contiene usuarioId correcto")
        void expirar_payloadContieneUsuarioId() throws Exception {
            servicio.expirarVencidas();

            JsonNode payload = capturarPayload();

            assertThat(payload.get("usuarioId").asText())
                .isEqualTo(USUARIO_ID.toString());
        }

        @Test
        @DisplayName("El payload contiene eventType = INSCRIPCION_EXPIRADA")
        void expirar_payloadContieneEventTypeCorrecto() throws Exception {
            servicio.expirarVencidas();

            JsonNode payload = capturarPayload();

            assertThat(payload.get("eventType").asText())
                .isEqualTo("INSCRIPCION_EXPIRADA");
        }

        @Test
        @DisplayName("El payload contiene aggregateType = Inscripcion")
        void expirar_payloadContieneAggregateType() throws Exception {
            servicio.expirarVencidas();

            JsonNode payload = capturarPayload();

            assertThat(payload.get("aggregateType").asText())
                .isEqualTo("Inscripcion");
        }

        @Test
        @DisplayName("El payload contiene schemaVersion = v1")
        void expirar_payloadContieneSchemaVersion() throws Exception {
            servicio.expirarVencidas();

            JsonNode payload = capturarPayload();

            assertThat(payload.get("schemaVersion").asText())
                .isEqualTo("v1");
        }

        @Test
        @DisplayName("El payload NO contiene codigoQr (no aplica en expiración)")
        void expirar_payloadNoContieneCodigoQr() throws Exception {
            servicio.expirarVencidas();

            JsonNode payload = capturarPayload();

            // @JsonInclude(NON_NULL) excluye el campo cuando es null
            assertThat(payload.has("codigoQr"))
                .as("codigoQr no debe estar en el payload de INSCRIPCION_EXPIRADA")
                .isFalse();
        }

        @Test
        @DisplayName("El payload contiene todos los metadatos del evento")
        void expirar_payloadContieneMetadatosEvento() throws Exception {
            servicio.expirarVencidas();

            JsonNode payload = capturarPayload();

            assertThat(payload.has("eventId")).as("eventId").isTrue();
            assertThat(payload.has("aggregateId")).as("aggregateId").isTrue();
            assertThat(payload.has("occurredAt")).as("occurredAt").isTrue();
        }

        @Test
        @DisplayName("aggregateId del payload coincide con el inscripcionId")
        void expirar_aggregateIdEsInscripcionId() throws Exception {
            servicio.expirarVencidas();

            JsonNode payload = capturarPayload();

            assertThat(payload.get("aggregateId").asText())
                .isEqualTo(INSCRIPCION_ID.toString());
        }

        @Test
        @DisplayName("El OutboxEvent tiene aggregateType=Inscripcion y eventType correcto")
        void expirar_outboxEventConCamposCorrectos() {
            servicio.expirarVencidas();

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).guardar(captor.capture());
            OutboxEvent outbox = captor.getValue();

            assertThat(outbox.getAggregateType()).isEqualTo("Inscripcion");
            assertThat(outbox.getEventType()).isEqualTo("INSCRIPCION_EXPIRADA");
            assertThat(outbox.getAggregateId()).isEqualTo(INSCRIPCION_ID);
        }
    }

    // ─── Comportamiento del job ───────────────────────────────────────────────

    @Nested
    @DisplayName("Comportamiento del job de expiración")
    class ComportamientoJob {

        @Test
        @DisplayName("Retorna 0 cuando no hay inscripciones expiradas")
        void expirar_sinExpiradas_retornaCero() {
            when(inscripcionRepository.buscarExpiradas()).thenReturn(List.of());

            int resultado = servicio.expirarVencidas();

            assertThat(resultado).isZero();
            verify(outboxRepository, never()).guardar(any());
            verify(eventoService, never()).liberarCupo(any());
        }

        @Test
        @DisplayName("Llama a eventoService.liberarCupo con el eventoId correcto")
        void expirar_llamaLiberarCupoConEventoId() {
            when(inscripcionRepository.buscarExpiradas())
                .thenReturn(List.of(inscripcionExpirada()));
            when(inscripcionRepository.guardar(any()))
                .thenAnswer(inv -> inv.getArgument(0));
            when(outboxRepository.guardar(any()))
                .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(eventoService).liberarCupo(any());

            servicio.expirarVencidas();

            verify(eventoService).liberarCupo(EVENTO_ID);
        }
    }

    // ─── Helper de captura ───────────────────────────────────────────────────

    private JsonNode capturarPayload() throws Exception {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).guardar(captor.capture());
        return objectMapper.readTree(captor.getValue().getPayload());
    }
}
