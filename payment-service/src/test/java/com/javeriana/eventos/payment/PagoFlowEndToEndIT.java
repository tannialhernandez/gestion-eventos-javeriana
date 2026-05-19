package com.javeriana.eventos.payment;

import com.javeriana.eventos.payment.domain.model.EstadoPago;
import com.javeriana.eventos.payment.domain.port.in.CrearPreferenciaUseCase;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase.ResultadoWebhook;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase.WebhookPayload;
import com.javeriana.eventos.payment.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.payment.domain.port.out.PagoRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test E2E del flujo inscripción ↔ pago vía Outbox Pattern.
 *
 * Demuestra (docs/comportamiento-runtime-inscripcion-pago.md §7):
 *  - ProcesarWebhookService persiste PagoConfirmadoEvent en outbox dentro de la transacción.
 *  - OutboxRelayService publica el evento en RabbitMQ dentro de 2s (fixedDelay=2000).
 *  - Idempotencia: webhook duplicado NO genera segundo evento.
 *
 * Infraestructura real vía TestContainers: PostgreSQL 15 + RabbitMQ 3-management.
 * Sin mocks en las capas de persistencia ni mensajería.
 */
@SpringBootTest
@Testcontainers
class PagoFlowEndToEndIT {

    // ─── Contenedores (compartidos por todos los tests de esta clase) ────────────

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        registry.add("spring.rabbitmq.host",         RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port",         RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username",     () -> "guest");
        registry.add("spring.rabbitmq.password",     () -> "guest");
        registry.add("spring.rabbitmq.virtual-host", () -> "/");

        // Usar adaptador simulado — sin llamadas HTTP a MercadoPago
        registry.add("payment.gateway.provider", () -> "simulador");
    }

    // ─── Dependencias Spring ─────────────────────────────────────────────────────

    @Autowired CrearPreferenciaUseCase crearPreferencia;
    @Autowired ProcesarWebhookUseCase  procesarWebhook;
    @Autowired PagoRepository          pagoRepository;
    @Autowired OutboxEventRepository   outboxRepository;
    @Autowired RabbitTemplate          rabbitTemplate;
    @Autowired AmqpAdmin               amqpAdmin;

    private static final String QUEUE_PAGO_CONFIRMADO = "pago.confirmado.test";
    private static final String EXCHANGE              = "eventos.topic";

    @BeforeEach
    void declararColaDeTest() {
        // Cola temporal solo para este test; no afecta la topología de producción
        Queue queue = new Queue(QUEUE_PAGO_CONFIRMADO, false, false, true);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(
            BindingBuilder.bind(queue)
                .to(new TopicExchange(EXCHANGE))
                .with("pago.confirmado")
        );
    }

    // ─── Escenario 1: Happy path ─────────────────────────────────────────────────

    @Test
    @DisplayName("Happy path: webhook confirmado → outbox publicado → mensaje en cola")
    void webhookConfirmado_publicaEnRabbitMQ() {
        UUID inscripcionId = UUID.randomUUID();

        // ── Paso 1: crear preferencia (pago en estado INICIADO/PROCESANDO) ──────
        System.out.println("[E2E] Paso 1: crear preferencia para inscripción " + inscripcionId);
        CrearPreferenciaUseCase.Result preferencia = crearPreferencia.crear(
            new CrearPreferenciaUseCase.Command(inscripcionId, new BigDecimal("50000"), "COP", UUID.randomUUID())
        );
        assertThat(preferencia.pagoId()).isNotNull();
        assertThat(preferencia.checkoutUrl()).contains("/simulador/pagos/");
        System.out.println("[E2E] Preferencia creada: pagoId=" + preferencia.pagoId());

        // ── Paso 2: procesar webhook de confirmación ─────────────────────────────
        System.out.println("[E2E] Paso 2: procesar webhook 'approved'");
        ResultadoWebhook resultado = procesarWebhook.procesar(
            new WebhookPayload("MP-REF-001", inscripcionId.toString(), "approved", "{}")
        );
        assertThat(resultado).isEqualTo(ResultadoWebhook.CONFIRMADO);

        // ── Paso 3: verificar pago en BD → CONFIRMADO ────────────────────────────
        System.out.println("[E2E] Paso 3: verificar estado del pago en BD");
        var pago = pagoRepository.buscarPorInscripcionId(inscripcionId);
        assertThat(pago).isPresent();
        assertThat(pago.get().getEstado()).isEqualTo(EstadoPago.CONFIRMADO);
        assertThat(pago.get().getReferenciaExterna()).isEqualTo("MP-REF-001");
        System.out.println("[E2E] Pago CONFIRMADO en BD ✓");

        // ── Paso 4: esperar que OutboxRelayService publique (fixedDelay=2s) ───────
        System.out.println("[E2E] Paso 4: esperando publicación del Outbox Relay (máx 10s)...");
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> outboxRepository.buscarNoPublicados().isEmpty());
        System.out.println("[E2E] Outbox vacío — todos los eventos publicados ✓");

        // ── Paso 5: verificar mensaje en RabbitMQ ────────────────────────────────
        System.out.println("[E2E] Paso 5: verificar mensaje en cola pago.confirmado");
        var mensaje = rabbitTemplate.receive(QUEUE_PAGO_CONFIRMADO, 3_000);
        assertThat(mensaje).isNotNull();
        assertThat(mensaje.getMessageProperties().getMessageId()).isNotNull();
        System.out.println("[E2E] Mensaje recibido en RabbitMQ ✓ messageId=" +
            mensaje.getMessageProperties().getMessageId());
        System.out.println("[E2E] ✅ Happy path COMPLETO");
    }

    // ─── Escenario 2: Idempotencia de webhook ───────────────────────────────────

    @Test
    @DisplayName("Idempotencia: webhook duplicado NO genera segundo evento en outbox")
    void webhookDuplicado_esIdempotente() {
        UUID inscripcionId = UUID.randomUUID();

        // Crear pago
        crearPreferencia.crear(
            new CrearPreferenciaUseCase.Command(inscripcionId, new BigDecimal("75000"), "COP", UUID.randomUUID())
        );

        WebhookPayload webhook = new WebhookPayload("MP-REF-DUP", inscripcionId.toString(), "approved", "{}");

        // ── Primera llamada: procesado normalmente ───────────────────────────────
        System.out.println("[E2E] Idempotencia — Primera llamada al webhook");
        ResultadoWebhook primerResultado = procesarWebhook.procesar(webhook);
        assertThat(primerResultado).isEqualTo(ResultadoWebhook.CONFIRMADO);

        long eventosTrasPrimera = outboxRepository.buscarNoPublicados().size();

        // ── Segunda llamada: misma referencia externa ────────────────────────────
        System.out.println("[E2E] Idempotencia — Segunda llamada (duplicado)");
        ResultadoWebhook segundoResultado = procesarWebhook.procesar(webhook);
        assertThat(segundoResultado).isEqualTo(ResultadoWebhook.DUPLICADO);

        long eventosTrasDuplicado = outboxRepository.buscarNoPublicados().size();

        // El duplicado NO debe agregar eventos al outbox
        assertThat(eventosTrasDuplicado).isEqualTo(eventosTrasPrimera);
        System.out.println("[E2E] ✅ Idempotencia verificada: no se crearon eventos adicionales");
    }
}
