package com.javeriana.eventos.payment.retention;

import com.javeriana.eventos.payment.domain.port.out.OutboxRetentionRepository;
import com.javeriana.eventos.payment.infrastructure.retention.RetentionConfig;
import com.javeriana.eventos.payment.infrastructure.retention.RetentionMetrics;
import com.javeriana.eventos.payment.infrastructure.retention.RetentionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RetentionServiceTest - payment-service")
class RetentionServiceTest {

    private FakeOutboxRetentionRepository outboxRepository;
    private FakeRetentionMetrics metrics;
    private RetentionConfig config;
    private RetentionService retentionService;

    @BeforeEach
    void setUp() {
        outboxRepository = new FakeOutboxRetentionRepository();
        metrics = new FakeRetentionMetrics();
        config = new RetentionConfig();
        config.setBatchSize(1000);
        config.setMaxBatchesPerRun(100);
        retentionService = new RetentionService(outboxRepository, config, metrics);
    }

    @Test
    @DisplayName("debeEliminarOutboxEnviadosMasViejosDe30Dias")
    void debeEliminarOutboxEnviadosMasViejosDe30Dias() {
        Instant desde = Instant.now().minus(30, ChronoUnit.DAYS).minusSeconds(2);
        retentionService.limpiar();
        Instant hasta = Instant.now().minus(30, ChronoUnit.DAYS).plusSeconds(2);

        assertThat(outboxRepository.enviadosCalls).hasSize(1);
        assertThat(outboxRepository.enviadosCalls.get(0).umbral()).isBetween(desde, hasta);
    }

    @Test
    @DisplayName("debeEliminarOutboxFallidosMasViejosDe90DiasYConIntentosMayores10")
    void debeEliminarOutboxFallidosMasViejosDe90DiasYConIntentosMayores10() {
        Instant desde = Instant.now().minus(90, ChronoUnit.DAYS).minusSeconds(2);
        retentionService.limpiar();
        Instant hasta = Instant.now().minus(90, ChronoUnit.DAYS).plusSeconds(2);

        assertThat(outboxRepository.fallidosCalls).hasSize(1);
        assertThat(outboxRepository.fallidosCalls.get(0).umbral()).isBetween(desde, hasta);
    }

    @Test
    @DisplayName("debeRespetarBatchSize")
    void debeRespetarBatchSize() {
        config.setBatchSize(37);

        retentionService.limpiar();

        assertThat(outboxRepository.enviadosCalls).allSatisfy(call -> assertThat(call.batchSize()).isEqualTo(37));
        assertThat(outboxRepository.fallidosCalls).allSatisfy(call -> assertThat(call.batchSize()).isEqualTo(37));
        assertThat(outboxRepository.procesadosCalls).allSatisfy(call -> assertThat(call.batchSize()).isEqualTo(37));
    }

    @Test
    @DisplayName("debeRespetarMaxBatchesPerRun")
    void debeRespetarMaxBatchesPerRun() {
        config.setBatchSize(10);
        config.setMaxBatchesPerRun(3);
        outboxRepository.enviadosReturn = 10;

        retentionService.limpiar();

        assertThat(outboxRepository.enviadosCalls).hasSize(3);
        assertThat(outboxRepository.fallidosCalls).hasSize(1);
        assertThat(outboxRepository.procesadosCalls).hasSize(1);
        assertThat(metrics.filasEliminadas).isEqualTo(30);
    }

    @Test
    @DisplayName("debeRegistrarMetricasTrasEjecucion")
    void debeRegistrarMetricasTrasEjecucion() {
        config.setBatchSize(10);
        outboxRepository.enviadosReturn = 4;
        outboxRepository.fallidosReturn = 6;
        outboxRepository.procesadosReturn = 8;

        retentionService.limpiar();

        assertThat(metrics.filasEliminadas).isEqualTo(18);
        assertThat(metrics.duracionMs).isGreaterThanOrEqualTo(0);
        assertThat(metrics.errores).isZero();
    }

    private record Call(Instant umbral, int batchSize) {
    }

    private static final class FakeOutboxRetentionRepository implements OutboxRetentionRepository {
        private final List<Call> enviadosCalls = new ArrayList<>();
        private final List<Call> fallidosCalls = new ArrayList<>();
        private final List<Call> procesadosCalls = new ArrayList<>();
        private int enviadosReturn;
        private int fallidosReturn;
        private int procesadosReturn;

        @Override
        public int eliminarEnviadosAnterioresA(Instant umbral, int batchSize) {
            enviadosCalls.add(new Call(umbral, batchSize));
            return enviadosReturn;
        }

        @Override
        public int eliminarFallidosAnterioresA(Instant umbral, int batchSize) {
            fallidosCalls.add(new Call(umbral, batchSize));
            return fallidosReturn;
        }

        @Override
        public int eliminarMensajesProcesadosAnterioresA(Instant umbral, int batchSize) {
            procesadosCalls.add(new Call(umbral, batchSize));
            return procesadosReturn;
        }
    }

    private static final class FakeRetentionMetrics extends RetentionMetrics {
        private int filasEliminadas;
        private long duracionMs = -1;
        private int errores;

        private FakeRetentionMetrics() {
            super(new SimpleMeterRegistry());
        }

        @Override
        public void recordRun(int filasEliminadas, long duracionMs) {
            this.filasEliminadas = filasEliminadas;
            this.duracionMs = duracionMs;
        }

        @Override
        public void recordError() {
            errores++;
        }
    }
}
