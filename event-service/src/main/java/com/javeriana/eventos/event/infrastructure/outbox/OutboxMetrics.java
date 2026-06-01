package com.javeriana.eventos.event.infrastructure.outbox;

import com.javeriana.eventos.event.domain.port.out.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    private static final String SERVICIO = "event-service";

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
            .description("Fallos de publicacion reintentables")
            .tag("servicio", SERVICIO)
            .register(registry);

        this.eventosFallidosDefinitivos = Counter.builder("outbox.events.failed.terminal")
            .description("Eventos marcados FALLIDO tras agotar MAX_INTENTOS")
            .tag("servicio", SERVICIO)
            .register(registry);

        this.tamanoLotePolling = DistributionSummary.builder("outbox.polling.batch.size")
            .description("Cantidad de eventos procesados por iteracion del relay")
            .tag("servicio", SERVICIO)
            .register(registry);

        Gauge.builder("outbox.events.pending", outboxRepository,
                      OutboxEventRepository::contarPendientes)
            .description("Eventos pendientes de publicar")
            .tag("servicio", SERVICIO)
            .register(registry);
    }

    public void registrarPolling(int tamano)       { tamanoLotePolling.record(tamano); }
    public void registrarPublicacionExitosa()      { eventosPublicadosExitosos.increment(); }
    public void registrarFalloPublicacion()        { eventosFallidos.increment(); }
    public void registrarEventoFallidoDefinitivo() { eventosFallidosDefinitivos.increment(); }
}
