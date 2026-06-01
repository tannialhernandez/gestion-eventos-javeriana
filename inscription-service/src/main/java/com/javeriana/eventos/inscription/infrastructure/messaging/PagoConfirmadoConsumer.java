package com.javeriana.eventos.inscription.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.inscription.domain.port.in.ConfirmarInscripcionUseCase;
import com.javeriana.eventos.inscription.domain.port.out.MensajeProcesadoRepository;
import com.javeriana.eventos.inscription.infrastructure.observability.MdcKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Escucha la queue "pago.confirmado" y confirma inscripciones.
 *
 * Garantías implementadas:
 *
 *  1. Idempotencia por messageId (ADR-009, hallazgo M-02):
 *     Si RabbitMQ reentrega el mismo mensaje (at-least-once), el segundo intento
 *     es ignorado silenciosamente usando la tabla mensaje_procesado.
 *
 *  2. Validación defensiva (hallazgo M-02):
 *     Si el payload no tiene inscripcionId o referenciaExterna, el mensaje se
 *     rechaza con AmqpRejectAndDontRequeueException → va directo a DLQ sin
 *     consumir los 3 reintentos (dato estructuralmente inválido nunca sanará).
 *
 *  3. DLQ en código (ADR-019, hallazgo M-05):
 *     La cola pago.confirmado y su DLQ están declaradas como @Bean en
 *     RabbitMQConfig; no dependen de definitions.json externo.
 *
 *  4. Atomicidad con el caso de uso:
 *     @Transactional garantiza que registrarProcesado() y confirmar() comparten
 *     la misma transacción. Si la confirmación falla → rollback → el registro
 *     de idempotencia también se revierte → próxima entrega lo reintentará.
 *
 *  5. MDC con correlationId y messageId para trazabilidad E2E (RNF-16).
 */
@Component
public class PagoConfirmadoConsumer {

    private static final Logger log = LoggerFactory.getLogger(PagoConfirmadoConsumer.class);

    static final String CONSUMER_GRUPO = "confirmar-inscripcion";
    static final String TIPO_MENSAJE   = "PAGO_CONFIRMADO";

    private final ConfirmarInscripcionUseCase confirmarInscripcion;
    private final MensajeProcesadoRepository  mensajeProcesadoRepository;
    private final ObjectMapper                objectMapper;

    public PagoConfirmadoConsumer(ConfirmarInscripcionUseCase confirmarInscripcion,
                                   MensajeProcesadoRepository mensajeProcesadoRepository,
                                   ObjectMapper objectMapper) {
        this.confirmarInscripcion     = confirmarInscripcion;
        this.mensajeProcesadoRepository = mensajeProcesadoRepository;
        this.objectMapper             = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PAGO)
    @Transactional
    public void onPagoConfirmado(
            String mensajeJson,
            @Header(value = AmqpHeaders.MESSAGE_ID,       required = false) String messageId,
            @Header(value = MdcKeys.AMQP_HEADER,          required = false) String amqpCorrelationId,
            @Header(value = "X-Correlation-Id",            required = false) String httpCorrelationId) {

        // ── Contexto de trazabilidad (m-02): priorizar correlationId del mensaje AMQP
        String corrId = amqpCorrelationId != null ? amqpCorrelationId
                      : httpCorrelationId != null  ? httpCorrelationId
                      : UUID.randomUUID().toString();

        MDC.put(MdcKeys.CORRELATION_ID, corrId);
        MDC.put(MdcKeys.MESSAGE_ID,     messageId != null ? messageId : "sin-id");

        try {
            // ── 1. Idempotencia ───────────────────────────────────────────────
            if (messageId != null
                    && mensajeProcesadoRepository.estaProcesado(messageId, CONSUMER_GRUPO)) {
                log.info("[pago-consumer] Mensaje duplicado ignorado: messageId={}, corrId={}",
                    messageId, corrId);
                return;
            }

            // ── 2. Validación defensiva del payload ───────────────────────────
            JsonNode payload = parsearPayload(mensajeJson);

            JsonNode inscripcionNode = payload.get("inscripcionId");
            JsonNode referenciaNode  = payload.get("referenciaExterna");

            if (inscripcionNode == null || inscripcionNode.isNull()) {
                log.error("[pago-consumer] Payload sin inscripcionId — rechazando sin reintento");
                throw new AmqpRejectAndDontRequeueException(
                    "Payload pago.confirmado sin inscripcionId → DLQ");
            }
            if (referenciaNode == null || referenciaNode.isNull()) {
                log.error("[pago-consumer] Payload sin referenciaExterna — rechazando sin reintento");
                throw new AmqpRejectAndDontRequeueException(
                    "Payload pago.confirmado sin referenciaExterna → DLQ");
            }

            UUID   inscripcionId     = UUID.fromString(inscripcionNode.asText());
            String referenciaExterna = referenciaNode.asText();

            // ── 3. Ejecutar caso de uso ───────────────────────────────────────
            log.info("[pago-consumer] Confirmando inscripción: inscripcionId={}", inscripcionId);
            confirmarInscripcion.confirmar(inscripcionId, referenciaExterna);

            // ── 4. Registrar idempotencia (dentro de la misma TX) ─────────────
            if (messageId != null) {
                mensajeProcesadoRepository.registrarProcesado(
                    messageId, CONSUMER_GRUPO, TIPO_MENSAJE);
            }

        } catch (AmqpRejectAndDontRequeueException e) {
            throw e;  // ya loqueado y etiquetado — re-lanzar para que vaya a DLQ

        } catch (IllegalArgumentException e) {
            // UUID malformado u otro error de datos → no tiene sentido reintentar
            log.error("[pago-consumer] Dato inválido, rechazando sin reintento: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException(
                "Dato inválido en pago.confirmado → DLQ", e);

        } catch (Exception e) {
            // Error transitorio (DB flap, timeout) → Spring retintentará (max 3)
            log.error("[pago-consumer] Error procesando pago.confirmado messageId={}: {}",
                messageId, e.getMessage(), e);
            throw new RuntimeException("Error procesando pago.confirmado", e);

        } finally {
            MDC.clear();
        }
    }

    // ─── Helper privado ───────────────────────────────────────────────────────

    private JsonNode parsearPayload(String mensajeJson) {
        try {
            return objectMapper.readTree(mensajeJson);
        } catch (Exception e) {
            log.error("[pago-consumer] JSON inválido — rechazando sin reintento: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException(
                "JSON malformado en pago.confirmado → DLQ", e);
        }
    }
}
