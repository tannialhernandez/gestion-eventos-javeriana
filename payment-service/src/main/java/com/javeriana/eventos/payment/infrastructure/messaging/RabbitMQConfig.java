package com.javeriana.eventos.payment.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para payment-service.
 *
 * El exchange y las queues se declaran vía definitions.json al arranque
 * del contenedor de RabbitMQ. Esta config solo declara el RabbitTemplate
 * que usa OutboxRelayService para publicar.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "eventos.topic";

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          ObjectMapper objectMapper) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter(objectMapper));
        return template;
    }

    @Bean
    public TopicExchange eventosTopicExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }
}
