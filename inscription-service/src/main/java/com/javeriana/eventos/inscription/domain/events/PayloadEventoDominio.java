package com.javeriana.eventos.inscription.domain.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * DTO canónico del payload publicado al exchange AMQP para eventos de inscripción.
 *
 * Contrato documentado en ADR-020 y docs/asyncapi/inscription-service.yml.
 * Routing keys: inscripcion.creada | inscripcion.confirmada | inscripcion.expirada
 *
 * Decisión de diseño (ADR-014): eventType también va en el HEADER AMQP.
 * Lo incluimos en el body para audit logs que persistan solo el payload.
 *
 * @JsonInclude(NON_NULL): excluye campos opcionales cuando son null, por ejemplo:
 *   - codigoQr: null para CREADA y EXPIRADA
 *   - checkoutUrl, monto, moneda, expiraEn: null para CONFIRMADA y EXPIRADA
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PayloadEventoDominio(
    String eventId,
    String aggregateId,
    String aggregateType,
    String eventType,
    String eventoId,
    String usuarioId,
    String codigoQr,       // CONFIRMADA únicamente
    String checkoutUrl,    // CREADA únicamente
    String monto,          // CREADA únicamente (string para preservar precisión)
    String moneda,         // CREADA únicamente
    String expiraEn,       // CREADA únicamente (ISO-8601 UTC)
    String occurredAt,
    String schemaVersion
) {

    private static final String SCHEMA_V1      = "v1";
    private static final String AGGREGATE_TYPE = "Inscripcion";

    /**
     * Payload para INSCRIPCION_CREADA.
     * Incluye monto, moneda y checkoutUrl para que notification-service
     * pueda enviar el recordatorio de pago completo.
     */
    public static PayloadEventoDominio deCreada(InscripcionCreadaEvent event,
                                                 BigDecimal monto,
                                                 String moneda,
                                                 String checkoutUrl) {
        return new PayloadEventoDominio(
            event.eventId().toString(),
            event.aggregateId().toString(),     // inscripcionId
            AGGREGATE_TYPE,
            event.eventType(),                  // "INSCRIPCION_CREADA"
            event.eventoId().toString(),
            event.usuarioId().toString(),
            null,                               // sin codigoQr en creación
            checkoutUrl,
            monto != null ? monto.toPlainString() : null,
            moneda,
            event.expiraEn().toString(),
            event.occurredAt().toString(),
            SCHEMA_V1
        );
    }

    /**
     * Payload para INSCRIPCION_CONFIRMADA.
     * Incluye codigoQr del agregado (no disponible en el evento de dominio).
     */
    public static PayloadEventoDominio deConfirmada(InscripcionConfirmadaEvent event,
                                                     String codigoQr) {
        return new PayloadEventoDominio(
            event.eventId().toString(),
            event.aggregateId().toString(),
            AGGREGATE_TYPE,
            event.eventType(),
            event.eventoId().toString(),
            event.usuarioId().toString(),
            codigoQr,
            null,   // sin checkoutUrl en confirmación
            null,   // sin monto
            null,   // sin moneda
            null,   // sin expiraEn
            event.occurredAt().toString(),
            SCHEMA_V1
        );
    }

    /**
     * Payload para INSCRIPCION_EXPIRADA.
     * CORRECCIÓN C-01 (Prompt 9): eventoId usa event.eventoId() (UUID real),
     * no event.eventType() (string literal).
     */
    public static PayloadEventoDominio deExpirada(InscripcionExpiradaEvent event) {
        return new PayloadEventoDominio(
            event.eventId().toString(),
            event.aggregateId().toString(),
            AGGREGATE_TYPE,
            event.eventType(),
            event.eventoId().toString(),    // UUID del evento académico — CORRECTO
            event.usuarioId().toString(),
            null,   // sin codigoQr en expiración
            null,   // sin checkoutUrl
            null,   // sin monto
            null,   // sin moneda
            null,   // sin expiraEn
            event.occurredAt().toString(),
            SCHEMA_V1
        );
    }
}
