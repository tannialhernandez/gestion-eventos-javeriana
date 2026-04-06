package com.javeriana.eventos.inscription.domain.model;

/**
 * Máquina de estados de PAGO (ver docs/modelo-datos-conceptual.md §4.3)
 */
public enum EstadoPago {
    INICIADO,
    PROCESANDO,
    CONFIRMADO,
    FALLIDO,
    REEMBOLSADO
}
