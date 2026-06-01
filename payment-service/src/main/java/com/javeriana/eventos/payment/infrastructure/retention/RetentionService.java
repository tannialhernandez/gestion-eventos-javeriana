package com.javeriana.eventos.payment.infrastructure.retention;

import com.javeriana.eventos.payment.domain.port.out.OutboxRetentionRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.IntSupplier;

/**
 * Job de retencion para payment-service.
 * Tablas gestionadas:
 *   - outbox_events (ENVIADO: 30d, FALLIDO+intentos>=10: 90d)
 *   - mensaje_procesado (30d) — columnas EN: processed_at, consumer_group
 * CRITICO: pago_audit NO se toca (Ley 1581 Art. 11 — datos financieros).
 */
@Service
@ConditionalOnProperty(name = "retention.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final OutboxRetentionRepository outboxRepository;
    private final RetentionConfig config;
    private final RetentionMetrics metrics;

    public RetentionService(OutboxRetentionRepository outboxRepository,
                             RetentionConfig config,
                             RetentionMetrics metrics) {
        this.outboxRepository = outboxRepository;
        this.config           = config;
        this.metrics          = metrics;
    }

    @Scheduled(cron = "${retention.cron:0 0 3 * * *}")
    @SchedulerLock(name = "payment-RetentionService",
                   lockAtMostFor = "PT30M",
                   lockAtLeastFor = "PT1M")
    public void limpiar() {
        log.info("[retention-payment] Iniciando limpieza programada");
        long inicio = System.currentTimeMillis();
        try {
            int enviados   = limpiarPorBatches(() -> outboxRepository.eliminarEnviadosAnterioresA(
                Instant.now().minus(config.getOutboxEnviadoDays(), ChronoUnit.DAYS), config.getBatchSize()));
            int fallidos   = limpiarPorBatches(() -> outboxRepository.eliminarFallidosAnterioresA(
                Instant.now().minus(config.getOutboxFallidoDays(), ChronoUnit.DAYS), config.getBatchSize()));
            int procesados = limpiarPorBatches(() -> outboxRepository.eliminarMensajesProcesadosAnterioresA(
                Instant.now().minus(config.getMensajeProcesadoDays(), ChronoUnit.DAYS), config.getBatchSize()));
            long duracionMs = System.currentTimeMillis() - inicio;
            metrics.recordRun(enviados + fallidos + procesados, duracionMs);
            log.info("[retention-payment] Completada: outbox_enviado={}, outbox_fallido={}, mensaje_procesado={}, duracion={}ms",
                enviados, fallidos, procesados, duracionMs);
        } catch (Exception e) {
            metrics.recordError();
            log.error("[retention-payment] Error durante limpieza: {}", e.getMessage(), e);
        }
    }

    private int limpiarPorBatches(IntSupplier batchSupplier) {
        int total = 0;
        int batches = 0;
        int eliminados;
        do {
            eliminados = batchSupplier.getAsInt();
            total += eliminados;
            batches++;
            if (batches >= config.getMaxBatchesPerRun()) {
                log.warn("[retention-payment] Techo de batches alcanzado ({}), pausando hasta proximo run", batches);
                break;
            }
        } while (eliminados == config.getBatchSize());
        return total;
    }
}
