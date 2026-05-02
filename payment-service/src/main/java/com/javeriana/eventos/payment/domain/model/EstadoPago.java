package com.javeriana.eventos.payment.domain.model;

/**
 * Máquina de estados de PAGO (docs/modelo-datos-conceptual.md §4.3)
 *
 * INICIADO → PROCESANDO → CONFIRMADO
 *                       → FALLIDO
 * CONFIRMADO → REEMBOLSADO
 */
public enum EstadoPago {
    INICIADO,
    PROCESANDO,
    CONFIRMADO,
    FALLIDO,
    REEMBOLSADO;

    public boolean esFinal() {
        return this == CONFIRMADO || this == FALLIDO || this == REEMBOLSADO;
    }
}
