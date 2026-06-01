package com.javeriana.eventos.inscription.application;

import com.javeriana.eventos.inscription.domain.events.InscripcionExpiradaEvent;
import com.javeriana.eventos.inscription.domain.events.PayloadEventoDominio;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.in.ExpirarInscripcionesUseCase;
import com.javeriana.eventos.inscription.domain.port.out.EventoSerializadorPort;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort;
import com.javeriana.eventos.inscription.domain.port.out.InscripcionRepository;
import com.javeriana.eventos.inscription.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.inscription.infrastructure.observability.MdcKeys;
import com.javeriana.eventos.shared.domain.DomainEvent;
import com.javeriana.eventos.shared.domain.outbox.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de expiración de inscripciones.
 *
 * M-04 (ADR-001): ObjectMapper reemplazado por EventoSerializadorPort.
 * La capa de aplicación solo conoce objetos de dominio; Jackson vive
 * exclusivamente en infrastructure/serialization/JacksonEventoSerializador.
 *
 * Payload AMQP publicado (SAD §5.3.4):
 * routing key: inscripcion.expirada
 */
@Service
public class ExpirarInscripcionesService implements ExpirarInscripcionesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpirarInscripcionesService.class);

    private final InscripcionRepository  inscripcionRepository;
    private final EventoServicePort      eventoService;
    private final OutboxEventRepository  outboxRepository;
    private final EventoSerializadorPort serializador;

    public ExpirarInscripcionesService(InscripcionRepository inscripcionRepository,
                                       EventoServicePort eventoService,
                                       OutboxEventRepository outboxRepository,
                                       EventoSerializadorPort serializador) {
        this.inscripcionRepository = inscripcionRepository;
        this.eventoService         = eventoService;
        this.outboxRepository      = outboxRepository;
        this.serializador          = serializador;
    }

    @Override
    public int expirarVencidas() {
        List<Inscripcion> expiradas = inscripcionRepository.buscarExpiradas();

        if (expiradas.isEmpty()) return 0;

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
        MDC.put(MdcKeys.INSCRIPCION_ID, inscripcion.getId().toString());
        MDC.put(MdcKeys.EVENTO_ID,      inscripcion.getEventoId().toString());

        inscripcion.expirar();
        inscripcionRepository.guardar(inscripcion);
        eventoService.liberarCupo(inscripcion.getEventoId());

        for (DomainEvent event : inscripcion.pullDomainEvents()) {
            outboxRepository.guardar(crearOutboxEvent(event));
        }

        log.info("Inscripción {} expirada. Cupo liberado en evento {}.",
            inscripcion.getId(), inscripcion.getEventoId());
    }

    /**
     * Construye el OutboxEvent delegando la serialización a EventoSerializadorPort (M-04).
     */
    private OutboxEvent crearOutboxEvent(DomainEvent event) {
        if (!(event instanceof InscripcionExpiradaEvent expirada)) {
            throw new IllegalArgumentException(
                "ExpirarInscripcionesService solo procesa InscripcionExpiradaEvent, " +
                "recibió: " + event.getClass().getSimpleName());
        }
        PayloadEventoDominio payload = PayloadEventoDominio.deExpirada(expirada);
        String json = serializador.serializar(payload);
        return new OutboxEvent("Inscripcion", event.aggregateId(), event.eventType(), json);
    }
}
