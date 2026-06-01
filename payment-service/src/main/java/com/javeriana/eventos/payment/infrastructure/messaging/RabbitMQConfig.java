package com.javeriana.eventos.payment.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para payment-service.
 *
 * Publisher Confirms (ADR-011):
 *  Con publisher-confirm-type=correlated en application.yml, la ConnectionFactory
 *  ya está configurada para enviar ACK/NACK por mensaje. El RabbitTemplate aquí
 *  registra los callbacks que procesan esas confirmaciones.
 *
 *  - ConfirmCallback: loguea si el broker aceptó (ACK) o rechazó (NACK) el mensaje.
 *  - ReturnsCallback: loguea mensajes que el broker no pudo rutear (mandatory=true).
 *
 *  La lógica de reintento (incrementarIntentos, marcarFallido) vive en
 *  OutboxRelayService, no aquí, para mantener separación de responsabilidades.
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    public static final String EXCHANGE = "eventos.topic";

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          ObjectMapper objectMapper) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter(objectMapper));
        template.setMandatory(true);

        // ACK / NACK del broker — el relay espera el futuro en CorrelationData;
        // este callback es para logging y trazabilidad adicional.
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null) return;
            String eventId = correlationData.getId();
            if (ack) {
                log.debug("[payment-rabbit] Broker confirmó publicación: eventId={}", eventId);
            } else {
                log.error("[payment-rabbit] Broker NACK: eventId={}, motivo={}", eventId, cause);
            }
        });

        // Mensaje no ruteable — el broker lo devuelve en lugar de descartarlo silenciosamente
        template.setReturnsCallback(returned -> log.error(
            "[payment-rabbit] Mensaje no ruteable: routingKey={}, exchange={}, replyText={}",
            returned.getRoutingKey(), returned.getExchange(), returned.getReplyText()));

        return template;
    }

    @Bean
    public TopicExchange eventosTopicExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }
}
