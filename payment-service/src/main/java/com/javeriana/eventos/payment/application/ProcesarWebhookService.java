package com.javeriana.eventos.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.payment.application.observer.PagoAuditObserver;
import com.javeriana.eventos.payment.domain.events.SerializacionPayloadException;
import com.javeriana.eventos.payment.domain.model.EstadoPago;
import com.javeriana.eventos.payment.domain.model.Pago;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase;
import com.javeriana.eventos.payment.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.payment.domain.port.out.PagoRepository;
import com.javeriana.eventos.payment.domain.port.out.PasarelaPagoFactory;
import com.javeriana.eventos.payment.domain.port.out.PasarelaPagoPort;
import com.javeriana.eventos.shared.domain.DomainEvent;
import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Procesa webhooks de la pasarela de pago.
 *
 * Garantías:
 * 1. IDEMPOTENCIA (RN-13): verifica referencia_externa antes de procesar.
 * 2. PAGO TARDÍO (RN-10): reembolso + PagoReembolsadoEvent al outbox.
 * 3. OUTBOX PATTERN (ADR-11): eventos en la misma TX (aprobado, tardío, rechazado).
 * 4. FACTORY METHOD: PasarelaPagoFactory resuelve el adaptador concreto.
 *
 * Hallazgo #6 CERRADO: payload del evento ahora incluye monto y moneda.
 * Hallazgo #7 CERRADO: procesarRechazado ahora emite PagoFallidoEvent al outbox.
 */
@Service
@Transactional
public class ProcesarWebhookService implements ProcesarWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcesarWebhookService.class);

    @Value("${payment.expiration.window-seconds:900}")
    private int expirationWindowSeconds;

    @Value("${payment.expiration.grace-period-seconds:60}")
    private int gracePeriodSeconds;

    private final PagoRepository pagoRepository;
    private final PasarelaPagoFactory pasarelaFactory;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final PagoAuditObserver auditObserver;

    public ProcesarWebhookService(PagoRepository pagoRepository,
                                   PasarelaPagoFactory pasarelaFactory,
                                   OutboxEventRepository outboxRepository,
                                   ObjectMapper objectMapper,
                                   PagoAuditObserver auditObserver) {
        this.pagoRepository = pagoRepository;
        this.pasarelaFactory = pasarelaFactory;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.auditObserver = auditObserver;
    }

    @Override
    public ResultadoWebhook procesar(WebhookPayload payload) {

        PasarelaPagoPort pasarela = pasarelaFactory.crearPasarela();

        // ── Idempotencia (RN-13) ──────────────────────────────────────────────
        Optional<Pago> pagoExistente =
            pagoRepository.buscarPorReferenciaExterna(payload.referenciaExterna());

        if (pagoExistente.isPresent()
                && pagoExistente.get().getEstado().esFinal()) {
            log.info("[webhook] Duplicado: referencia={} estado={} → ignorar.",
                payload.referenciaExterna(), pagoExistente.get().getEstado());
            return ResultadoWebhook.DUPLICADO;
        }

        // ── Buscar pago por inscripción ───────────────────────────────────────
        UUID inscripcionId = UUID.fromString(payload.inscripcionId());
        Pago pago = pagoRepository.buscarPorInscripcionId(inscripcionId)
            .orElseThrow(() -> new IllegalArgumentException(
                "No existe pago para inscripción: " + inscripcionId));

        // ── Procesar según estado de la pasarela ─────────────────────────────
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
        Instant deadline = pago.getFechaCreacion()
            .plusSeconds((long) expirationWindowSeconds + gracePeriodSeconds);

        if (Instant.now().isAfter(deadline)) {
            return procesarPagoTardio(pago, payload, pasarela);
        }

        EstadoPago estadoAnterior = pago.getEstado();
        pago.confirmar(payload.referenciaExterna(), payload.metadatosJson());
        pagoRepository.guardar(pago);
        auditObserver.registrarTransicion(pago, estadoAnterior.name(),
            "PASARELA:" + pago.getPasarela(), "Pago aprobado por pasarela");

        for (DomainEvent event : pago.pullDomainEvents()) {
            outboxRepository.guardar(crearOutboxEvent(event));
        }

        log.info("[webhook] Pago {} CONFIRMADO para inscripción {}.",
            pago.getId(), pago.getInscripcionId());

        return ResultadoWebhook.CONFIRMADO;
    }

    private ResultadoWebhook procesarPagoTardio(Pago pago,
                                                 WebhookPayload payload,
                                                 PasarelaPagoPort pasarela) {
        log.warn("[webhook] Pago TARDIO: inscripcion={} creado={} deadline superado ({}s+{}s). Reembolsando.",
            pago.getInscripcionId(), pago.getFechaCreacion(),
            expirationWindowSeconds, gracePeriodSeconds);

        // TODO[DEUDA-TECNICA]: pasarela.reembolsar() se llama ANTES del COMMIT de BD.
        // Solicion correcta: reembolso disparado por consumer del PagoReembolsadoEvent.
        pasarela.reembolsar(payload.referenciaExterna(), pago.getMonto());

        EstadoPago estadoAnterior = pago.getEstado();
        pago.reembolsarPorExpiracion();
        pagoRepository.guardar(pago);
        auditObserver.registrarTransicion(pago, estadoAnterior.name(),
            "SISTEMA", "Reembolso automatico por expiracion de inscripcion");

        for (DomainEvent event : pago.pullDomainEvents()) {
            outboxRepository.guardar(crearOutboxEvent(event));
        }

        log.info("[webhook] Reembolso emitido: pago={} inscripcion={}.",
            pago.getId(), pago.getInscripcionId());

        return ResultadoWebhook.INSCRIPCION_EXPIRADA;
    }

    private ResultadoWebhook procesarRechazado(Pago pago, WebhookPayload payload) {
        EstadoPago estadoAnterior = pago.getEstado();
        pago.marcarFallido("RECHAZADO_POR_PASARELA");
        pagoRepository.guardar(pago);
        auditObserver.registrarTransicion(pago, estadoAnterior.name(),
            "PASARELA", "Pago rechazado por la pasarela");

        for (DomainEvent event : pago.pullDomainEvents()) {
            outboxRepository.guardar(crearOutboxEvent(event));
        }

        log.warn("[webhook] Pago {} RECHAZADO para inscripcion {}.",
            pago.getId(), pago.getInscripcionId());

        return ResultadoWebhook.RECHAZADO;
    }

    /**
     * Serializa el DomainEvent completo (record) a JSON.
     *
     * El record es la fuente de verdad del payload: si se agrega un campo al
     * evento, el JSON lo refleja automáticamente sin cambios aquí.
     * Jackson serializa los componentes del record por nombre.
     */
    private OutboxEvent crearOutboxEvent(DomainEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            return new OutboxEvent("Pago", event.aggregateId(), event.eventType(), json);
        } catch (JsonProcessingException e) {
            throw new SerializacionPayloadException(
                "Error serializando evento " + event.eventType(), e);
        }
    }
}
