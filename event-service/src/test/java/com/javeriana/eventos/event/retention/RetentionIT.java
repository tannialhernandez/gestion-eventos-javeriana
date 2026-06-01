package com.javeriana.eventos.event.retention;

import com.javeriana.eventos.event.domain.port.out.OutboxRetentionRepository;
import com.javeriana.eventos.event.infrastructure.retention.RetentionService;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración del job de retención de outbox_events (ADR-022 / retention policy).
 *
 * Verifica que RetentionService.limpiar() respeta correctamente las ventanas de tiempo:
 * 1. Registros ENVIADO recientes NO son eliminados.
 * 2. Registros ENVIADO con más de 30 días SÍ son eliminados (en batches).
 * 3. Registros FALLIDO con ≥10 intentos y más de 90 días SÍ son eliminados.
 *
 * La propiedad retention.enabled=false del perfil "test" se sobreescribe con
 * @TestPropertySource para que Spring cree el bean RetentionService en este contexto.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "retention.enabled=true",
    "retention.batch-size=25",
    "retention.max-batches-per-run=10"
})
@DisplayName("RetentionIT — job de retención de outbox_events")
class RetentionIT {

    // ─── Testcontainers ───────────────────────────────────────────────────────

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("eventos_event_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @Container
    static final RabbitMQContainer RABBITMQ =
        new RabbitMQContainer("rabbitmq:3-management-alpine");

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    // ─── Property overrides ───────────────────────────────────────────────────

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host",       RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port",       RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username",   () -> "guest");
        registry.add("spring.rabbitmq.password",   () -> "guest");
        registry.add("spring.data.redis.host",     REDIS::getHost);
        registry.add("spring.data.redis.port",     () -> REDIS.getMappedPort(6379).toString());
    }

    // ─── Beans ────────────────────────────────────────────────────────────────

    @Autowired
    private RetentionService retentionService;

    @Autowired
    private OutboxRetentionRepository outboxRetentionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ─── Setup ────────────────────────────────────────────────────────────────

    @BeforeEach
    void limpiarTabla() {
        expirarLockRetencion("event-RetentionService");
        jdbcTemplate.execute("DELETE FROM outbox_events");
    }

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("debeMantenerOutboxEnviadosRecientes — 5 registros ENVIADO de ahora deben permanecer")
    void debeMantenerOutboxEnviadosRecientes() {
        // DADO: 5 registros con estado=ENVIADO y enviado_en=ahora (recientes)
        Timestamp ahora = Timestamp.from(Instant.now());
        for (int i = 0; i < 5; i++) {
            jdbcTemplate.update(
                "INSERT INTO outbox_events " +
                "(id, aggregate_type, aggregate_id, event_type, payload, estado, intentos, creado_en, enviado_en) " +
                "VALUES (gen_random_uuid(), 'Evento', gen_random_uuid(), 'EVENTO_PUBLICADO', '{}', ?, ?, ?, ?)",
                "ENVIADO", 1, ahora, ahora
            );
        }

        long antesDeEjecutar = contarFilas();
        assertThat(antesDeEjecutar).isEqualTo(5);

        // CUANDO: se ejecuta el job de limpieza
        retentionService.limpiar();

        // ENTONCES: los 5 registros siguen existiendo (son más recientes que el umbral de 30 días)
        long despuesDeEjecutar = contarFilas();
        assertThat(despuesDeEjecutar)
            .as("Los registros ENVIADO recientes NO deben ser eliminados")
            .isEqualTo(5);
    }

    @Test
    @DisplayName("debeEliminarOutboxEnviadosAntiguosBatched — 100 registros ENVIADO de hace 31 días deben eliminarse")
    void debeEliminarOutboxEnviadosAntiguosBatched() {
        // DADO: 100 registros con estado=ENVIADO y enviado_en=hace 31 días (superan el umbral de 30 días)
        Timestamp hace31Dias = Timestamp.from(Instant.now().minus(31, ChronoUnit.DAYS));
        Timestamp creadoEn   = Timestamp.from(Instant.now().minus(35, ChronoUnit.DAYS));
        for (int i = 0; i < 100; i++) {
            jdbcTemplate.update(
                "INSERT INTO outbox_events " +
                "(id, aggregate_type, aggregate_id, event_type, payload, estado, intentos, creado_en, enviado_en) " +
                "VALUES (gen_random_uuid(), 'Evento', gen_random_uuid(), 'EVENTO_PUBLICADO', '{}', ?, ?, ?, ?)",
                "ENVIADO", 1, creadoEn, hace31Dias
            );
        }

        long antesDeEjecutar = contarFilas();
        assertThat(antesDeEjecutar).isEqualTo(100);

        // CUANDO: se ejecuta el job de limpieza
        retentionService.limpiar();

        // ENTONCES: todos los registros son eliminados (el batch_size=1000 los cubre en un solo batch)
        long despuesDeEjecutar = contarFilas();
        assertThat(despuesDeEjecutar)
            .as("Los 100 registros ENVIADO de hace 31 días deben ser eliminados completamente")
            .isZero();
    }

    @Test
    @DisplayName("debeEliminarOutboxFallidosAntiguosConMuchosIntentos — 50 registros FALLIDO con 10 intentos y 91 días deben eliminarse")
    void debeEliminarOutboxFallidosAntiguosConMuchosIntentos() {
        // DADO: 50 registros con estado=FALLIDO, intentos=10 y creado_en=hace 91 días (superan el umbral de 90 días)
        Timestamp hace91Dias = Timestamp.from(Instant.now().minus(91, ChronoUnit.DAYS));
        for (int i = 0; i < 50; i++) {
            jdbcTemplate.update(
                "INSERT INTO outbox_events " +
                "(id, aggregate_type, aggregate_id, event_type, payload, estado, intentos, creado_en, enviado_en) " +
                "VALUES (gen_random_uuid(), 'Evento', gen_random_uuid(), 'EVENTO_PUBLICADO', '{}', ?, ?, ?, ?)",
                "FALLIDO", 10, hace91Dias, null
            );
        }

        long antesDeEjecutar = contarFilas();
        assertThat(antesDeEjecutar).isEqualTo(50);

        // CUANDO: se ejecuta el job de limpieza
        retentionService.limpiar();

        // ENTONCES: todos los registros son eliminados (FALLIDO + intentos>=10 + más de 90 días)
        long despuesDeEjecutar = contarFilas();
        assertThat(despuesDeEjecutar)
            .as("Los 50 registros FALLIDO con 10 intentos de hace 91 días deben ser eliminados completamente")
            .isZero();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private long contarFilas() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Long.class);
        return count != null ? count : 0L;
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
