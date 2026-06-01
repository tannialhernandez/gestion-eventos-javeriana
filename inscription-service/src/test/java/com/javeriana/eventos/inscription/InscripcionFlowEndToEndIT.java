package com.javeriana.eventos.inscription;

import com.javeriana.eventos.inscription.domain.model.EstadoInscripcion;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.in.ConfirmarInscripcionUseCase;
import com.javeriana.eventos.inscription.domain.port.in.CrearInscripcionUseCase;
import com.javeriana.eventos.inscription.domain.port.in.ExpirarInscripcionesUseCase;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort;
import com.javeriana.eventos.inscription.domain.port.out.InscripcionRepository;
import com.javeriana.eventos.inscription.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.inscription.domain.port.out.PaymentServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.awaitility.Awaitility;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Tests E2E del flujo de inscripciones con Outbox Pattern.
 *
 * Cubre los 4 escenarios clave:
 *  1. Crear inscripción → reserva cupo atómicamente (SELECT FOR UPDATE)
 *  2. Confirmar inscripción → publica InscripcionConfirmadaEvent al outbox y RabbitMQ
 *  3. Expirar inscripción → publica InscripcionExpiradaEvent al outbox y RabbitMQ
 *  4. Idempotencia → misma idempotencyKey retorna la inscripción existente
 *
 * Los puertos Feign (EventoServicePort, PaymentServicePort) se mockean para
 * evitar llamadas HTTP reales a event-service y payment-service en tests.
 *
 * Infraestructura real: PostgreSQL 15 + RabbitMQ 3-management vía Testcontainers.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class InscripcionFlowEndToEndIT {

    private static final Logger log = LoggerFactory.getLogger(InscripcionFlowEndToEndIT.class);

    // ─── Colas de prueba ─────────────────────────────────────────────────────────

    private static final String COLA_CREADA      = "test.inscripcion.creada";
    private static final String COLA_CONFIRMADA  = "test.inscripcion.confirmada";
    private static final String COLA_EXPIRADA    = "test.inscripcion.expirada";
    private static final String EXCHANGE         = "eventos.topic";

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
        // virtual-host viene de application-test.yml: "/"
    }

    // ─── Mocks de puertos Feign ──────────────────────────────────────────────────

    @MockBean EventoServicePort   eventoService;
    @MockBean PaymentServicePort  paymentService;

    // ─── Beans Spring ────────────────────────────────────────────────────────────

    @Autowired CrearInscripcionUseCase      crearInscripcion;
    @Autowired ConfirmarInscripcionUseCase  confirmarInscripcion;
    @Autowired ExpirarInscripcionesUseCase  expirarInscripciones;
    @Autowired InscripcionRepository        inscripcionRepository;
    @Autowired OutboxEventRepository        outboxRepository;
    @Autowired RabbitTemplate               rabbitTemplate;
    @Autowired AmqpAdmin                    amqpAdmin;
    @Autowired JdbcTemplate                 jdbcTemplate;

    // ─── Setup por test ──────────────────────────────────────────────────────────

    @BeforeEach
    void setupColasDePrueba() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        declararCola(COLA_CREADA,     "inscripcion.creada",     exchange);
        declararCola(COLA_CONFIRMADA, "inscripcion.confirmada", exchange);
        declararCola(COLA_EXPIRADA,   "inscripcion.expirada",   exchange);

        amqpAdmin.purgeQueue(COLA_CREADA,     false);
        amqpAdmin.purgeQueue(COLA_CONFIRMADA, false);
        amqpAdmin.purgeQueue(COLA_EXPIRADA,   false);
    }

    // ─── Escenario 1: crear inscripción con reserva de cupo ─────────────────────

    @Test
    @DisplayName("Crear inscripción: reserva cupo + publica INSCRIPCION_CREADA al outbox")
    void crearInscripcion_reservaCupoYPublicaEventoCreada() throws Exception {
        UUID eventoId  = setupEventoConCupo(5);
        UUID usuarioId = UUID.randomUUID();

        log.info("[E2E-INS-1] Creando inscripción para evento={}", eventoId);
        Inscripcion inscripcion = crearInscripcionPendiente(usuarioId, eventoId);

        assertThat(inscripcion.getId()).isNotNull();
        assertThat(inscripcion.getEstado()).isEqualTo(EstadoInscripcion.PENDIENTE_PAGO);
        assertThat(inscripcion.getFechaExpiracionPago()).isNotNull();

        // Verificar que el cupo fue decrementado en BD
        Integer cupoActual = jdbcTemplate.queryForObject(
            "SELECT cupo_disponible FROM evento_cupo WHERE evento_id = ?",
            Integer.class, eventoId);
        assertThat(cupoActual).isEqualTo(4);

        // ADR-020 (Prompt 15): INSCRIPCION_CREADA se publica para notification-service
        log.info("[E2E-INS-1] Esperando publicación de INSCRIPCION_CREADA (máx 15s)...");
        Message mensaje = consumirMensajeConTimeout(COLA_CREADA, 15);

        assertThat(mensaje.getMessageProperties().getMessageId()).isNotNull();
        assertThat(mensaje.getMessageProperties().getHeader("eventType").toString())
            .isEqualTo("INSCRIPCION_CREADA");
        assertThat(mensaje.getMessageProperties().getHeader("x-schema-version").toString())
            .isEqualTo("v1");

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> outboxRepository.contarPendientes() == 0);
        log.info("[E2E-INS-1] ✅ Inscripción creada, cupo decrementado de 5 a 4, INSCRIPCION_CREADA publicado");
    }

    // ─── Escenario 2: confirmar inscripción ──────────────────────────────────────

    @Test
    @DisplayName("Confirmar inscripción: publica InscripcionConfirmadaEvent al outbox y RabbitMQ")
    void confirmarInscripcion_publicaEventoConfirmada() throws Exception {
        UUID eventoId  = setupEventoConCupo(3);
        UUID usuarioId = UUID.randomUUID();

        log.info("[E2E-INS-2] Creando y confirmando inscripción para evento={}", eventoId);
        Inscripcion inscripcion = crearInscripcionPendiente(usuarioId, eventoId);
        confirmarInscripcion.confirmar(inscripcion.getId(), "REF-TEST-CONFIRMAR-001");

        log.info("[E2E-INS-2] Esperando publicación del relay (máx 15s)...");
        Message mensaje = consumirMensajeConTimeout(COLA_CONFIRMADA, 15);

        log.info("[E2E-INS-2] Mensaje recibido. Verificando...");
        assertThat(mensaje.getMessageProperties().getMessageId()).isNotNull();
        assertThat(mensaje.getMessageProperties().getHeader("eventType").toString())
            .isEqualTo("INSCRIPCION_CONFIRMADA");
        assertThat(mensaje.getMessageProperties().getHeader("aggregateType").toString())
            .isEqualTo("Inscripcion");

        // Verificar BD
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> outboxRepository.contarPendientes() == 0);
        Inscripcion inscripcionFinal = inscripcionRepository.buscarPorId(inscripcion.getId()).orElseThrow();
        assertThat(inscripcionFinal.getEstado()).isEqualTo(EstadoInscripcion.CONFIRMADA);
        assertThat(inscripcionFinal.getCodigoQr()).startsWith("QR-");

        // No debe haber mensaje en cola de expiradas
        assertThat(rabbitTemplate.receive(COLA_EXPIRADA, 500))
            .as("No debe haber evento de expiración para una inscripción confirmada")
            .isNull();

        log.info("[E2E-INS-2] ✅ InscripcionConfirmadaEvent publicado correctamente");
    }

    // ─── Escenario 3: expirar inscripción ────────────────────────────────────────

    @Test
    @DisplayName("Expirar inscripción: publica InscripcionExpiradaEvent al outbox y RabbitMQ")
    void expirarInscripcion_publicaEventoExpirada() throws Exception {
        UUID eventoId  = setupEventoConCupo(2);
        UUID usuarioId = UUID.randomUUID();

        log.info("[E2E-INS-3] Creando inscripción y forzando expiración...");
        Inscripcion inscripcion = crearInscripcionPendiente(usuarioId, eventoId);

        // Retroceder fechaExpiracionPago al pasado para que el job la encuentre
        jdbcTemplate.update(
            "UPDATE inscripcion SET fecha_expiracion_pago = NOW() - INTERVAL '30 minutes' WHERE id = ?",
            inscripcion.getId());

        log.info("[E2E-INS-3] Ejecutando servicio de expiración...");
        int expiradas = expirarInscripciones.expirarVencidas();
        assertThat(expiradas).isGreaterThanOrEqualTo(1);

        log.info("[E2E-INS-3] Esperando publicación del relay (máx 15s)...");
        Message mensaje = consumirMensajeConTimeout(COLA_EXPIRADA, 15);

        log.info("[E2E-INS-3] Mensaje recibido. Verificando...");
        assertThat(mensaje.getMessageProperties().getMessageId()).isNotNull();
        assertThat(mensaje.getMessageProperties().getHeader("eventType").toString())
            .isEqualTo("INSCRIPCION_EXPIRADA");
        assertThat(mensaje.getMessageProperties().getHeader("aggregateType").toString())
            .isEqualTo("Inscripcion");

        // Verificar BD
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> outboxRepository.contarPendientes() == 0);
        Inscripcion inscripcionFinal = inscripcionRepository.buscarPorId(inscripcion.getId()).orElseThrow();
        assertThat(inscripcionFinal.getEstado()).isEqualTo(EstadoInscripcion.EXPIRADA);

        // eventoService.liberarCupo() debe haber sido llamado (cupo liberado en event-service)
        Mockito.verify(eventoService, Mockito.atLeastOnce()).liberarCupo(eq(eventoId));

        log.info("[E2E-INS-3] ✅ InscripcionExpiradaEvent publicado y cupo liberado");
    }

    // ─── Escenario 4: idempotencia de creación ───────────────────────────────────

    @Test
    @DisplayName("Crear inscripción idempotente: misma idempotencyKey retorna existente")
    void crearInscripcion_idempotente_retornaExistente() {
        UUID eventoId       = setupEventoConCupo(10);
        UUID usuarioId      = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID tarifaId       = UUID.randomUUID();

        CrearInscripcionUseCase.Command command = new CrearInscripcionUseCase.Command(
            usuarioId, eventoId, tarifaId, idempotencyKey);

        Mockito.when(paymentService.crearPreferencia(any(), any(), any(), any()))
            .thenReturn(new PaymentServicePort.PreferenciaPago(
                UUID.randomUUID(), "https://pagos.test/checkout-idem", "PREF-IDEM"));

        log.info("[E2E-INS-4] Primera creación (debe insertar)...");
        CrearInscripcionUseCase.Result primera = crearInscripcion.crear(command);
        assertThat(primera.inscripcion().getIdempotencyKey()).isEqualTo(idempotencyKey);

        // El cupo solo se decrementa UNA vez
        Integer cupoDespuesPrimera = jdbcTemplate.queryForObject(
            "SELECT cupo_disponible FROM evento_cupo WHERE evento_id = ?",
            Integer.class, eventoId);
        assertThat(cupoDespuesPrimera).isEqualTo(9);

        log.info("[E2E-INS-4] Segunda creación con misma clave (debe retornar existente)...");
        CrearInscripcionUseCase.Result segunda = crearInscripcion.crear(command);
        assertThat(segunda.inscripcion().getId()).isEqualTo(primera.inscripcion().getId());

        // El cupo NO debe decrementarse una segunda vez
        Integer cupoDespuesSegunda = jdbcTemplate.queryForObject(
            "SELECT cupo_disponible FROM evento_cupo WHERE evento_id = ?",
            Integer.class, eventoId);
        assertThat(cupoDespuesSegunda)
            .as("El cupo no debe decrementarse en la segunda llamada (idempotente)")
            .isEqualTo(9);

        log.info("[E2E-INS-4] ✅ Idempotencia verificada: mismo id, cupo intacto");
    }

    // ─── Helpers de infraestructura ──────────────────────────────────────────────

    private void declararCola(String nombre, String routingKey, TopicExchange exchange) {
        Queue q = new Queue(nombre, false, false, false);
        amqpAdmin.declareQueue(q);
        amqpAdmin.declareBinding(BindingBuilder.bind(q).to(exchange).with(routingKey));
    }

    private Message consumirMensajeConTimeout(String cola, int segundosTimeout) {
        long deadline = System.currentTimeMillis() + (long) segundosTimeout * 1_000;
        while (System.currentTimeMillis() < deadline) {
            Message mensaje = rabbitTemplate.receive(cola, 500);
            if (mensaje != null) return mensaje;
        }
        fail("No se recibió mensaje en cola '" + cola + "' tras " + segundosTimeout + "s");
        return null;
    }

    /**
     * Inserta un registro en evento_cupo y configura el mock de eventoService
     * para responder correctamente a las llamadas del servicio de aplicación.
     */
    private UUID setupEventoConCupo(int cupoDisponible) {
        UUID eventoId = UUID.randomUUID();

        jdbcTemplate.update(
            "INSERT INTO evento_cupo (evento_id, cupo_disponible, cupo_maximo, version) VALUES (?, ?, ?, ?)",
            eventoId, cupoDisponible, cupoDisponible, 0);

        Mockito.when(eventoService.obtenerEvento(eventoId))
            .thenReturn(new EventoServicePort.EventoInfo(
                eventoId, "Conferencia Test", "ACTIVO", cupoDisponible, true));

        // M-03: mock de tarifa con monto real (no el 100.000 hardcodeado anterior)
        Mockito.when(eventoService.obtenerTarifa(any()))
            .thenReturn(new EventoServicePort.TarifaInfo(
                UUID.randomUUID(), new java.math.BigDecimal("150000.00"), "COP", "Tarifa general"));

        Mockito.doNothing().when(eventoService).liberarCupo(eventoId);

        return eventoId;
    }

    /** Crea una inscripción en estado PROCESANDO vía el flujo normal. */
    private Inscripcion crearInscripcionPendiente(UUID usuarioId, UUID eventoId) {
        UUID tarifaId       = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();

        Mockito.when(paymentService.crearPreferencia(any(), any(), any(), any()))
            .thenReturn(new PaymentServicePort.PreferenciaPago(
                UUID.randomUUID(), "https://pagos.test/checkout", "PREF-TEST-001"));

        CrearInscripcionUseCase.Result result = crearInscripcion.crear(
            new CrearInscripcionUseCase.Command(usuarioId, eventoId, tarifaId, idempotencyKey));

        return result.inscripcion();
    }
}
