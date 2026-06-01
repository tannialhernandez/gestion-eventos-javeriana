package com.javeriana.eventos.event.infrastructure.outbox;

import com.javeriana.eventos.event.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.event.infrastructure.observability.MdcKeys;
import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;
import com.javeriana.eventos.shared.domain.outbox.PublicacionException;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
 * Outbox Relay — publica eventos pendientes de event-service a RabbitMQ.
 *
 * Outbox Pattern (ADR-011) con Publisher Confirms:
 *  1. Lee lote PENDIENTE con SELECT FOR UPDATE SKIP LOCKED (ADR-012).
 *  2. Publica con CorrelationData y espera ACK del broker (5s max).
 *  3. ACK  -> marcarProcesado().
 *  4. NACK / timeout -> incrementarIntentos(); si >= MAX_INTENTOS -> marcarFallido().
 *
 * ShedLock previene ejecuciones paralelas en deploys multi-instancia.
 * Headers publicados:
 *   - eventType         (ADR-014)
 *   - aggregateType / aggregateId
 *   - x-schema-version: v1  (ADR-020)
 *   - x-correlation-id       (Prompt 13)
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

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:5000}")
    @SchedulerLock(name = "event-service-outbox-relay",
                   lockAtMostFor = "PT30S", lockAtLeastFor = "PT4S")
    @Transactional
    public void publicarEventosPendientes() {
        List<OutboxEvent> pendientes = outboxRepository.buscarNoPublicados(BATCH_SIZE);

        if (pendientes.isEmpty()) {
            return;
        }

        metricas.registrarPolling(pendientes.size());
        log.debug("[event-outbox] Procesando {} eventos pendientes.", pendientes.size());

        for (OutboxEvent evento : pendientes) {
            MDC.put("eventId",       evento.getId().toString());
            MDC.put("aggregateType", evento.getAggregateType());
            MDC.put("aggregateId",   evento.getAggregateId().toString());
            try {
                publicarConConfirmacion(evento);
                outboxRepository.marcarProcesado(evento.getId());
                metricas.registrarPublicacionExitosa();
                log.info("[event-outbox] Evento publicado: type={} aggregate={}",
                    evento.getEventType(), evento.getAggregateId());
            } catch (Exception e) {
                gestionarFallo(evento, e);
            } finally {
                MDC.remove("eventId");
                MDC.remove("aggregateType");
                MDC.remove("aggregateId");
            }
        }
    }

    private void publicarConConfirmacion(OutboxEvent evento) throws PublicacionException {
        String routingKey = construirRoutingKey(evento.getEventType());

        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        if (correlationId == null) correlationId = evento.getId().toString();

        Message mensaje = MessageBuilder
            .withBody(evento.getPayload().getBytes(StandardCharsets.UTF_8))
            .setMessageId(evento.getId().toString())
            .setContentType("application/json")
            .setHeader("eventType",         evento.getEventType())
            .setHeader("aggregateType",     evento.getAggregateType())
            .setHeader("aggregateId",       evento.getAggregateId().toString())
            .setHeader(MdcKeys.AMQP_HEADER, correlationId)
            .setHeader("x-schema-version",  "v1")
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
                "Timeout esperando confirmacion del broker para evento " + evento.getId(), e);
        } catch (PublicacionException e) {
            throw e;
        } catch (Exception e) {
            throw new PublicacionException(
                "Error inesperado esperando confirmacion del broker", e);
        }
    }

    private void gestionarFallo(OutboxEvent evento, Exception e) {
        outboxRepository.incrementarIntentos(evento.getId());
        int intentosActualizados = evento.getIntentos() + 1;

        if (intentosActualizados >= MAX_INTENTOS) {
            outboxRepository.marcarFallido(evento.getId());
            metricas.registrarEventoFallidoDefinitivo();
            log.error("[event-outbox] Evento marcado FALLIDO tras {} intentos: {}",
                MAX_INTENTOS, e.getMessage());
        } else {
            metricas.registrarFalloPublicacion();
            log.warn("[event-outbox] Fallo publicacion intento {}/{}: {}. Se reintentara.",
                intentosActualizados, MAX_INTENTOS, e.getMessage());
        }
    }

    private String construirRoutingKey(String eventType) {
        return eventType.toLowerCase().replace('_', '.');
    }
}
