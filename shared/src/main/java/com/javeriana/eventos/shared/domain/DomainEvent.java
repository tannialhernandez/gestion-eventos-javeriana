package com.javeriana.eventos.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Interfaz base para todos los eventos de dominio.
 *
 * Un evento de dominio representa algo que ocurrió en el dominio y que
 * otros componentes pueden necesitar conocer (e.g., InscripcionConfirmada,
 * PagoConfirmado). Se usan para desacoplar microservicios vía Outbox Pattern.
 */
public interface DomainEvent {

    /** Identificador único del evento */
    UUID eventId();

    /** ID del agregado que originó el evento */
    UUID aggregateId();

    /** Tipo del evento (e.g., "INSCRIPCION_CONFIRMADA") */
    String eventType();

    /** Momento en que ocurrió el evento */
    Instant occurredAt();
}
