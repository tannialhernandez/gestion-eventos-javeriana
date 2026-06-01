package com.javeriana.eventos.inscription.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javeriana.eventos.inscription.domain.port.in.ConfirmarInscripcionUseCase;
import com.javeriana.eventos.inscription.domain.port.out.MensajeProcesadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de PagoConfirmadoConsumer.
 *
 * Cubre los 5 escenarios obligatorios del hallazgo M-02:
 *  1. Mensaje duplicado → ignorado (idempotencia)
 *  2. Payload sin inscripcionId → reject sin reintento (→ DLQ)
 *  3. Payload sin referenciaExterna → reject sin reintento (→ DLQ)
 *  4. Procesamiento exitoso → registra idempotencia
 *  5. Caso de uso falla → NO registra idempotencia (TX rollback semántico)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PagoConfirmadoConsumer — Idempotencia y validación defensiva")
class PagoConfirmadoConsumerTest {

    @Mock private ConfirmarInscripcionUseCase  confirmarInscripcion;
    @Mock private MensajeProcesadoRepository   mensajeProcesado;

    private PagoConfirmadoConsumer consumer;
    private ObjectMapper objectMapper;

    private static final UUID   INSCRIPCION_ID     = UUID.randomUUID();
    private static final String REFERENCIA_EXTERNA = "REF-PAGO-TEST-001";
    private static final String MESSAGE_ID         = UUID.randomUUID().toString();
    private static final String CORRELATION_ID     = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new PagoConfirmadoConsumer(
            confirmarInscripcion, mensajeProcesado, objectMapper);
    }

    private String payloadValido() {
        return String.format("""
            {
              "inscripcionId":    "%s",
              "referenciaExterna": "%s",
              "monto":            150000.00,
              "moneda":           "COP"
            }
            """, INSCRIPCION_ID, REFERENCIA_EXTERNA);
    }

    // ─── 1. Idempotencia ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotencia por messageId")
    class Idempotencia {

        @Test
        @DisplayName("Mensaje duplicado es ignorado sin invocar el caso de uso")
        void debeIgnorarMensajeDuplicadoSiYaFueProcesado() {
            when(mensajeProcesado.estaProcesado(MESSAGE_ID,
                    PagoConfirmadoConsumer.CONSUMER_GRUPO))
                .thenReturn(true);

            assertThatNoException()
                .isThrownBy(() -> consumer.onPagoConfirmado(
                    payloadValido(), MESSAGE_ID, null, CORRELATION_ID));

            verify(confirmarInscripcion, never()).confirmar(any(), any());
            verify(mensajeProcesado, never()).registrarProcesado(any(), any(), any());
        }

        @Test
        @DisplayName("Mensaje nuevo es procesado y registrado")
        void debeRegistrarMensajeProcesadoTrasEjecucionExitosa() throws Exception {
            when(mensajeProcesado.estaProcesado(MESSAGE_ID,
                    PagoConfirmadoConsumer.CONSUMER_GRUPO))
                .thenReturn(false);
            doNothing().when(confirmarInscripcion).confirmar(any(), any());

            consumer.onPagoConfirmado(payloadValido(), MESSAGE_ID, null, CORRELATION_ID);

            verify(confirmarInscripcion).confirmar(INSCRIPCION_ID, REFERENCIA_EXTERNA);
            verify(mensajeProcesado).registrarProcesado(
                MESSAGE_ID,
                PagoConfirmadoConsumer.CONSUMER_GRUPO,
                PagoConfirmadoConsumer.TIPO_MENSAJE);
        }

        @Test
        @DisplayName("Sin messageId → procesa pero NO registra idempotencia")
        void sinMessageIdProcesaSinRegistrarIdempotencia() throws Exception {
            doNothing().when(confirmarInscripcion).confirmar(any(), any());

            consumer.onPagoConfirmado(payloadValido(), null, null, CORRELATION_ID);

            verify(confirmarInscripcion).confirmar(INSCRIPCION_ID, REFERENCIA_EXTERNA);
            verify(mensajeProcesado, never()).registrarProcesado(any(), any(), any());
            verify(mensajeProcesado, never()).estaProcesado(any(), any());
        }
    }

    // ─── 2. Validación defensiva ─────────────────────────────────────────────

    @Nested
    @DisplayName("Validación defensiva de payload")
    class ValidacionDefensiva {

        @BeforeEach
        void configurarNuevoMensaje() {
            when(mensajeProcesado.estaProcesado(any(), any())).thenReturn(false);
        }

        @Test
        @DisplayName("Payload sin inscripcionId → AmqpRejectAndDontRequeueException (→ DLQ)")
        void debeLanzarRejectSiPayloadNoTieneInscripcionId() {
            String payloadSinId = """
                { "referenciaExterna": "REF-001", "monto": 100000 }
                """;

            assertThatThrownBy(() ->
                consumer.onPagoConfirmado(payloadSinId, MESSAGE_ID, null, CORRELATION_ID))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("inscripcionId");

            verify(confirmarInscripcion, never()).confirmar(any(), any());
        }

        @Test
        @DisplayName("Payload sin referenciaExterna → AmqpRejectAndDontRequeueException (→ DLQ)")
        void debeLanzarRejectSiPayloadNoTieneReferenciaExterna() {
            String payloadSinReferencia = String.format("""
                { "inscripcionId": "%s" }
                """, INSCRIPCION_ID);

            assertThatThrownBy(() ->
                consumer.onPagoConfirmado(payloadSinReferencia, MESSAGE_ID, null, CORRELATION_ID))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("referenciaExterna");

            verify(confirmarInscripcion, never()).confirmar(any(), any());
        }

        @Test
        @DisplayName("JSON malformado → AmqpRejectAndDontRequeueException (→ DLQ)")
        void debeLanzarRejectSiJsonEsMalformado() {
            String jsonRoto = "{ esto no es json válido }";

            assertThatThrownBy(() ->
                consumer.onPagoConfirmado(jsonRoto, MESSAGE_ID, null, CORRELATION_ID))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

            verify(confirmarInscripcion, never()).confirmar(any(), any());
        }

        @Test
        @DisplayName("inscripcionId con UUID malformado → AmqpRejectAndDontRequeueException")
        void debeLanzarRejectSiInscripcionIdNoEsUuid() {
            String payloadUuidMalo = """
                { "inscripcionId": "no-es-uuid", "referenciaExterna": "REF-001" }
                """;

            assertThatThrownBy(() ->
                consumer.onPagoConfirmado(payloadUuidMalo, MESSAGE_ID, null, CORRELATION_ID))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }
    }

    // ─── 3. Manejo de fallos del caso de uso ─────────────────────────────────

    @Nested
    @DisplayName("Fallos del caso de uso")
    class FallosCasoDeUso {

        @BeforeEach
        void configurarNuevoMensaje() {
            when(mensajeProcesado.estaProcesado(any(), any())).thenReturn(false);
        }

        @Test
        @DisplayName("Si confirmar() lanza RuntimeException → NO registra idempotencia")
        void debeNoRegistrarSiCasoDeUsoFalla() {
            doThrow(new RuntimeException("DB temporalmente no disponible"))
                .when(confirmarInscripcion).confirmar(any(), any());

            assertThatThrownBy(() ->
                consumer.onPagoConfirmado(payloadValido(), MESSAGE_ID, null, CORRELATION_ID))
                .isInstanceOf(RuntimeException.class);

            // Idempotencia NO registrada → próximo reintento lo procesará de nuevo
            verify(mensajeProcesado, never()).registrarProcesado(any(), any(), any());
        }

        @Test
        @DisplayName("Si confirmar() lanza RuntimeException → relanza para reintento por Spring AMQP")
        void debeRelanzarParaQueSpringAmqpReintente() {
            RuntimeException errorTransitorio = new RuntimeException("Timeout BD");
            doThrow(errorTransitorio)
                .when(confirmarInscripcion).confirmar(any(), any());

            assertThatThrownBy(() ->
                consumer.onPagoConfirmado(payloadValido(), MESSAGE_ID, null, CORRELATION_ID))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(AmqpRejectAndDontRequeueException.class);
        }
    }
}
