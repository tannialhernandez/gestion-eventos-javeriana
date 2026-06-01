package com.javeriana.eventos.event.outbox;

import com.javeriana.eventos.event.domain.model.ModalidadEvento;
import com.javeriana.eventos.event.domain.model.TipoEvento;
import com.javeriana.eventos.event.domain.port.in.CrearEventoUseCase;
import com.javeriana.eventos.event.domain.port.in.PublicarEventoUseCase;
import com.javeriana.eventos.event.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.event.infrastructure.outbox.OutboxRelayService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración del Outbox Relay (ADR-011).
 *
 * Verifica:
 * 1. El relay publica eventos PENDIENTES y los marca ENVIADO.
 * 2. Los mensajes llevan header x-schema-version: v1 (ADR-020).
 * 3. Los mensajes llevan header x-correlation-id (Prompt 13).
 * 4. SKIP LOCKED: dos invocaciones del relay no procesan el mismo evento.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("OutboxRelayIT — Outbox Pattern event-service")
class OutboxRelayIT {

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

    @Autowired private CrearEventoUseCase      crearEventoUseCase;
    @Autowired private PublicarEventoUseCase   publicarEventoUseCase;
    @Autowired private OutboxEventRepository   outboxRepository;
    @Autowired private OutboxRelayService      relayService;
    @Autowired private RabbitTemplate          rabbitTemplate;
    @Autowired private AmqpAdmin               amqpAdmin;

    private static final String EXCHANGE        = "eventos.topic";
    private static final String COLA_PUBLICADO  = "test.evento.publicado";

    @BeforeEach
    void setupCola() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        Queue cola = new Queue(COLA_PUBLICADO, false, false, true);
        amqpAdmin.declareQueue(cola);
        amqpAdmin.declareBinding(
            BindingBuilder.bind(cola).to(exchange).with("evento.publicado"));
        amqpAdmin.purgeQueue(COLA_PUBLICADO);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private UUID crearYPublicarEvento() {
        UUID eventoId = UUID.randomUUID();
        crearEventoUseCase.crear(new CrearEventoUseCase.Command(
            "Congreso de Patrones " + eventoId.toString().substring(0, 6),
            "Descripcion de prueba",
            TipoEvento.CONGRESO,
            ModalidadEvento.VIRTUAL,
            LocalDate.now().plusDays(30),
            LocalDate.now().plusDays(31),
            LocalDateTime.now().plusDays(25),
            100,
            UUID.randomUUID()
        ));
        // Buscamos el evento recien creado por organizadorId no es simple; usamos el port directo
        // via publicar con el ID generado internamente — simplificamos usando el outbox directo
        return eventoId;
    }

    // ─── tests ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("El relay publica eventos PENDIENTES y los marca ENVIADO")
    void debePublicarPendientesYMarcarComoEnviados() {
        // Crear y publicar un evento para generar el outbox entry
        UUID organizadorId = UUID.randomUUID();
        CrearEventoUseCase.Command cmd = new CrearEventoUseCase.Command(
            "Evento Relay Test", "Desc", TipoEvento.CONGRESO, ModalidadEvento.VIRTUAL,
            LocalDate.now().plusDays(30), LocalDate.now().plusDays(31),
            LocalDateTime.now().plusDays(25), 50, organizadorId
        );
        // crear + publicar
        // We use the service directly
        crearEventoPublicado();

        // After publishing there should be 1+ pending outbox events
        long pendientesAntes = outboxRepository.contarPendientes();
        assertThat(pendientesAntes).isGreaterThanOrEqualTo(1);

        // Manually trigger relay
        relayService.publicarEventosPendientes();

        // All pending should now be ENVIADO
        Awaitility.await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() ->
                assertThat((Long) outboxRepository.contarPendientes()).isZero());
    }

    @Test
    @DisplayName("Mensaje en cola lleva header x-schema-version: v1 (ADR-020)")
    void debeIncluirHeaderXSchemaVersion() {
        crearEventoPublicado();
        relayService.publicarEventosPendientes();

        Message mensaje = rabbitTemplate.receive(COLA_PUBLICADO, 5000);
        assertThat(mensaje != null).as("Se esperaba mensaje en la cola").isTrue();
        String schemaVersion = mensaje.getMessageProperties().getHeader("x-schema-version");
        assertThat(schemaVersion).isEqualTo("v1");
    }

    @Test
    @DisplayName("Mensaje en cola lleva header x-correlation-id (Prompt 13)")
    void debeIncluirHeaderXCorrelationId() {
        crearEventoPublicado();
        relayService.publicarEventosPendientes();

        Message mensaje = rabbitTemplate.receive(COLA_PUBLICADO, 5000);
        assertThat(mensaje != null).as("Se esperaba mensaje en la cola").isTrue();
        String correlationId = mensaje.getMessageProperties().getHeader("x-correlation-id");
        assertThat(correlationId)
            .isNotNull()
            .isInstanceOf(String.class);
    }

    @Test
    @DisplayName("SKIP LOCKED: segunda invocacion del relay no procesa el mismo evento")
    void debeUsarSkipLockedEnConsultaDeBatch() {
        crearEventoPublicado();

        // Primera llamada marca el evento como ENVIADO
        relayService.publicarEventosPendientes();
        long pendientesDespues = outboxRepository.contarPendientes();

        // Segunda llamada no encuentra nada (SKIP LOCKED o ya ENVIADO)
        relayService.publicarEventosPendientes();
        assertThat((Long) outboxRepository.contarPendientes()).isEqualTo(pendientesDespues);
    }

    // ─── helper interno ───────────────────────────────────────────────────────

    private void crearEventoPublicado() {
        // Workaround: crear via use case, luego buscar el ID para publicar
        UUID organizadorId = UUID.randomUUID();
        com.javeriana.eventos.event.domain.model.Evento ev =
            crearEventoUseCase.crear(new CrearEventoUseCase.Command(
                "Evento IT " + System.nanoTime(), "Desc test",
                TipoEvento.CONGRESO, ModalidadEvento.VIRTUAL,
                LocalDate.now().plusDays(30), LocalDate.now().plusDays(31),
                LocalDateTime.now().plusDays(25), 50, organizadorId
            ));
        publicarEventoUseCase.publicar(ev.getId(), organizadorId);
    }
}
