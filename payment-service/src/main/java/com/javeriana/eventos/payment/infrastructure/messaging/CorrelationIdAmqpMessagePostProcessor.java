package com.javeriana.eventos.payment.infrastructure.messaging;

import com.javeriana.eventos.payment.infrastructure.observability.MdcKeys;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.stereotype.Component;

/**
 * MessagePostProcessor que inyecta el Correlation-ID en headers AMQP.
 *
 * Equivalente al de inscription-service (Prompt 13).
 * Usado opcionalmente en contextos donde se usa convertAndSend.
 * OutboxRelayService inyecta el header directamente en MessageBuilder.
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
