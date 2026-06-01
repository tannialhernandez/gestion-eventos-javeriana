package com.javeriana.eventos.inscription.domain.port.out;

import java.util.UUID;

/**
 * Port de salida hacia event-service (Feign client).
 *
 * Inscription-service llama a event-service para:
 * - Verificar que el evento existe y acepta inscripciones
 * - Liberar el cupo cuando una inscripción expira o se cancela
 *
 * El bloqueo pesimista para la RESERVA inicial de cupo ocurre dentro
 * de inscription-service (en su propia BD), no vía HTTP a event-service,
 * para mantener atomicidad transaccional.
 */
public interface EventoServicePort {

    EventoInfo obtenerEvento(UUID eventoId);

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
        boolean aceptaInscripciones
    ) {}

    record TarifaInfo(
        UUID id,
        java.math.BigDecimal monto,
        String moneda,        // "COP", "USD"
        String descripcion
    ) {}
}
