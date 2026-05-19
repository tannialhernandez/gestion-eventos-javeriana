package com.javeriana.eventos.payment.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox Relay — publica eventos pendientes de payment-service a RabbitMQ cada 2 segundos.
 *
 * Outbox Pattern (ADR-11 del SAD):
 *  Los eventos PagoConfirmado/PagoReembolsado se persisten en outbox_events dentro
 *  de la misma transacción de negocio (ProcesarWebhookService). Este relay los lee y
 *  publica en RabbitMQ de forma asíncrona, garantizando que:
 *
 *  - Si el servidor cae después del COMMIT pero antes de publicar:
 *    → Al reiniciar el relay encuentra los eventos pendientes y los publica.
 *  - Si RabbitMQ está caído:
 *    → Los eventos permanecen en outbox_events hasta que RabbitMQ se recupere.
 *  - Idempotencia en RabbitMQ: messageId = event UUID (consumidor debe deduplicar).
 *  - Exactly-once en BD (published=true), at-least-once en RabbitMQ.
 *
 * Routing keys (docs/comportamiento-runtime-inscripcion-pago.md §7):
 *  - PAGO_CONFIRMADO  → pago.confirmado  (inscription-service lo consume)
 *  - PAGO_REEMBOLSADO → pago.reembolsado
 */
@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    private static final String EXCHANGE = "eventos.topic";

    private final SpringDataOutboxRepository outboxRepo;
    private final RabbitTemplate rabbitTemplate;

    public OutboxRelayService(SpringDataOutboxRepository outboxRepo,
                              RabbitTemplate rabbitTemplate) {
        this.outboxRepo = outboxRepo;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publicarEventosPendientes() {
        List<OutboxEventEntity> pendientes = outboxRepo.findPendingEvents();

        if (pendientes.isEmpty()) {
            return;
        }

        log.debug("[payment-outbox] Publicando {} eventos pendientes.", pendientes.size());

        for (OutboxEventEntity evento : pendientes) {
            try {
                // PAGO_CONFIRMADO → pago.confirmado | PAGO_REEMBOLSADO → pago.reembolsado
                String routingKey = evento.getEventType().toLowerCase().replace('_', '.');

                // event_id como messageId → idempotencia en el consumidor (ADR-09)
                final String messageId = evento.getId().toString();
                rabbitTemplate.convertAndSend(EXCHANGE, routingKey, evento.getPayload(),
                    msg -> {
                        msg.getMessageProperties().setMessageId(messageId);
                        return msg;
                    });

                evento.setPublished(true);
                evento.setPublishedAt(Instant.now());
                outboxRepo.save(evento);

                log.info("[payment-outbox] Evento {} ({}) publicado en {}/{}",
                    evento.getId(), evento.getEventType(), EXCHANGE, routingKey);

            } catch (Exception e) {
                log.error("[payment-outbox] Error publicando evento {} ({}): {}. Se reintentará en el próximo ciclo.",
                    evento.getId(), evento.getEventType(), e.getMessage());
                // No marcar como publicado → el próximo tick lo reintenta
            }
        }
    }
}
