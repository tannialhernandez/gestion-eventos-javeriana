package com.javeriana.eventos.inscription.domain.port.in;

/**
 * Ejecutado por el job scheduler cada minuto.
 * Busca inscripciones PENDIENTE_PAGO cuya fecha_expiracion_pago ya pasó
 * y las expira, liberando el cupo en event-service.
 */
public interface ExpirarInscripcionesUseCase {

    /**
     * @return número de inscripciones expiradas en esta ejecución
     */
    int expirarVencidas();
}
