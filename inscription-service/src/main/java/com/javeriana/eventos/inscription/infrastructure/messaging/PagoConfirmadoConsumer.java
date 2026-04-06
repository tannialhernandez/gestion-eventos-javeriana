package com.javeriana.eventos.inscription.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.inscription.domain.port.in.ConfirmarInscripcionUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Escucha la queue "pago.confirmado" publicada por payment-service.
 *
 * Cuando payment-service confirma un pago exitoso, publica en esta queue.
 * Este consumer invoca ConfirmarInscripcionUseCase para hacer la transición
 * de estado: PENDIENTE_PAGO → CONFIRMADA.
 *
 * Idempotencia: ConfirmarInscripcionService verifica el estado actual antes
 * de confirmar. Si ya está CONFIRMADA, ignora el mensaje sin error.
 *
 * Si este consumer falla 3 veces, RabbitMQ mueve el mensaje a la DLQ
 * configurada en infrastructure/rabbitmq/definitions.json.
 */
@Component
public class PagoConfirmadoConsumer {

    private static final Logger log = LoggerFactory.getLogger(PagoConfirmadoConsumer.class);

    private final ConfirmarInscripcionUseCase confirmarInscripcion;
    private final ObjectMapper objectMapper;

    public PagoConfirmadoConsumer(ConfirmarInscripcionUseCase confirmarInscripcion,
                                   ObjectMapper objectMapper) {
        this.confirmarInscripcion = confirmarInscripcion;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "pago.confirmado")
    public void onPagoConfirmado(String mensaje) {
        try {
            JsonNode payload = objectMapper.readTree(mensaje);
            UUID inscripcionId = UUID.fromString(payload.get("inscripcionId").asText());
            String referenciaExterna = payload.get("referenciaExterna").asText();

            log.info("Pago confirmado recibido para inscripción: {}", inscripcionId);
            confirmarInscripcion.confirmar(inscripcionId, referenciaExterna);

        } catch (Exception e) {
            log.error("Error procesando mensaje de pago confirmado: {}", e.getMessage());
            // Re-lanzar para que RabbitMQ reintente y eventualmente mueva a DLQ
            throw new RuntimeException("Error procesando pago.confirmado", e);
        }
    }
}
