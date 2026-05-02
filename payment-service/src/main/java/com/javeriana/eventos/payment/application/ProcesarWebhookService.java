package com.javeriana.eventos.payment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.payment.domain.model.Pago;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase;
import com.javeriana.eventos.payment.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.payment.domain.port.out.PagoRepository;
import com.javeriana.eventos.payment.domain.port.out.PasarelaPagoPort;
import com.javeriana.eventos.shared.domain.DomainEvent;
import com.javeriana.eventos.shared.infrastructure.outbox.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Procesa webhooks de la pasarela de pago.
 *
 * Garantías implementadas (docs/comportamiento-runtime-inscripcion-pago.md §5, §4):
 *
 * 1. IDEMPOTENCIA (RN-03):
 *    Antes de procesar, se verifica si referencia_externa ya existe en BD.
 *    Si existe y está CONFIRMADO → retornar DUPLICADO sin reprocessing.
 *
 * 2. PAGO TARDÍO:
 *    Si la inscripción expiró (notification de inscription-service vía RabbitMQ),
 *    se emite reembolso automático a través de PasarelaPagoPort.
 *    → retornar INSCRIPCION_EXPIRADA
 *
 * 3. OUTBOX PATTERN:
 *    PagoConfirmado/PagoReembolsado se persisten en outbox_events dentro
 *    de la misma transacción. El relay los publica a inscription-service
 *    vía RabbitMQ.
 */
@Service
@Transactional
public class ProcesarWebhookService implements ProcesarWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcesarWebhookService.class);

    private final PagoRepository pagoRepository;
    private final PasarelaPagoPort pasarela;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ProcesarWebhookService(PagoRepository pagoRepository,
                                   PasarelaPagoPort pasarela,
                                   OutboxEventRepository outboxRepository,
                                   ObjectMapper objectMapper) {
        this.pagoRepository = pagoRepository;
        this.pasarela = pasarela;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResultadoWebhook procesar(WebhookPayload payload) {

        // ── Paso 1: Idempotencia ─────────────────────────────────────────────
        Optional<Pago> pagoExistente =
            pagoRepository.buscarPorReferenciaExterna(payload.referenciaExterna());

        if (pagoExistente.isPresent()
                && pagoExistente.get().getEstado().esFinal()) {
            log.info("Webhook duplicado para referencia {}. Ya procesado → ignorar.",
                payload.referenciaExterna());
            return ResultadoWebhook.DUPLICADO;
        }

        // ── Paso 2: Buscar el pago por inscripción ────────────────────────────
        UUID inscripcionId = UUID.fromString(payload.inscripcionId());
        Pago pago = pagoRepository.buscarPorInscripcionId(inscripcionId)
            .orElseThrow(() -> new IllegalArgumentException(
                "No existe pago para inscripción: " + inscripcionId));

        // ── Paso 3: Procesar según el estado reportado por la pasarela ────────
        return switch (payload.estado()) {
            case "approved" -> procesarAprobado(pago, payload);
            case "rejected" -> procesarRechazado(pago, payload);
            default -> {
                log.warn("Estado de webhook desconocido: {}", payload.estado());
                yield ResultadoWebhook.RECHAZADO;
            }
        };
    }

    private ResultadoWebhook procesarAprobado(Pago pago, WebhookPayload payload) {
        // Confirmar el pago en el agregado (registra PagoConfirmadoEvent)
        pago.confirmar(payload.referenciaExterna(), payload.metadatosJson());
        pagoRepository.guardar(pago);

        // Publicar evento vía Outbox → inscription-service lo consumirá
        for (DomainEvent event : pago.pullDomainEvents()) {
            outboxRepository.guardar(crearOutboxEvent(event, Map.of(
                "inscripcionId", pago.getInscripcionId().toString(),
                "referenciaExterna", payload.referenciaExterna()
            )));
        }

        log.info("Pago {} confirmado para inscripción {}.",
            pago.getId(), pago.getInscripcionId());

        return ResultadoWebhook.CONFIRMADO;
    }

    private ResultadoWebhook procesarRechazado(Pago pago, WebhookPayload payload) {
        pago.marcarFallido("Rechazado por pasarela");
        pagoRepository.guardar(pago);

        log.warn("Pago {} rechazado para inscripción {}.",
            pago.getId(), pago.getInscripcionId());

        return ResultadoWebhook.RECHAZADO;
    }

    private OutboxEvent crearOutboxEvent(DomainEvent event, Map<String, String> extras) {
        try {
            Map<String, Object> payload = Map.of(
                "eventId", event.eventId().toString(),
                "aggregateId", event.aggregateId().toString(),
                "eventType", event.eventType(),
                "occurredAt", event.occurredAt().toString(),
                "inscripcionId", extras.getOrDefault("inscripcionId", ""),
                "referenciaExterna", extras.getOrDefault("referenciaExterna", "")
            );
            String json = objectMapper.writeValueAsString(payload);
            return new OutboxEvent(event.aggregateId(), event.eventType(), json);
        } catch (Exception e) {
            throw new RuntimeException("Error serializando evento de dominio", e);
        }
    }
}
