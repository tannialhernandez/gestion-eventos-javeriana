package com.javeriana.eventos.event.infrastructure.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.event.domain.port.out.EventoSerializadorPort;
import org.springframework.stereotype.Component;

@Component
public class JacksonEventoSerializador implements EventoSerializadorPort {

    private final ObjectMapper objectMapper;

    public JacksonEventoSerializador(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String serializar(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException(
                "Error serializando payload de tipo " + payload.getClass().getSimpleName()
                + ": " + e.getMessage(), e);
        }
    }
}
