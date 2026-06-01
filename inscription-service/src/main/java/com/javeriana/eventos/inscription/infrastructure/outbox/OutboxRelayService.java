package com.javeriana.eventos.inscription.infrastructure.outbox;

import com.javeriana.eventos.inscription.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.inscription.infrastructure.observability.MdcKeys;
import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;
import com.javeriana.eventos.shared.domain.outbox.PublicacionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Outbox Relay — publica eventos pendientes de inscription-service a RabbitMQ cada 5 segundos.
 *
 * Outbox Pattern (ADR-011) con Publisher Confirms:
 *  1. Lee lote PENDIENTE con SELECT FOR UPDATE SKIP LOCKED (ADR-012).
 *  2. Publica con CorrelationData y espera ACK del broker (≤5s).
 *  3. ACK  → marcarProcesado().
 *  4. NACK / timeout → incrementarIntentos(); si >= MAX_INTENTOS → marcarFallido().
 */
@Component
public class OutboxRelayService {

    private static final int    BATCH_SIZE        = 50;
    private static final int    MAX_INTENTOS      = 5;
    private static final long   CONFIRM_TIMEOUT_S = 5L;
    private static final String EXCHANGE          = "eventos.topic";
    private static final Logger log               = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate        rabbitTemplate;
    private final OutboxMetrics         metricas;

    public OutboxRelayService(OutboxEventRepository outboxRepository,
                              RabbitTemplate rabbitTemplate,
                              OutboxMetrics metricas) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate   = rabbitTemplate;
        this.metricas         = metricas;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publicarEventosPendientes() {
        List<OutboxEvent> pendientes = outboxRepository.buscarNoPublicados(BATCH_SIZE);

        if (pendientes.isEmpty()) {
            return;
        }

        metricas.registrarPolling(pendientes.size());
        log.debug("[inscription-outbox] Procesando {} eventos pendientes.", pendientes.size());

        for (OutboxEvent evento : pendientes) {
            MDC.put("eventId",       evento.getId().toString());
            MDC.put("aggregateType", evento.getAggregateType());
            MDC.put("aggregateId",   evento.getAggregateId().toString());
            try {
                publicarConConfirmacion(evento);
                outboxRepository.marcarProcesado(evento.getId());
                metricas.registrarPublicacionExitosa();
                log.info("[inscription-outbox] Evento publicado y confirmado por el broker.");
            } catch (Exception e) {
                gestionarFallo(evento, e);
            } finally {
                MDC.clear();
            }
        }
    }

    private void publicarConConfirmacion(OutboxEvent evento) throws PublicacionException {
        String routingKey = construirRoutingKey(evento.getEventType());

        // En el relay (hilo scheduler), el correlationId es el propio eventId del outbox
        // para que los consumidores puedan rastrear el origen del evento
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        if (correlationId == null) correlationId = evento.getId().toString();

        Message mensaje = MessageBuilder
            .withBody(evento.getPayload().getBytes(StandardCharsets.UTF_8))
            .setMessageId(evento.getId().toString())
            .setContentType("application/json")
            .setHeader("eventType",          evento.getEventType())
            .setHeader("aggregateType",      evento.getAggregateType())
            .setHeader("aggregateId",        evento.getAggregateId().toString())
            .setHeader(MdcKeys.AMQP_HEADER,  correlationId)     // m-02: propagación E2E
            .setHeader("x-schema-version",   "v1")              // ADR-020: versionado
            .build();

        CorrelationData correlationData = new CorrelationData(evento.getId().toString());
        rabbitTemplate.send(EXCHANGE, routingKey, mensaje, correlationData);

        try {
            CorrelationData.Confirm confirm =
                correlationData.getFuture().get(CONFIRM_TIMEOUT_S, TimeUnit.SECONDS);

            if (!confirm.isAck()) {
                throw new PublicacionException(
                    "Broker NACK para evento " + evento.getId() + ": " + confirm.getReason());
            }
        } catch (TimeoutException e) {
            throw new PublicacionException(
                "Timeout esperando confirmación del broker para evento " + evento.getId(), e);
        } catch (PublicacionException e) {
            throw e;
        } catch (Exception e) {
            throw new PublicacionException(
                "Error inesperado esperando confirmación del broker", e);
        }
    }

    private void gestionarFallo(OutboxEvent evento, Exception e) {
        outboxRepository.incrementarIntentos(evento.getId());
        int intentosActualizados = evento.getIntentos() + 1;

        if (intentosActualizados >= MAX_INTENTOS) {
            outboxRepository.marcarFallido(evento.getId());
            metricas.registrarEventoFallidoDefinitivo();
            log.error("[inscription-outbox] Evento marcado FALLIDO tras {} intentos: {}",
                MAX_INTENTOS, e.getMessage());
        } else {
            metricas.registrarFalloPublicacion();
            log.error("[inscription-outbox] Fallo publicación intento {}/{}: {}. Se reintentará.",
                intentosActualizados, MAX_INTENTOS, e.getMessage());
        }
    }

    private String construirRoutingKey(String eventType) {
        return eventType.toLowerCase().replace('_', '.');
    }
}
