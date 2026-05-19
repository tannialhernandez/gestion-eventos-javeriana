package com.javeriana.eventos.payment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.payment.domain.model.Pago;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase;
import com.javeriana.eventos.payment.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.payment.domain.port.out.PagoRepository;
import com.javeriana.eventos.payment.domain.port.out.PasarelaPagoFactory;
import com.javeriana.eventos.payment.domain.port.out.PasarelaPagoPort;
import com.javeriana.eventos.shared.domain.DomainEvent;
import com.javeriana.eventos.shared.infrastructure.outbox.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Procesa webhooks de la pasarela de pago.
 *
 * Garantías (docs/comportamiento-runtime-inscripcion-pago.md §4, §5, §7):
 *
 * 1. IDEMPOTENCIA (RN-13): verifica referencia_externa antes de procesar.
 *    Si el pago ya está en estado final → DUPLICADO sin reprocessing.
 *
 * 2. PAGO TARDÍO (RN-10): si el webhook llega tras (window + grace), la
 *    inscripción ya expiró. Se emite reembolso y PagoReembolsadoEvent al outbox.
 *    Ventana configurable en application.yml (payment.expiration.*).
 *
 * 3. OUTBOX PATTERN (ADR-11): eventos persistidos en la misma transacción.
 *    OutboxRelayService los publica a RabbitMQ de forma asíncrona.
 *
 * 4. FACTORY METHOD (ADR-Factory): PasarelaPagoFactory resuelve el adaptador
 *    concreto por config, sin acoplamiento a MercadoPago ni Simulador aquí.
 */
@Service
@Transactional
public class ProcesarWebhookService implements ProcesarWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcesarWebhookService.class);

    // RN-10 (SRS §9.3.3): deadline pago = fecha_inscripcion + 15 minutos.
    // grace-period: margen para la latencia entre el job de expiración de
    // inscription-service (cada 60s) y la llegada del webhook.
    // Ver docs/follow-ups/payment-refund-async.md para la decisión completa.
    @Value("${payment.expiration.window-seconds:900}")
    private int expirationWindowSeconds;

    @Value("${payment.expiration.grace-period-seconds:60}")
    private int gracePeriodSeconds;

    private final PagoRepository pagoRepository;
    private final PasarelaPagoFactory pasarelaFactory;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ProcesarWebhookService(PagoRepository pagoRepository,
                                   PasarelaPagoFactory pasarelaFactory,
                                   OutboxEventRepository outboxRepository,
                                   ObjectMapper objectMapper) {
        this.pagoRepository = pagoRepository;
        this.pasarelaFactory = pasarelaFactory;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResultadoWebhook procesar(WebhookPayload payload) {

        // Factory Method — adaptador resuelto por config, no hardcodeado
        PasarelaPagoPort pasarela = pasarelaFactory.crearPasarela();

        // ── Paso 1: Idempotencia (RN-13) ─────────────────────────────────────
        Optional<Pago> pagoExistente =
            pagoRepository.buscarPorReferenciaExterna(payload.referenciaExterna());

        if (pagoExistente.isPresent()
                && pagoExistente.get().getEstado().esFinal()) {
            log.info("[webhook] Duplicado: referencia={} estado={} → ignorar.",
                payload.referenciaExterna(), pagoExistente.get().getEstado());
            return ResultadoWebhook.DUPLICADO;
        }

        // ── Paso 2: Buscar pago por inscripción ───────────────────────────────
        UUID inscripcionId = UUID.fromString(payload.inscripcionId());
        Pago pago = pagoRepository.buscarPorInscripcionId(inscripcionId)
            .orElseThrow(() -> new IllegalArgumentException(
                "No existe pago para inscripción: " + inscripcionId));

        // ── Paso 3: Procesar según estado de la pasarela ─────────────────────
        return switch (payload.estado()) {
            case "approved" -> procesarAprobado(pago, payload, pasarela);
            case "rejected" -> procesarRechazado(pago, payload);
            default -> {
                log.warn("[webhook] Estado desconocido: {} para inscripcionId={}",
                    payload.estado(), inscripcionId);
                yield ResultadoWebhook.RECHAZADO;
            }
        };
    }

    private ResultadoWebhook procesarAprobado(Pago pago,
                                               WebhookPayload payload,
                                               PasarelaPagoPort pasarela) {
        // RN-10: detectar pago tardío por tiempo transcurrido desde la creación
        Instant deadline = pago.getFechaCreacion()
            .plusSeconds((long) expirationWindowSeconds + gracePeriodSeconds);

        if (Instant.now().isAfter(deadline)) {
            return procesarPagoTardio(pago, payload, pasarela);
        }

        // ── Camino feliz: confirmar ───────────────────────────────────────────
        pago.confirmar(payload.referenciaExterna(), payload.metadatosJson());
        pagoRepository.guardar(pago);

        for (DomainEvent event : pago.pullDomainEvents()) {
            outboxRepository.guardar(crearOutboxEvent(event, Map.of(
                "inscripcionId", pago.getInscripcionId().toString(),
                "referenciaExterna", payload.referenciaExterna()
            )));
        }

        log.info("[webhook] Pago {} CONFIRMADO para inscripción {}.",
            pago.getId(), pago.getInscripcionId());

        return ResultadoWebhook.CONFIRMADO;
    }

    private ResultadoWebhook procesarPagoTardio(Pago pago,
                                                 WebhookPayload payload,
                                                 PasarelaPagoPort pasarela) {
        log.warn("[webhook] Pago TARDÍO: inscripcion={} creado={} deadline superado ({}s+{}s). Reembolsando.",
            pago.getInscripcionId(), pago.getFechaCreacion(),
            expirationWindowSeconds, gracePeriodSeconds);

        // TODO[DEUDA-TÉCNICA]: pasarela.reembolsar() se llama ANTES del COMMIT de BD.
        // Si la BD falla tras un reembolso exitoso en pasarela → estado divergente.
        // Solución correcta: reembolso disparado por consumer del PagoReembolsadoEvent.
        // Ver docs/follow-ups/payment-refund-async.md para diseño y análisis completo.
        pasarela.reembolsar(payload.referenciaExterna(), pago.getMonto());

        // RN-PAGO-05 (propuesta): desde INICIADO/PROCESANDO → REEMBOLSADO
        pago.reembolsarPorExpiracion();
        pagoRepository.guardar(pago);

        for (DomainEvent event : pago.pullDomainEvents()) {
            outboxRepository.guardar(crearOutboxEvent(event, Map.of(
                "inscripcionId", pago.getInscripcionId().toString(),
                "referenciaExterna", payload.referenciaExterna()
            )));
        }

        log.info("[webhook] Reembolso emitido: pago={} inscripcion={}.",
            pago.getId(), pago.getInscripcionId());

        return ResultadoWebhook.INSCRIPCION_EXPIRADA;
    }

    private ResultadoWebhook procesarRechazado(Pago pago, WebhookPayload payload) {
        pago.marcarFallido("Rechazado por pasarela");
        pagoRepository.guardar(pago);

        log.warn("[webhook] Pago {} RECHAZADO para inscripción {}.",
            pago.getId(), pago.getInscripcionId());

        return ResultadoWebhook.RECHAZADO;
    }

    private OutboxEvent crearOutboxEvent(DomainEvent event, Map<String, String> extras) {
        try {
            Map<String, Object> payload = Map.of(
                "eventId",           event.eventId().toString(),
                "aggregateId",       event.aggregateId().toString(),
                "eventType",         event.eventType(),
                "occurredAt",        event.occurredAt().toString(),
                "inscripcionId",     extras.getOrDefault("inscripcionId", ""),
                "referenciaExterna", extras.getOrDefault("referenciaExterna", "")
            );
            String json = objectMapper.writeValueAsString(payload);
            return new OutboxEvent(event.aggregateId(), event.eventType(), json);
        } catch (Exception e) {
            throw new RuntimeException("Error serializando evento de dominio", e);
        }
    }
}
