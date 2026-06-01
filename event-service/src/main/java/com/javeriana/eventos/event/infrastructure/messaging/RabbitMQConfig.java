package com.javeriana.eventos.event.infrastructure.messaging;

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
 * Topologia RabbitMQ de event-service (ADR-019 — DLQ Strategy).
 *
 * Producer:
 *   exchange: eventos.topic  (compartido con inscription-service y payment-service)
 *   routing keys: evento.publicado | evento.cancelado | evento.cupo.liberado | evento.cupo.reservado
 *
 * Colas declaradas para auditoría / consumidores futuros (notification-service):
 *   evento.publicado     → DLX → eventos.topic.dlx → evento.publicado.dead → evento.publicado.dlq
 *   evento.cancelado     → DLX idem
 *
 * Publisher Confirms activados por application.yml:
 *   spring.rabbitmq.publisher-confirm-type: correlated
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_EVENTOS  = "eventos.topic";
    public static final String EXCHANGE_DLX      = "eventos.topic.dlx";

    public static final String QUEUE_PUBLICADO   = "evento.publicado";
    public static final String QUEUE_CANCELADO   = "evento.cancelado";
    public static final String QUEUE_PUBLICADO_DLQ = "evento.publicado.dlq";
    public static final String QUEUE_CANCELADO_DLQ = "evento.cancelado.dlq";

    @Bean
    public TopicExchange eventosTopicExchange() {
        return new TopicExchange(EXCHANGE_EVENTOS, true, false);
    }

    @Bean
    public TopicExchange eventosDlxExchange() {
        return new TopicExchange(EXCHANGE_DLX, true, false);
    }

    @Bean
    public Queue eventoPublicadoQueue() {
        return QueueBuilder.durable(QUEUE_PUBLICADO)
            .withArgument("x-dead-letter-exchange",    EXCHANGE_DLX)
            .withArgument("x-dead-letter-routing-key", "evento.publicado.dead")
            .build();
    }

    @Bean
    public Queue eventoCanceladoQueue() {
        return QueueBuilder.durable(QUEUE_CANCELADO)
            .withArgument("x-dead-letter-exchange",    EXCHANGE_DLX)
            .withArgument("x-dead-letter-routing-key", "evento.cancelado.dead")
            .build();
    }

    @Bean
    public Queue eventoPublicadoDlq() {
        return QueueBuilder.durable(QUEUE_PUBLICADO_DLQ).build();
    }

    @Bean
    public Queue eventoCanceladoDlq() {
        return QueueBuilder.durable(QUEUE_CANCELADO_DLQ).build();
    }

    @Bean
    public Binding bindingEventoPublicado() {
        return BindingBuilder.bind(eventoPublicadoQueue())
            .to(eventosTopicExchange()).with("evento.publicado");
    }

    @Bean
    public Binding bindingEventoCancelado() {
        return BindingBuilder.bind(eventoCanceladoQueue())
            .to(eventosTopicExchange()).with("evento.cancelado");
    }

    @Bean
    public Binding bindingEventoPublicadoDlq() {
        return BindingBuilder.bind(eventoPublicadoDlq())
            .to(eventosDlxExchange()).with("evento.publicado.dead");
    }

    @Bean
    public Binding bindingEventoCanceladoDlq() {
        return BindingBuilder.bind(eventoCanceladoDlq())
            .to(eventosDlxExchange()).with("evento.cancelado.dead");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        template.setMandatory(true);
        return template;
    }
}
