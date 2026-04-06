package com.javeriana.eventos.inscription.infrastructure.scheduler;

import com.javeriana.eventos.inscription.domain.port.in.ExpirarInscripcionesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job que revisa inscripciones expiradas cada 60 segundos.
 *
 * Comportamiento (docs/comportamiento-runtime-inscripcion-pago.md §4):
 * - Busca inscripciones PENDIENTE_PAGO con fecha_expiracion_pago < NOW()
 * - Para cada una: estado → EXPIRADA + cupo liberado en event-service
 * - Si el pago llega después (webhook tardío) → payment-service emite reembolso
 *
 * El job es idempotente: si corre dos veces seguidas, la segunda no encuentra
 * inscripciones en PENDIENTE_PAGO que ya fueron expiradas por la primera.
 */
@Component
public class InscripcionExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(InscripcionExpirationJob.class);

    private final ExpirarInscripcionesUseCase expirarInscripciones;

    public InscripcionExpirationJob(ExpirarInscripcionesUseCase expirarInscripciones) {
        this.expirarInscripciones = expirarInscripciones;
    }

    @Scheduled(fixedRate = 60_000)   // Cada 60 segundos
    public void ejecutar() {
        log.debug("Job de expiración iniciado...");
        int expiradas = expirarInscripciones.expirarVencidas();
        if (expiradas > 0) {
            log.info("Job de expiración: {} inscripciones expiradas y cupos liberados.", expiradas);
        }
    }
}
