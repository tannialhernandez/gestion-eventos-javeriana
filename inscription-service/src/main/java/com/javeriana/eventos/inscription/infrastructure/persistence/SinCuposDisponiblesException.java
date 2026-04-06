package com.javeriana.eventos.inscription.infrastructure.persistence;

import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;

import java.util.UUID;

/**
 * Se lanza cuando el bloqueo pesimista confirma que cupo_disponible == 0.
 * La capa web la mapea a HTTP 409 Conflict.
 */
public class SinCuposDisponiblesException extends BusinessRuleViolationException {

    public SinCuposDisponiblesException(UUID eventoId) {
        super("RN-08", "No hay cupos disponibles para el evento: " + eventoId);
    }
}
