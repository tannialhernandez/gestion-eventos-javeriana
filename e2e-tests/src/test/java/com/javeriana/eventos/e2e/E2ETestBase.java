package com.javeriana.eventos.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Clase base para smoke tests E2E del sistema completo.
 *
 * Levanta el stack completo usando Testcontainers:
 *   - PostgreSQL 15 (3 instancias: una por servicio)
 *   - RabbitMQ 3-management (compartido)
 *   - Redis 7 (para event-service)
 *   - Los 3 microservicios (construidos como imágenes Docker locales)
 *
 * PRERREQUISITO:
 *   mvn package -DskipTests -pl shared,event-service,inscription-service,payment-service
 *
 * Los servicios se comunican en una red Docker compartida.
 * event-service y inscription-service usan el perfil 'local' con credenciales de test.
 */
@Testcontainers
public abstract class E2ETestBase {

    protected static final Logger log = LoggerFactory.getLogger(E2ETestBase.class);

    // ─── Clave pública RSA (inscription-service la valida)
    protected static final String JWT_PUBLIC_KEY =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4C3rkbm3lVOmdTFJH/fG" +
        "TNz1Fk+ScJIu+SkFXJdu8D9C10X9Bmpj97owkfgX2zGbZ+jDjbvwCIDkIKxW1Kzg" +
        "deGxEWSnYUBCH5fakqW1f6eA1B08yoWT1WzAB46CI6Dlu9Fd965/zN1tWzzFrpvmr" +
        "mqZDpm5fVsCxsDSrRrKhO36wMJVpXMFxRNxmYIbQmw/CsKCA4oJdTaC0wKF86s5p3" +
        "sneqs4tH/eTNhjyrbpufYkS6aDWOxSxtqhX5C1CRqF+65QoLyvRlTocLs69O+XRQo" +
        "DYxa5teZM77stmLMqAjZCtDVpSnNaYknsaqQnS9AfPiYAKJ3dd9ql/jYF5UtF9QIDAQAB";

    // ─── Network compartida ───────────────────────────────────────────────────

    protected static final Network RED_E2E = Network.newNetwork();

    // ─── Infraestructura ──────────────────────────────────────────────────────

    protected static final PostgreSQLContainer<?> DB_EVENT =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withNetwork(RED_E2E).withNetworkAliases("db-event")
            .withDatabaseName("eventos_event").withUsername("e2e").withPassword("e2e");

    protected static final PostgreSQLContainer<?> DB_INSCRIPTION =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withNetwork(RED_E2E).withNetworkAliases("db-inscription")
            .withDatabaseName("eventos_inscription").withUsername("e2e").withPassword("e2e");

    protected static final PostgreSQLContainer<?> DB_PAYMENT =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withNetwork(RED_E2E).withNetworkAliases("db-payment")
            .withDatabaseName("eventos_payment").withUsername("e2e").withPassword("e2e");

    protected static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine")
            .withNetwork(RED_E2E).withNetworkAliases("redis-e2e")
            .withExposedPorts(6379);

    protected static final RabbitMQContainer RABBITMQ =
        new RabbitMQContainer("rabbitmq:3-management-alpine")
            .withNetwork(RED_E2E).withNetworkAliases("rabbitmq-e2e");

    // ─── Microservicios ───────────────────────────────────────────────────────

    protected static GenericContainer<?> eventService;
    protected static GenericContainer<?> paymentService;
    protected static GenericContainer<?> inscriptionService;

    // ─── Clientes ─────────────────────────────────────────────────────────────

    protected static WebClient eventClient;
    protected static WebClient paymentClient;
    protected static WebClient inscriptionClient;
    protected static RabbitTemplate rabbitTemplate;
    protected static AmqpAdmin amqpAdmin;

    @BeforeAll
    static void arrancarStack() {
        // 1. Infraestructura en paralelo
        log.info("[e2e] Arrancando infraestructura...");
        DB_EVENT.start();
        DB_INSCRIPTION.start();
        DB_PAYMENT.start();
        REDIS.start();
        RABBITMQ.start();

        // 2. Microservicios en orden (event → payment → inscription)
        log.info("[e2e] Arrancando event-service...");
        eventService = crearContenedorServicio(
            "event-service", 8082,
            new String[]{
                "SPRING_PROFILES_ACTIVE=local",
                "POSTGRES_USER=e2e", "POSTGRES_PASSWORD=e2e",
                "DB_HOST=db-event", "DB_PORT=5432", "DB_NAME=eventos_event",
                "SPRING_DATASOURCE_URL=jdbc:postgresql://db-event:5432/eventos_event",
                "SPRING_DATASOURCE_USERNAME=e2e", "SPRING_DATASOURCE_PASSWORD=e2e",
                "REDIS_HOST=redis-e2e", "REDIS_PORT=6379",
                "RABBITMQ_HOST=rabbitmq-e2e", "RABBITMQ_USER=guest", "RABBITMQ_PASSWORD=guest",
                "JWT_PUBLIC_KEY=" + JWT_PUBLIC_KEY,
                "RETENTION_ENABLED=false"
            });
        eventService.start();

        log.info("[e2e] Arrancando payment-service...");
        paymentService = crearContenedorServicio(
            "payment-service", 8084,
            new String[]{
                "SPRING_PROFILES_ACTIVE=local",
                "POSTGRES_USER=e2e", "POSTGRES_PASSWORD=e2e",
                "SPRING_DATASOURCE_URL=jdbc:postgresql://db-payment:5432/eventos_payment",
                "SPRING_DATASOURCE_USERNAME=e2e", "SPRING_DATASOURCE_PASSWORD=e2e",
                "RABBITMQ_HOST=rabbitmq-e2e", "RABBITMQ_USER=guest", "RABBITMQ_PASSWORD=guest",
                "PAYMENT_GATEWAY_PROVIDER=simulador",
                "PAYMENT_WEBHOOK_SECRET=e2e-secret",
                "RETENTION_ENABLED=false",
                "API_VERSION=1.44"
            });
        paymentService.start();

        log.info("[e2e] Arrancando inscription-service...");
        inscriptionService = crearContenedorServicio(
            "inscription-service", 8083,
            new String[]{
                "SPRING_PROFILES_ACTIVE=local",
                "POSTGRES_USER=e2e", "POSTGRES_PASSWORD=e2e",
                "SPRING_DATASOURCE_URL=jdbc:postgresql://db-inscription:5432/eventos_inscription",
                "SPRING_DATASOURCE_USERNAME=e2e", "SPRING_DATASOURCE_PASSWORD=e2e",
                "RABBITMQ_HOST=rabbitmq-e2e", "RABBITMQ_USER=guest", "RABBITMQ_PASSWORD=guest",
                "EVENT_SERVICE_URL=http://event-service:8082",
                "PAYMENT_SERVICE_URL=http://payment-service:8084",
                "JWT_PUBLIC_KEY=" + JWT_PUBLIC_KEY,
                "RETENTION_ENABLED=false",
                "API_VERSION=1.44"
            });
        inscriptionService.start();

        // 3. WebClients
        eventClient      = crearWebClient(eventService.getMappedPort(8082));
        paymentClient    = crearWebClient(paymentService.getMappedPort(8084));
        inscriptionClient = crearWebClient(inscriptionService.getMappedPort(8083));

        // 4. AMQP para verificar mensajes publicados
        CachingConnectionFactory cf = new CachingConnectionFactory(
            RABBITMQ.getHost(), RABBITMQ.getAmqpPort());
        cf.setUsername("guest");
        cf.setPassword("guest");
        rabbitTemplate = new RabbitTemplate(cf);
        amqpAdmin = new RabbitAdmin(cf);

        // 5. Awaitility defaults
        Awaitility.setDefaultTimeout(Duration.ofSeconds(30));
        Awaitility.setDefaultPollInterval(Duration.ofMillis(500));

        log.info("[e2e] Stack E2E listo. event:{} inscription:{} payment:{}",
            eventService.getMappedPort(8082),
            inscriptionService.getMappedPort(8083),
            paymentService.getMappedPort(8084));
    }

    // ─── Helpers de infraestructura ───────────────────────────────────────────

    private static GenericContainer<?> crearContenedorServicio(
            String servicio, int puerto, String[] envVars) {

        File jar = encontrarJar(servicio);
        File dockerfile = new File("../" + servicio + "/Dockerfile");

        return new GenericContainer<>(
            new ImageFromDockerfile(servicio + ":e2e", false)
                .withDockerfileFromBuilder(builder ->
                    builder.from("eclipse-temurin:17-jre")
                        .workDir("/app")
                        .copy("app.jar", "app.jar")
                        .entryPoint("java", "-Djava.security.egd=file:/dev/./urandom",
                            "-XX:+UseContainerSupport", "-jar", "app.jar")
                        .build())
                .withFileFromFile("app.jar", jar))
            .withNetwork(RED_E2E)
            .withNetworkAliases(servicio)
            .withEnv(construirEnvMap(envVars))
            .withExposedPorts(puerto)
            .waitingFor(Wait.forHttp("/actuator/health").forPort(puerto)
                .withStartupTimeout(Duration.ofSeconds(120)));
    }

    private static File encontrarJar(String servicio) {
        File targetDir = new File("../" + servicio + "/target");
        if (!targetDir.exists()) {
            throw new IllegalStateException(
                "target/ no existe en " + servicio + ". " +
                "Ejecutar: mvn package -DskipTests -pl " + servicio);
        }
        File[] jars = targetDir.listFiles(f ->
            f.getName().endsWith(".jar") && !f.getName().endsWith(".original"));
        if (jars == null || jars.length == 0) {
            throw new IllegalStateException(
                "No se encontró JAR en " + targetDir.getAbsolutePath());
        }
        return jars[0];
    }

    private static java.util.Map<String, String> construirEnvMap(String[] envVars) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        for (String env : envVars) {
            int idx = env.indexOf('=');
            if (idx > 0) map.put(env.substring(0, idx), env.substring(idx + 1));
        }
        return map;
    }

    private static WebClient crearWebClient(int puerto) {
        return WebClient.builder()
            .baseUrl("http://localhost:" + puerto)
            .build();
    }

    // ─── Helpers de tests ─────────────────────────────────────────────────────

    protected void declararColaTest(String nombre, String routingKey) {
        Queue q = new Queue(nombre, false, false, false);
        amqpAdmin.declareQueue(q);
        amqpAdmin.declareBinding(
            BindingBuilder.bind(q)
                .to(new TopicExchange("eventos.topic", true, false))
                .with(routingKey));
        amqpAdmin.purgeQueue(nombre, false);
    }

    protected Message recibirMensaje(String cola, int segundosTimeout) {
        long deadline = System.currentTimeMillis() + (long) segundosTimeout * 1000;
        while (System.currentTimeMillis() < deadline) {
            Message msg = rabbitTemplate.receive(cola, 500);
            if (msg != null) return msg;
        }
        throw new AssertionError("No se recibió mensaje en cola '" + cola + "' tras " + segundosTimeout + "s");
    }
}
