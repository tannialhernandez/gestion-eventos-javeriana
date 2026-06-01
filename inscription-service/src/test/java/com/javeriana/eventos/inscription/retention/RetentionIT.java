package com.javeriana.eventos.inscription.retention;

import com.javeriana.eventos.inscription.infrastructure.retention.RetentionService;
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

/**
 * Tests de integración para RetentionService en inscription-service.
 *
 * Verifica que el job de retención:
 *  1. Preserva outbox_events ENVIADO recientes (< 30 días).
 *  2. Elimina outbox_events ENVIADO antiguos (> 30 días) en batches.
 *  3. Elimina mensaje_procesado antiguos (> 30 días).
 *  4. Preserva mensaje_procesado recientes (< 30 días).
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
    "retention.max-batches-per-run=10"
})
@DisplayName("RetentionIT — inscription-service")
class RetentionIT {

    // ─── Contenedores ────────────────────────────────────────────────────────────

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("eventos_inscription")
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

    // ─── Setup por test ──────────────────────────────────────────────────────────

    @BeforeEach
    void limpiarTablas() {
        expirarLockRetencion("inscription-RetentionService");
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
                "VALUES (gen_random_uuid(), 'Inscripcion', gen_random_uuid(), " +
                "'INSCRIPCION_CREADA', '{}', 'ENVIADO', 0, NOW(), NOW())"
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
                "VALUES (gen_random_uuid(), 'Inscripcion', gen_random_uuid(), " +
                "'INSCRIPCION_CREADA', '{}', 'ENVIADO', 0, NOW(), NOW() - interval '31 days')"
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
        // Insertar 50 registros de idempotencia con procesado_en hace 31 días
        for (int i = 0; i < 50; i++) {
            jdbcTemplate.update(
                "INSERT INTO mensaje_procesado (message_id, consumer_grupo, tipo_mensaje, procesado_en) " +
                "VALUES (?, 'test-group', 'TEST', NOW() - interval '31 days')",
                UUID.randomUUID().toString()
            );
        }

        retentionService.limpiar();

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mensaje_procesado",
            Integer.class);
        assertThat(count).isEqualTo(0);
    }

    // ─── Test 4: preservar mensaje_procesado recientes ───────────────────────────

    @Test
    @DisplayName("Debe mantener mensaje_procesado recientes (< 30 días)")
    void debeMantenerMensajesProcesadosRecientes() {
        // Insertar 10 registros de idempotencia con procesado_en = NOW()
        for (int i = 0; i < 10; i++) {
            jdbcTemplate.update(
                "INSERT INTO mensaje_procesado (message_id, consumer_grupo, tipo_mensaje, procesado_en) " +
                "VALUES (?, 'test-group', 'TEST', NOW())",
                UUID.randomUUID().toString()
            );
        }

        retentionService.limpiar();

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mensaje_procesado",
            Integer.class);
        assertThat(count).isEqualTo(10);
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
