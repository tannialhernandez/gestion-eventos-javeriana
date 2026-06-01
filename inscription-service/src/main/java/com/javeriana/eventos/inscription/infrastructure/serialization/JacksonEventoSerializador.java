package com.javeriana.eventos.inscription.infrastructure.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.inscription.domain.port.out.EventoSerializadorPort;
import org.springframework.stereotype.Component;

/**
 * Adaptador Jackson que implementa EventoSerializadorPort.
 *
 * Es el único lugar del sistema donde Jackson conoce los payloads de eventos
 * de dominio de inscription-service. Los servicios de aplicación no tienen
 * dependencia directa de Jackson (ADR-001 M-04 cerrado).
 *
 * ObjectMapper provisto por Spring Boot autoconfiguration: incluye JavaTimeModule
 * y WRITE_DATES_AS_TIMESTAMPS=false por defecto (Instant → ISO-8601).
 */
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
