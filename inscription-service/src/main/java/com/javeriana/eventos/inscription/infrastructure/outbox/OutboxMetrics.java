package com.javeriana.eventos.inscription.infrastructure.outbox;

import com.javeriana.eventos.inscription.domain.port.out.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Métricas Micrometer para el Outbox Relay de inscription-service.
 *
 * Métricas expuestas (prefijo: outbox.*):
 *  - outbox.events.pending           (Gauge)   : eventos PENDIENTE en BD en este instante
 *  - outbox.events.published.success (Counter) : eventos publicados y ACK del broker
 *  - outbox.events.publish.failed    (Counter) : fallos de publicación (reintentables)
 *  - outbox.events.failed.terminal   (Counter) : eventos marcados FALLIDO
 *  - outbox.polling.batch.size       (Summary) : distribución del tamaño de lote por ciclo
 */
@Component
public class OutboxMetrics {

    private static final String SERVICIO = "inscription-service";

    private final Counter eventosPublicadosExitosos;
    private final Counter eventosFallidos;
    private final Counter eventosFallidosDefinitivos;
    private final DistributionSummary tamanoLotePolling;

    public OutboxMetrics(MeterRegistry registry, OutboxEventRepository outboxRepository) {

        this.eventosPublicadosExitosos = Counter.builder("outbox.events.published.success")
            .description("Eventos publicados y confirmados por el broker")
            .tag("servicio", SERVICIO)
            .register(registry);

        this.eventosFallidos = Counter.builder("outbox.events.publish.failed")
            .description("Fallos de publicación reintentables")
            .tag("servicio", SERVICIO)
            .register(registry);

        this.eventosFallidosDefinitivos = Counter.builder("outbox.events.failed.terminal")
            .description("Eventos marcados FALLIDO tras agotar MAX_INTENTOS")
            .tag("servicio", SERVICIO)
            .register(registry);

        this.tamanoLotePolling = DistributionSummary.builder("outbox.polling.batch.size")
            .description("Cantidad de eventos procesados por iteración del relay")
            .tag("servicio", SERVICIO)
            .register(registry);

        Gauge.builder("outbox.events.pending", outboxRepository,
                      OutboxEventRepository::contarPendientes)
            .description("Eventos pendientes de publicar en este instante")
            .tag("servicio", SERVICIO)
            .register(registry);
    }

    public void registrarPolling(int tamano)          { tamanoLotePolling.record(tamano); }
    public void registrarPublicacionExitosa()         { eventosPublicadosExitosos.increment(); }
    public void registrarFalloPublicacion()           { eventosFallidos.increment(); }
    public void registrarEventoFallidoDefinitivo()    { eventosFallidosDefinitivos.increment(); }
}
