package com.javeriana.eventos.inscription.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.in.ExpirarInscripcionesUseCase;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort;
import com.javeriana.eventos.inscription.domain.port.out.InscripcionRepository;
import com.javeriana.eventos.inscription.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.shared.domain.DomainEvent;
import com.javeriana.eventos.shared.infrastructure.outbox.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Servicio de expiración de inscripciones.
 *
 * Invocado por InscripcionExpirationJob cada minuto.
 * Para cada inscripción expirada:
 *  1. Transición de estado: PENDIENTE_PAGO → EXPIRADA (en el agregado)
 *  2. Persiste el cambio
 *  3. Llama a event-service para liberar el cupo (Feign HTTP)
 *  4. Guarda InscripcionExpiradaEvent en outbox
 *
 * Cada inscripción se procesa en su propia transacción para garantizar
 * que un fallo en una no bloquee las demás.
 */
@Service
public class ExpirarInscripcionesService implements ExpirarInscripcionesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpirarInscripcionesService.class);

    private final InscripcionRepository inscripcionRepository;
    private final EventoServicePort eventoService;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ExpirarInscripcionesService(InscripcionRepository inscripcionRepository,
                                       EventoServicePort eventoService,
                                       OutboxEventRepository outboxRepository,
                                       ObjectMapper objectMapper) {
        this.inscripcionRepository = inscripcionRepository;
        this.eventoService = eventoService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public int expirarVencidas() {
        List<Inscripcion> expiradas = inscripcionRepository.buscarExpiradas();

        if (expiradas.isEmpty()) {
            return 0;
        }

        log.info("Job de expiración: encontradas {} inscripciones vencidas.", expiradas.size());

        int procesadas = 0;
        for (Inscripcion inscripcion : expiradas) {
            try {
                procesarExpiracion(inscripcion);
                procesadas++;
            } catch (Exception e) {
                log.error("Error expirando inscripción {}: {}", inscripcion.getId(), e.getMessage());
            }
        }

        log.info("Job de expiración: {} inscripciones procesadas.", procesadas);
        return procesadas;
    }

    @Transactional
    protected void procesarExpiracion(Inscripcion inscripcion) {
        // 1. Transición de estado en el agregado (valida invariantes)
        inscripcion.expirar();

        // 2. Persistir estado EXPIRADA
        inscripcionRepository.guardar(inscripcion);

        // 3. Liberar cupo en event-service (llamada Feign)
        eventoService.liberarCupo(inscripcion.getEventoId());

        // 4. Publicar eventos de dominio vía Outbox
        for (DomainEvent event : inscripcion.pullDomainEvents()) {
            outboxRepository.guardar(serializarEvento(event));
        }

        log.info("Inscripción {} expirada. Cupo liberado en evento {}.",
            inscripcion.getId(), inscripcion.getEventoId());
    }

    private OutboxEvent serializarEvento(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", event.eventId().toString(),
                "aggregateId", event.aggregateId().toString(),
                "eventoId", event.eventType(),
                "occurredAt", event.occurredAt().toString()
            ));
            return new OutboxEvent(event.aggregateId(), event.eventType(), payload);
        } catch (Exception e) {
            throw new RuntimeException("Error serializando evento", e);
        }
    }
}
