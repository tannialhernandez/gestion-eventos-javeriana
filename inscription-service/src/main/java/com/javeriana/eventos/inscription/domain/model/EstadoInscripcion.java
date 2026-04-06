package com.javeriana.eventos.inscription.domain.model;

/**
 * Máquina de estados de INSCRIPCION (ver docs/modelo-datos-conceptual.md §4.2)
 *
 * PENDIENTE_PAGO → CONFIRMADA → ASISTENCIA_REGISTRADA → CERTIFICADO_EMITIDO
 * PENDIENTE_PAGO → EXPIRADA (timeout 15 min, cupo liberado)
 */
public enum EstadoInscripcion {
    PENDIENTE_PAGO,
    CONFIRMADA,
    ASISTENCIA_REGISTRADA,
    CERTIFICADO_EMITIDO,
    EXPIRADA;

    public boolean aceptaPago() {
        return this == PENDIENTE_PAGO;
    }

    public boolean estaActiva() {
        return this == CONFIRMADA || this == ASISTENCIA_REGISTRADA || this == CERTIFICADO_EMITIDO;
    }
}
