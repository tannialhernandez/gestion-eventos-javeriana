package com.javeriana.eventos.payment.retention;

import com.javeriana.eventos.payment.infrastructure.retention.RetentionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests de integración para RetentionService en payment-service.
 *
 * Verifica que el job de retención:
 *  1. Preserva outbox_events ENVIADO recientes (< 30 días).
 *  2. Elimina outbox_events ENVIADO antiguos (> 30 días) en batches.
 *  3. Elimina mensaje_procesado antiguos (> 30 días).
 *  4. NUNCA toca pago_audit — tabla inmutable por Ley 1581 Art. 11.
 *
 * CRITICO: el test debeNoEliminarPagoAudit() garantiza cumplimiento legal.
 * pago_audit tiene triggers que bloquean UPDATE y DELETE. El job de retención
 * NO debe intentar borrar filas de esta tabla bajo ninguna circunstancia.
 *
 * Infraestructura real vía Testcontainers: PostgreSQL 15 + RabbitMQ 3-management.
 * retention.enabled=true sobreescribe el perfil test (que lo tiene en false).
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "retention.enabled=true",
    "retention.batch-size=25",
    "retention.max-batches-per-run=10",
    "payment.gateway.provider=simulador"
})
@DisplayName("RetentionIT — payment-service")
class RetentionIT {

    // ─── Contenedores ────────────────────────────────────────────────────────────

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("eventos_payment")
            .withUsername("test_user")
            .withPassword("test_pass");

    @Container
    static final RabbitMQContainer RABBITMQ =
        new RabbitMQContainer("rabbitmq:3-management-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.rabbitmq.host",     RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port",     RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    // ─── Beans Spring ────────────────────────────────────────────────────────────

    @Autowired RetentionService retentionService;
    @Autowired JdbcTemplate     jdbcTemplate;
    @Autowired MeterRegistry    meterRegistry;

    // ─── Setup por test ──────────────────────────────────────────────────────────

    /**
     * Limpia solo las tablas gestionadas por el job de retención.
     * NOTA: pago_audit NO se limpia aquí — tiene triggers que bloquean DELETE.
     * Su inmutabilidad se verifica en debeNoEliminarPagoAudit().
     */
    @BeforeEach
    void limpiarTablas() {
        expirarLockRetencion("payment-RetentionService");
        jdbcTemplate.execute("DELETE FROM outbox_events");
        jdbcTemplate.execute("DELETE FROM mensaje_procesado");
    }

    // ─── Test 1: preservar outbox recientes ──────────────────────────────────────

    @Test
    @DisplayName("Debe mantener outbox_events ENVIADO recientes (< 30 días)")
    void debeMantenerOutboxEnviadosRecientes() {
        // Insertar 5 eventos ENVIADO con enviado_en = NOW() (dentro del período de retención)
        for (int i = 0; i < 5; i++) {
            jdbcTemplate.execute(
                "INSERT INTO outbox_events " +
                "(id, aggregate_type, aggregate_id, event_type, payload, estado, intentos, creado_en, enviado_en) " +
                "VALUES (gen_random_uuid(), 'Pago', gen_random_uuid(), " +
                "'PAGO_CONFIRMADO', '{}', 'ENVIADO', 0, NOW(), NOW())"
            );
        }

        retentionService.limpiar();

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_events WHERE estado = 'ENVIADO'",
            Integer.class);
        assertThat(count).isEqualTo(5);
    }

    // ─── Test 2: eliminar outbox antiguos en batches ──────────────────────────────

    @Test
    @DisplayName("Debe eliminar outbox_events ENVIADO antiguos (> 30 días) en batches")
    void debeEliminarOutboxEnviadosAntiguosBatched() {
        // Insertar 100 eventos ENVIADO con enviado_en hace 31 días (fuera del período)
        for (int i = 0; i < 100; i++) {
            jdbcTemplate.execute(
                "INSERT INTO outbox_events " +
                "(id, aggregate_type, aggregate_id, event_type, payload, estado, intentos, creado_en, enviado_en) " +
                "VALUES (gen_random_uuid(), 'Pago', gen_random_uuid(), " +
                "'PAGO_CONFIRMADO', '{}', 'ENVIADO', 0, NOW(), NOW() - interval '31 days')"
            );
        }

        retentionService.limpiar();

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_events WHERE estado = 'ENVIADO'",
            Integer.class);
        assertThat(count).isEqualTo(0);
    }

    // ─── Test 3: eliminar mensaje_procesado antiguos ──────────────────────────────

    @Test
    @DisplayName("Debe eliminar mensaje_procesado con más de 30 días")
    void debeEliminarMensajesProcesadosAntiguos() {
        // Insertar 50 registros de idempotencia con processed_at hace 31 días
        // Columnas payment-service: message_id, consumer_group, event_type, processed_at
        for (int i = 0; i < 50; i++) {
            jdbcTemplate.update(
                "INSERT INTO mensaje_procesado (message_id, consumer_group, event_type, processed_at) " +
                "VALUES (?, 'test-consumer', 'PAGO_CONFIRMADO', NOW() - interval '31 days')",
                UUID.randomUUID().toString()
            );
        }

        retentionService.limpiar();

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mensaje_procesado",
            Integer.class);
        assertThat(count).isEqualTo(0);
    }

    // ─── Test 4 (CRITICO): pago_audit es inmutable — Ley 1581 ───────────────────

    /**
     * Verifica que el job de retención respeta la inmutabilidad de pago_audit.
     *
     * Ley 1581 de Colombia (Protección de Datos Personales), Art. 11:
     * los datos financieros deben conservarse sin alteración durante el período
     * legal de custodia. pago_audit usa triggers PostgreSQL que bloquean
     * UPDATE y DELETE con una excepción de BD.
     *
     * Este test verifica que limpiar() no lanza ninguna excepción relacionada
     * con intentar borrar de pago_audit. Si RetentionService intentara
     * ejecutar DELETE en esa tabla, el trigger lanzaría una excepción que
     * propagaría como DataAccessException y el test fallaría.
     *
     * Inserta una fila antigua de auditoría y cuenta las filas antes/después
     * para certificar la invariante de no-borrado.
     */
    @Test
    @DisplayName("CRITICO: RetentionService NO elimina pago_audit (Ley 1581 Art. 11)")
    void debeNoEliminarPagoAudit() {
        jdbcTemplate.execute("""
            INSERT INTO pago_audit
                (id, pago_id, inscripcion_id, estado_anterior, estado_nuevo, actor,
                 motivo, metadatos, ocurrido_en, ip_origen)
            VALUES
                (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 'INICIADO',
                 'CONFIRMADO', 'SISTEMA', 'retention guard', '{}',
                 NOW() - interval '6 years', '127.0.0.1')
            """);

        Integer countAntes = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pago_audit",
            Integer.class);
        double erroresAntes = retentionErrors();

        // El job de retención NO debe intentar borrar de pago_audit.
        // Si lo intentara, el trigger lanzaría una excepción que propagaría
        // como DataAccessException — y assertThatCode capturaría el fallo.
        assertThatCode(() -> retentionService.limpiar())
            .doesNotThrowAnyException();

        // Verificar que pago_audit no perdió ninguna fila
        Integer countDespues = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pago_audit",
            Integer.class);
        assertThat(countDespues)
            .as("pago_audit debe preservar todas sus filas tras la ejecución del job de retención (Ley 1581)")
            .isEqualTo(countAntes);
        assertThat(retentionErrors())
            .as("no debe registrarse error de retención por intentar tocar pago_audit")
            .isEqualTo(erroresAntes);
    }

    private double retentionErrors() {
        Counter counter = meterRegistry.find("retention.errors")
            .tag("servicio", "payment-service")
            .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private void expirarLockRetencion(String lockName) {
        jdbcTemplate.update("""
            INSERT INTO shedlock(name, lock_until, locked_at, locked_by)
            VALUES (?, timezone('utc', CURRENT_TIMESTAMP) - interval '1 second',
                    timezone('utc', CURRENT_TIMESTAMP) - interval '2 seconds', 'retention-it')
            ON CONFLICT (name) DO UPDATE
            SET lock_until = timezone('utc', CURRENT_TIMESTAMP) - interval '1 second',
                locked_at = timezone('utc', CURRENT_TIMESTAMP) - interval '2 seconds',
                locked_by = 'retention-it'
            """, lockName);
    }
}
