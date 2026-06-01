package com.javeriana.eventos.inscription.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para inscription-service.
 *
 * Topología declarada programáticamente (ADR-019 — DLQ Strategy, M-05):
 *
 *   Producer (payment-service):
 *     → exchange: eventos.topic
 *     → routing key: pago.confirmado
 *     → queue: pago.confirmado (durable, con DLX configurado)
 *
 *   En caso de fallo reiterado (3 reintentos por spring.rabbitmq.listener.retry):
 *     → exchange DLX: eventos.topic.dlx
 *     → routing key: pago.confirmado.dead
 *     → queue DLQ: pago.confirmado.dlq (durable, para inspección manual)
 *
 * Al declarar las colas y exchanges como @Bean, el RabbitAdmin las crea
 * automáticamente en el broker al arrancar la aplicación. Esto elimina
 * la dependencia de definitions.json externo (hallazgo M-05).
 *
 * Publisher Confirms (ADR-011):
 *   Con publisher-confirm-type=correlated en application.yml, la ConnectionFactory
 *   ya está configurada para enviar ACK/NACK por mensaje.
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    // ─── Constantes de topología ──────────────────────────────────────────────

    public static final String EXCHANGE_EVENTOS   = "eventos.topic";
    public static final String EXCHANGE_DLX       = "eventos.topic.dlx";
    public static final String QUEUE_PAGO         = "pago.confirmado";
    public static final String QUEUE_PAGO_DLQ     = "pago.confirmado.dlq";
    public static final String ROUTING_PAGO       = "pago.confirmado";
    public static final String ROUTING_PAGO_DEAD  = "pago.confirmado.dead";

    // ─── Exchanges ────────────────────────────────────────────────────────────

    @Bean
    public TopicExchange eventosTopicExchange() {
        return new TopicExchange(EXCHANGE_EVENTOS, true, false);
    }

    /** Exchange de dead-letters: recibe mensajes fallidos de las queues configuradas. */
    @Bean
    public TopicExchange eventosDlxExchange() {
        return new TopicExchange(EXCHANGE_DLX, true, false);
    }

    // ─── Queues ───────────────────────────────────────────────────────────────

    /**
     * Cola principal: recibe PAGO_CONFIRMADO de payment-service.
     * Argumentos DLX: si un mensaje falla 3 veces (listener.retry.max-attempts),
     * Spring AMQP lo rechaza y RabbitMQ lo mueve automáticamente a la DLQ.
     */
    @Bean
    public Queue pagoConfirmadoQueue() {
        return QueueBuilder.durable(QUEUE_PAGO)
            .withArgument("x-dead-letter-exchange",    EXCHANGE_DLX)
            .withArgument("x-dead-letter-routing-key", ROUTING_PAGO_DEAD)
            .build();
    }

    /** Cola de dead-letters: almacena mensajes fallidos para inspección y reintento manual. */
    @Bean
    public Queue pagoConfirmadoDlq() {
        return QueueBuilder.durable(QUEUE_PAGO_DLQ).build();
    }

    // ─── Bindings ─────────────────────────────────────────────────────────────

    @Bean
    public Binding pagoConfirmadoBinding(Queue pagoConfirmadoQueue,
                                          TopicExchange eventosTopicExchange) {
        return BindingBuilder.bind(pagoConfirmadoQueue)
            .to(eventosTopicExchange)
            .with(ROUTING_PAGO);
    }

    @Bean
    public Binding pagoConfirmadoDlqBinding(Queue pagoConfirmadoDlq,
                                             TopicExchange eventosDlxExchange) {
        return BindingBuilder.bind(pagoConfirmadoDlq)
            .to(eventosDlxExchange)
            .with(ROUTING_PAGO_DEAD);
    }

    // ─── RabbitTemplate ──────────────────────────────────────────────────────

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          ObjectMapper objectMapper) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter(objectMapper));
        template.setMandatory(true);

        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null) return;
            String eventId = correlationData.getId();
            if (ack) {
                log.debug("[inscription-rabbit] Broker confirmó publicación: eventId={}", eventId);
            } else {
                log.error("[inscription-rabbit] Broker NACK: eventId={}, motivo={}", eventId, cause);
            }
        });

        template.setReturnsCallback(returned -> log.error(
            "[inscription-rabbit] Mensaje no ruteable: routingKey={}, exchange={}, replyText={}",
            returned.getRoutingKey(), returned.getExchange(), returned.getReplyText()));

        return template;
    }
}
