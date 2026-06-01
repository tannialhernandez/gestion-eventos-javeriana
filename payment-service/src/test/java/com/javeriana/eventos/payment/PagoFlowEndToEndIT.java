package com.javeriana.eventos.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.payment.domain.model.EstadoPago;
import com.javeriana.eventos.payment.domain.model.Pago;
import com.javeriana.eventos.payment.domain.port.in.CrearPreferenciaUseCase;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase.ResultadoWebhook;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase.WebhookPayload;
import com.javeriana.eventos.payment.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.payment.domain.port.out.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Test E2E del Outbox Pattern en payment-service.
 *
 * Cubre los 4 escenarios que demuestran que el patrón funciona
 * bajo condiciones reales (incluyendo fallos transitorios):
 *
 *  1. CONFIRMADO:  webhook aprobado → payload completo con monto/moneda → BD y cola correctos
 *  2. RECHAZADO:   webhook rechazado → PagoFallidoEvent → cola pago.fallido
 *  3. IDEMPOTENTE: webhook duplicado → exactamente 1 evento, no 2
 *  4. REINTENTO:   fallo transitorio → el relay reintenta y termina publicando
 *
 * Infraestructura real vía TestContainers: PostgreSQL 15 + RabbitMQ 3-management.
 * Sin mocks en capas de persistencia ni mensajería.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class PagoFlowEndToEndIT {

    private static final Logger log = LoggerFactory.getLogger(PagoFlowEndToEndIT.class);

    // ─── Colas de prueba ─────────────────────────────────────────────────────────

    private static final String COLA_CONFIRMADO  = "test.pago.confirmado";
    private static final String COLA_FALLIDO     = "test.pago.fallido";
    private static final String COLA_REEMBOLSADO = "test.pago.reembolsado";
    private static final String EXCHANGE         = "eventos.topic";

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

        registry.add("spring.rabbitmq.host",     RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port",     RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        // virtual-host viene de application-test.yml: "/"

        registry.add("payment.gateway.provider", () -> "simulador");
    }

    // ─── Dependencias Spring ─────────────────────────────────────────────────────

    @Autowired CrearPreferenciaUseCase crearPreferencia;
    @Autowired ProcesarWebhookUseCase  procesarWebhook;
    @Autowired PagoRepository          pagoRepository;
    @Autowired OutboxEventRepository   outboxRepository;
    @Autowired RabbitTemplate          rabbitTemplate;
    @Autowired AmqpAdmin               amqpAdmin;
    @Autowired ObjectMapper            objectMapper;

    // ─── Setup por test ──────────────────────────────────────────────────────────

    @BeforeEach
    void setupColasDePrueba() {
        // El exchange 'eventos.topic' ya está declarado por RabbitMQConfig @Bean.
        // Declaramos colas de prueba (idempotente) y las purgamos entre tests.
        // autoDelete=false garantiza que las colas sobrevivan entre métodos de test.
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);

        declararCola(COLA_CONFIRMADO,  "pago.confirmado",  exchange);
        declararCola(COLA_FALLIDO,     "pago.fallido",     exchange);
        declararCola(COLA_REEMBOLSADO, "pago.reembolsado", exchange);

        // purgeQueue es atómico en RabbitMQ y más fiable que receive-loop
        // cuando publisher-confirm-type=correlated tiene canales en vuelo
        amqpAdmin.purgeQueue(COLA_CONFIRMADO,  false);
        amqpAdmin.purgeQueue(COLA_FALLIDO,     false);
        amqpAdmin.purgeQueue(COLA_REEMBOLSADO, false);
    }

    // ─── Escenario 1: payload completo ───────────────────────────────────────────

    @Test
    @DisplayName("Webhook confirmado: publica payload completo con monto y moneda")
    void webhookConfirmado_publicaPayloadCompleto() throws Exception {
        UUID inscripcionId = UUID.randomUUID();

        log.info("[E2E-1] Creando preferencia para inscripción={}", inscripcionId);
        Pago pago = crearPagoPendiente(inscripcionId, new BigDecimal("150000.00"), "COP");
        assertThat(pago).isNotNull();

        log.info("[E2E-1] Procesando webhook approved para pago={}", pago.getId());
        ResultadoWebhook resultado = procesarWebhook.procesar(
            aprobado(pago, "REF_E2E_CONFIRMADO_001"));
        assertThat(resultado).isEqualTo(ResultadoWebhook.CONFIRMADO);

        log.info("[E2E-1] Esperando publicación del relay (máx 10s)...");
        Message mensaje = consumirMensajeConTimeout(COLA_CONFIRMADO, 10);

        log.info("[E2E-1] Mensaje recibido. Verificando payload...");
        verificarPayloadPagoConfirmado(mensaje, inscripcionId,
                                       new BigDecimal("150000.00"), "COP");

        assertThat(outboxRepository.contarPendientes()).isZero();
        Pago pagoFinal = pagoRepository.buscarPorInscripcionId(inscripcionId).orElseThrow();
        assertThat(pagoFinal.getEstado()).isEqualTo(EstadoPago.CONFIRMADO);
        assertThat(pagoFinal.getReferenciaExterna()).isEqualTo("REF_E2E_CONFIRMADO_001");

        log.info("[E2E-1] ✅ Webhook confirmado con payload completo VERIFICADO");
    }

    // ─── Escenario 2: pago rechazado ─────────────────────────────────────────────

    @Test
    @DisplayName("Webhook rechazado: publica PagoFallidoEvent en cola pago.fallido")
    void webhookRechazado_publicaPagoFallido() throws Exception {
        UUID inscripcionId = UUID.randomUUID();

        log.info("[E2E-2] Creando preferencia para inscripción={}", inscripcionId);
        Pago pago = crearPagoPendiente(inscripcionId, new BigDecimal("80000.00"), "COP");

        log.info("[E2E-2] Procesando webhook rejected para pago={}", pago.getId());
        ResultadoWebhook resultado = procesarWebhook.procesar(
            rechazado(pago, "REF_E2E_RECHAZADO_001"));
        assertThat(resultado).isEqualTo(ResultadoWebhook.RECHAZADO);

        log.info("[E2E-2] Esperando publicación del relay (máx 10s)...");
        Message mensaje = consumirMensajeConTimeout(COLA_FALLIDO, 10);

        log.info("[E2E-2] Mensaje recibido. Verificando payload PagoFallido...");
        verificarPayloadPagoFallido(mensaje, inscripcionId, "RECHAZADO_POR_PASARELA");

        assertThat(outboxRepository.contarPendientes()).isZero();
        Pago pagoFinal = pagoRepository.buscarPorInscripcionId(inscripcionId).orElseThrow();
        assertThat(pagoFinal.getEstado()).isEqualTo(EstadoPago.FALLIDO);

        // Cola de confirmados debe estar vacía — el rechazo NO genera PAGO_CONFIRMADO
        assertThat(rabbitTemplate.receive(COLA_CONFIRMADO, 500))
            .as("No debe haber mensaje de confirmación cuando el pago es rechazado")
            .isNull();

        log.info("[E2E-2] ✅ Webhook rechazado con PagoFallidoEvent VERIFICADO");
    }

    // ─── Escenario 3: idempotencia ───────────────────────────────────────────────

    @Test
    @DisplayName("Webhook duplicado: idempotente — exactamente 1 evento publicado, no 2")
    void webhookDuplicado_esIdempotente() throws Exception {
        UUID inscripcionId = UUID.randomUUID();

        log.info("[E2E-3] Creando preferencia para inscripción={}", inscripcionId);
        Pago pago = crearPagoPendiente(inscripcionId, new BigDecimal("100000.00"), "COP");

        WebhookPayload webhook = aprobado(pago, "REF_E2E_DUPLICADO");

        // Primera llamada: procesada normalmente
        log.info("[E2E-3] Primera llamada al webhook (debe confirmar)");
        ResultadoWebhook primerResultado = procesarWebhook.procesar(webhook);
        assertThat(primerResultado).isEqualTo(ResultadoWebhook.CONFIRMADO);

        // Segunda llamada: misma referencia, debe ser ignorada
        log.info("[E2E-3] Segunda llamada al mismo webhook (debe ser DUPLICADO)");
        ResultadoWebhook segundoResultado = procesarWebhook.procesar(webhook);
        assertThat(segundoResultado).isEqualTo(ResultadoWebhook.DUPLICADO);

        // Exactamente 1 mensaje en la cola
        Message primero = consumirMensajeConTimeout(COLA_CONFIRMADO, 10);
        assertThat(primero).isNotNull();

        Message segundo = rabbitTemplate.receive(COLA_CONFIRMADO, 2_000);
        assertThat(segundo)
            .as("El webhook duplicado NO debe generar un segundo mensaje en la cola")
            .isNull();

        // BD consistente
        assertThat(outboxRepository.contarPendientes()).isZero();
        Pago pagoFinal = pagoRepository.buscarPorInscripcionId(inscripcionId).orElseThrow();
        assertThat(pagoFinal.getEstado()).isEqualTo(EstadoPago.CONFIRMADO);

        log.info("[E2E-3] ✅ Idempotencia verificada: exactamente 1 mensaje publicado");
    }

    // ─── Escenario 4: fallo transitorio y reintento ──────────────────────────────

    @Disabled("""
        Requiere pausar/reanudar el contenedor Docker de RabbitMQ, lo que puede ser
        inestable en entornos CI sin Docker privilegiado (Colima, DinD).

        Para habilitar manualmente:
          1. Ejecutar con Docker Desktop o Colima con acceso completo al API de Docker.
          2. Remover la anotación @Disabled.
          3. Verificar que el test pasa con `mvn failsafe:integration-test` aislado.

        Qué valida este test:
          - El relay intenta publicar, falla (RabbitMQ no disponible), incrementa intentos.
          - Cuando RabbitMQ se recupera, el relay publica en el siguiente ciclo.
          - El evento termina ENVIADO y aparece en la cola.
        """)
    @Test
    @DisplayName("Fallo transitorio de publicación: el relay reintenta y termina publicando")
    void publicacionFallida_seReintentaYConfirma() throws Exception {
        UUID inscripcionId = UUID.randomUUID();

        log.info("[E2E-4] Creando preferencia para inscripción={}", inscripcionId);
        Pago pago = crearPagoPendiente(inscripcionId, new BigDecimal("200000.00"), "COP");

        // Pausar RabbitMQ para forzar fallo en el primer ciclo del relay
        log.info("[E2E-4] Pausando contenedor RabbitMQ...");
        RABBITMQ.getDockerClient()
            .pauseContainerCmd(RABBITMQ.getContainerId()).exec();

        // Webhook → evento queda PENDIENTE; relay fallará al publicar
        procesarWebhook.procesar(aprobado(pago, "REF_E2E_REINTENTO"));

        // Esperar al menos 1 ciclo fallido del relay (fixedDelay=2s)
        Thread.sleep(3_500);

        // Verificar: evento sigue PENDIENTE, intentos >= 1
        assertThat(outboxRepository.contarPendientes())
            .as("El evento debe seguir PENDIENTE mientras RabbitMQ está caído")
            .isGreaterThan(0);

        // Reanudar RabbitMQ
        log.info("[E2E-4] Reanudando contenedor RabbitMQ...");
        RABBITMQ.getDockerClient()
            .unpauseContainerCmd(RABBITMQ.getContainerId()).exec();

        // El relay publica en el siguiente ciclo (hasta 15s)
        Message mensaje = consumirMensajeConTimeout(COLA_CONFIRMADO, 15);
        verificarPayloadPagoConfirmado(mensaje, inscripcionId,
                                       new BigDecimal("200000.00"), "COP");

        assertThat(outboxRepository.contarPendientes()).isZero();
        log.info("[E2E-4] ✅ Fallo transitorio y reintento verificado");
    }

    // ─── Helpers de infraestructura ──────────────────────────────────────────────

    private void declararCola(String nombre, String routingKey, TopicExchange exchange) {
        // autoDelete=false: la cola persiste entre tests para que purgeQueue funcione
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
        return null; // nunca se alcanza; evita error de compilación
    }

    /** Crea un pago en estado PROCESANDO vía el flujo normal (crearPreferencia). */
    private Pago crearPagoPendiente(UUID inscripcionId, BigDecimal monto, String moneda) {
        crearPreferencia.crear(new CrearPreferenciaUseCase.Command(
            inscripcionId, monto, moneda, UUID.randomUUID()));
        return pagoRepository.buscarPorInscripcionId(inscripcionId).orElseThrow();
    }

    // ─── Helpers de construcción de WebhookPayload ───────────────────────────────

    private static WebhookPayload aprobado(Pago pago, String referencia) {
        return new WebhookPayload(referencia, pago.getInscripcionId().toString(),
                                  "approved", "{}");
    }

    private static WebhookPayload rechazado(Pago pago, String referencia) {
        return new WebhookPayload(referencia, pago.getInscripcionId().toString(),
                                  "rejected", "{}");
    }

    // ─── Helpers de aserciones de payload ────────────────────────────────────────

    /**
     * Verifica el payload completo de PagoConfirmadoEvent.
     *
     * eventType() es un método de interfaz DomainEvent (no un componente del record),
     * por lo que Jackson no lo incluye en el JSON del body al serializar el record.
     * Se verifica desde el header AMQP (seteado explícitamente por el relay).
     * Los campos de datos (inscripcionId, monto, moneda, fechas) sí son componentes
     * del record y aparecen en el JSON body.
     */
    private void verificarPayloadPagoConfirmado(Message mensaje,
                                                UUID inscripcionEsperada,
                                                BigDecimal montoEsperado,
                                                String monedaEsperada) throws Exception {
        String body = new String(mensaje.getBody(), StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(body);

        // Campos de datos: componentes del record → están en el JSON body
        assertThat(json.get("inscripcionId").asText())
            .isEqualTo(inscripcionEsperada.toString());
        assertThat(json.get("monto").decimalValue())
            .isEqualByComparingTo(montoEsperado);
        assertThat(json.get("moneda").asText())
            .isEqualTo(monedaEsperada);
        assertThat(json.has("fechaConfirmacion"))
            .as("El payload debe incluir fechaConfirmacion")
            .isTrue();
        assertThat(json.has("referenciaExterna")).isTrue();
        assertThat(json.has("eventId")).isTrue();
        assertThat(json.has("aggregateId")).isTrue();
        assertThat(json.has("occurredAt")).isTrue();

        // Metadatos de enrutamiento: en headers AMQP (seteados por el relay)
        MessageProperties props = mensaje.getMessageProperties();
        assertThat(props.getMessageId())
            .as("messageId AMQP obligatorio para idempotencia del consumidor")
            .isNotNull();
        assertThat(props.getHeader("eventType").toString())
            .isEqualTo("PAGO_CONFIRMADO");
        assertThat(props.getHeader("aggregateType").toString())
            .isEqualTo("Pago");
    }

    private void verificarPayloadPagoFallido(Message mensaje,
                                             UUID inscripcionEsperada,
                                             String motivoEsperado) throws Exception {
        String body = new String(mensaje.getBody(), StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(body);

        // Campos de datos del record PagoFallidoEvent
        assertThat(json.get("inscripcionId").asText())
            .isEqualTo(inscripcionEsperada.toString());
        assertThat(json.get("motivoRechazo").asText())
            .isEqualTo(motivoEsperado);
        assertThat(json.has("monto"))
            .as("El payload de PagoFallido debe incluir monto")
            .isTrue();
        assertThat(json.has("moneda")).isTrue();
        assertThat(json.has("descripcionMotivo")).isTrue();

        MessageProperties props = mensaje.getMessageProperties();
        assertThat(props.getMessageId()).isNotNull();
        assertThat(props.getHeader("eventType").toString())
            .isEqualTo("PAGO_FALLIDO");
        assertThat(props.getHeader("aggregateType").toString())
            .isEqualTo("Pago");
    }
}
