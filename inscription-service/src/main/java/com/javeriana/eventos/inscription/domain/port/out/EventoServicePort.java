package com.javeriana.eventos.inscription.domain.port.out;

import java.util.UUID;
import java.time.LocalDate;

/**
 * Port de salida hacia event-service (Feign client).
 *
 * Inscription-service llama a event-service para:
 * - Verificar que el evento existe y acepta inscripciones
 * - Reservar/liberar el cupo visible en event-service
 *
 * El bloqueo pesimista para la reserva inicial de cupo ocurre dentro
 * de inscription-service. Luego se sincroniza event-service para que
 * catálogo y detalle muestren la ocupación actualizada.
 */
public interface EventoServicePort {

    EventoInfo obtenerEvento(UUID eventoId);

    void reservarCupo(UUID eventoId);

    void liberarCupo(UUID eventoId);

    /**
     * Obtiene los datos de precio de una tarifa (M-03).
     * Elimina el monto hardcodeado de 100.000 COP en CrearInscripcionService.
     *
     * @param tarifaId UUID de la tarifa seleccionada por el usuario
     * @return TarifaInfo con monto y moneda reales para crear la preferencia de pago
     */
    TarifaInfo obtenerTarifa(UUID tarifaId);

    record EventoInfo(
        UUID id,
        String titulo,
        String estado,
        int cupoDisponible,
        boolean aceptaInscripciones,
        UUID organizadorId,
        String modalidad,
        LocalDate fechaInicio
    ) {
        public EventoInfo(UUID id, String titulo, String estado,
                          int cupoDisponible, boolean aceptaInscripciones) {
            this(id, titulo, estado, cupoDisponible, aceptaInscripciones, null, null, null);
        }
    }

    record TarifaInfo(
        UUID id,
        java.math.BigDecimal monto,
        String moneda,        // "COP", "USD"
        String descripcion
    ) {}
}
