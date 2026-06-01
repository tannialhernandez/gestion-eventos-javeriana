package com.javeriana.eventos.inscription.infrastructure.messaging;

import com.javeriana.eventos.inscription.infrastructure.observability.MdcKeys;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.stereotype.Component;

/**
 * MessagePostProcessor que inyecta el Correlation-ID en headers AMQP (SAD §8.3).
 *
 * Uso con convertAndSend:
 *   rabbitTemplate.convertAndSend(exchange, key, payload, correlationPostProcessor);
 *
 * En el OutboxRelayService se aplica directamente al MessageBuilder ya que usa
 * rabbitTemplate.send() con un Message pre-construido.
 *
 * Permite que los consumidores AMQP (en otros servicios) reconstruyan el MDC
 * con el mismo correlationId, cerrando el ciclo de trazabilidad E2E.
 */
@Component
public class CorrelationIdAmqpMessagePostProcessor implements MessagePostProcessor {

    @Override
    public Message postProcessMessage(Message message) throws AmqpException {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        if (correlationId != null && !correlationId.isBlank()) {
            message.getMessageProperties().setHeader(MdcKeys.AMQP_HEADER, correlationId);
        }
        return message;
    }
}
