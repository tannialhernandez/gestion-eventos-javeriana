package com.javeriana.eventos.inscription.application;

import com.javeriana.eventos.inscription.domain.events.InscripcionConfirmadaEvent;
import com.javeriana.eventos.inscription.domain.events.PayloadEventoDominio;
import com.javeriana.eventos.inscription.domain.model.EstadoInscripcion;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.in.ConfirmarInscripcionUseCase;
import com.javeriana.eventos.inscription.domain.port.out.EventoSerializadorPort;
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

import java.util.UUID;

/**
 * Confirma una inscripción cuando payment-service notifica el pago exitoso.
 *
 * M-04 (ADR-001): ObjectMapper reemplazado por EventoSerializadorPort.
 * La capa de aplicación solo conoce objetos de dominio; la conversión a JSON
 * ocurre exclusivamente en infrastructure/serialization/JacksonEventoSerializador.
 *
 * Payload AMQP publicado (SAD §5.3.4):
 * {@code InscripcionConfirmadaEvent} → outbox → relay → exchange eventos.topic
 * routing key: inscripcion.confirmada
 */
@Service
@Transactional
public class ConfirmarInscripcionService implements ConfirmarInscripcionUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmarInscripcionService.class);

    private final InscripcionRepository  inscripcionRepository;
    private final OutboxEventRepository  outboxRepository;
    private final EventoSerializadorPort serializador;

    public ConfirmarInscripcionService(InscripcionRepository inscripcionRepository,
                                       OutboxEventRepository outboxRepository,
                                       EventoSerializadorPort serializador) {
        this.inscripcionRepository = inscripcionRepository;
        this.outboxRepository      = outboxRepository;
        this.serializador          = serializador;
    }

    @Override
    public void confirmar(UUID inscripcionId, String referenciaExterna) {
        Inscripcion inscripcion = inscripcionRepository.buscarPorId(inscripcionId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Inscripción no encontrada: " + inscripcionId));

        if (inscripcion.getEstado() == EstadoInscripcion.CONFIRMADA) {
            log.info("Inscripción {} ya está confirmada. Ignorando mensaje duplicado.", inscripcionId);
            return;
        }

        if (inscripcion.getEstado() == EstadoInscripcion.EXPIRADA) {
            log.warn("Pago recibido para inscripción EXPIRADA {}. " +
                "Payment-service debería emitir reembolso.", inscripcionId);
            return;
        }

        MDC.put(MdcKeys.INSCRIPCION_ID, inscripcionId.toString());
        MDC.put(MdcKeys.EVENTO_ID,      inscripcion.getEventoId().toString());

        String codigoQr = generarCodigoQr(inscripcionId);
        inscripcion.confirmar(codigoQr);
        inscripcionRepository.guardar(inscripcion);

        for (DomainEvent event : inscripcion.pullDomainEvents()) {
            outboxRepository.guardar(crearOutboxEvent(event, inscripcion));
        }

        log.info("Inscripción {} confirmada. QR generado.", inscripcionId);
    }

    @Override
    public void manejarPagoTardio(UUID inscripcionId, String referenciaExterna) {
        log.warn("Pago tardío para inscripción {} (ref: {}). Reembolso manejado por payment-service.",
            inscripcionId, referenciaExterna);
    }

    private String generarCodigoQr(UUID inscripcionId) {
        return "QR-" + UUID.randomUUID() + "-" + inscripcionId.toString().substring(0, 8);
    }

    /**
     * Construye el OutboxEvent delegando la serialización a EventoSerializadorPort (M-04).
     * La aplicación construye el payload de dominio; la infraestructura convierte a JSON.
     */
    private OutboxEvent crearOutboxEvent(DomainEvent event, Inscripcion inscripcion) {
        if (!(event instanceof InscripcionConfirmadaEvent confirmada)) {
            throw new IllegalArgumentException(
                "ConfirmarInscripcionService solo procesa InscripcionConfirmadaEvent, " +
                "recibió: " + event.getClass().getSimpleName());
        }
        PayloadEventoDominio payload = PayloadEventoDominio.deConfirmada(
            confirmada, inscripcion.getCodigoQr());
        String json = serializador.serializar(payload);
        return new OutboxEvent("Inscripcion", event.aggregateId(), event.eventType(), json);
    }
}
