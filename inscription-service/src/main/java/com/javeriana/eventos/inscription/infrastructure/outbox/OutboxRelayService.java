package com.javeriana.eventos.inscription.infrastructure.outbox;

import com.javeriana.eventos.shared.infrastructure.outbox.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox Relay — publica eventos pendientes a RabbitMQ cada 5 segundos.
 *
 * Outbox Pattern (ADR-11):
 *  Los eventos de dominio se persisten en outbox_events dentro de la misma
 *  transacción de negocio. Este relay los lee y publica en RabbitMQ de
 *  forma asíncrona, garantizando que:
 *
 *  - Si el servidor cae después del COMMIT pero antes de publicar:
 *    → Al reiniciar, el relay encuentra los eventos pendientes y los publica.
 *  - Si RabbitMQ está caído:
 *    → Los eventos permanecen en outbox_events hasta que RabbitMQ se recupere.
 *  - Exactamente-una-vez en la BD (publicado=true es idempotente).
 *  - Al-menos-una-vez en RabbitMQ (los consumers deben ser idempotentes).
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

    @Scheduled(fixedDelay = 5000)   // Cada 5 segundos
    @Transactional
    public void publicarEventosPendientes() {
        List<OutboxEventEntity> pendientes = outboxRepo.findPendingEvents();

        if (pendientes.isEmpty()) {
            return;
        }

        log.debug("OutboxRelay: publicando {} eventos pendientes.", pendientes.size());

        for (OutboxEventEntity evento : pendientes) {
            try {
                // Publicar en RabbitMQ con routing key = eventType en minúsculas
                // Ej: INSCRIPCION_CONFIRMADA → inscripcion.confirmada
                String routingKey = evento.getEventType().toLowerCase().replace('_', '.');

                rabbitTemplate.convertAndSend(EXCHANGE, routingKey, evento.getPayload());

                // Marcar como publicado solo si RabbitMQ lo aceptó (no lanzó excepción)
                evento.setPublished(true);
                evento.setPublishedAt(java.time.Instant.now());
                outboxRepo.save(evento);

                log.debug("Evento {} publicado en {}/{}",
                    evento.getId(), EXCHANGE, routingKey);

            } catch (Exception e) {
                log.error("Error publicando evento {}: {}. Se reintentará en el próximo ciclo.",
                    evento.getId(), e.getMessage());
                // No marcar como publicado → el próximo ciclo lo reintentará
            }
        }
    }
}
