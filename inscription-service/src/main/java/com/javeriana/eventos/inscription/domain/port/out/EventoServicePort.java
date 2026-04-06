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

    record EventoInfo(
        UUID id,
        String titulo,
        String estado,
        int cupoDisponible,
        boolean aceptaInscripciones
    ) {}
}
