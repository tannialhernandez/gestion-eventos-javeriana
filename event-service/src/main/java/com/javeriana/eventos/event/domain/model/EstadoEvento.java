package com.javeriana.eventos.event.domain.model;

/**
 * Máquina de estados de EVENTO (ver docs/modelo-datos-conceptual.md §4.1)
 *
 * Transiciones permitidas:
 *   BORRADOR → PENDIENTE_PUBLICACION, CANCELADO
 *   PENDIENTE_PUBLICACION → PUBLICADO, BORRADOR, CANCELADO
 *   PUBLICADO → FINALIZADO (automático), CANCELADO
 *   FINALIZADO → (terminal)
 *   CANCELADO → (terminal)
 */
public enum EstadoEvento {
    BORRADOR,
    PENDIENTE_PUBLICACION,
    PUBLICADO,
    FINALIZADO,
    CANCELADO;

    public boolean esTerminal() {
        return this == FINALIZADO || this == CANCELADO;
    }

    public boolean aceptaInscripciones() {
        return this == PUBLICADO;
    }
}
