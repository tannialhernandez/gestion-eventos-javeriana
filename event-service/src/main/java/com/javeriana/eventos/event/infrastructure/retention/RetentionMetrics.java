package com.javeriana.eventos.event.infrastructure.retention;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RetentionMetrics {

    private static final String SERVICIO = "event-service";

    private final MeterRegistry registry;
    private Counter filasEliminadasCounter;
    private Counter erroresCounter;
    private Timer duracionTimer;

    public RetentionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void init() {
        filasEliminadasCounter = Counter.builder("retention.rows.deleted")
            .description("Total filas eliminadas por el job de retencion")
            .tag("servicio", SERVICIO)
            .register(registry);
        erroresCounter = Counter.builder("retention.errors")
            .description("Fallos del job de retencion")
            .tag("servicio", SERVICIO)
            .register(registry);
        duracionTimer = Timer.builder("retention.duration")
            .description("Duracion del job de retencion")
            .tag("servicio", SERVICIO)
            .register(registry);
    }

    public void recordRun(int filasEliminadas, long duracionMs) {
        filasEliminadasCounter.increment(filasEliminadas);
        duracionTimer.record(duracionMs, TimeUnit.MILLISECONDS);
    }

    public void recordError() {
        erroresCounter.increment();
    }
}
