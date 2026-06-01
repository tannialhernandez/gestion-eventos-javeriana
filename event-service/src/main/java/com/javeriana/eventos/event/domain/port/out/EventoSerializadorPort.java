package com.javeriana.eventos.event.domain.port.out;

/**
 * Puerto de salida para serialización de payloads de eventos de dominio (ADR-001 M-04).
 * Extrae la dependencia de Jackson de la capa de aplicación.
 */
public interface EventoSerializadorPort {

    String serializar(Object payload);
}
