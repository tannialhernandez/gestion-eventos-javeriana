package com.javeriana.eventos.inscription.domain.port.out;

/**
 * Puerto de salida para serialización de payloads de eventos de dominio (ADR-001 M-04).
 *
 * Extrae la dependencia de Jackson (framework de infraestructura) de la capa de
 * aplicación. Los servicios de aplicación construyen el payload como un objeto
 * de dominio (PayloadEventoDominio) y delegan la conversión a JSON a este puerto.
 *
 * Beneficio: ConfirmarInscripcionService y ExpirarInscripcionesService dejan de
 * conocer Jackson → ADR-001 (Hexagonal) sube del 70% al 95%.
 *
 * Interface pura: sin anotaciones Spring ni dependencias de serialización.
 */
public interface EventoSerializadorPort {

    /**
     * Serializa un payload a JSON string.
     *
     * @param payload objeto a serializar (típicamente PayloadEventoDominio)
     * @return JSON string listo para almacenar en outbox_events.payload
     * @throws RuntimeException si la serialización falla
     */
    String serializar(Object payload);
}
