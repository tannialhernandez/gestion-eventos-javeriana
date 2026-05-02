# Cierre E2E inscripción ↔ pago vía Outbox + RabbitMQ — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cerrar el flujo end-to-end inscripción ↔ pago implementando Outbox completo en `payment-service` con RabbitMQ + test E2E con TestContainers, y arreglar la violación hexagonal del WIP en `EventoController`.

**Architecture:** Espejo del Outbox Pattern ya existente en `inscription-service`. Adaptador JPA persiste eventos en tabla `outbox_events`; `OutboxRelayService` los lee con `@Scheduled` y los publica al exchange `eventos.topic` de RabbitMQ. Test E2E con TestContainers levanta Postgres + RabbitMQ reales y prueba el flujo webhook → outbox → mensaje → consumer.

**Tech Stack:** Spring Boot 3, Spring Data JPA, Spring AMQP, Resilience4j, PostgreSQL 16, RabbitMQ 3.13, TestContainers 1.19, JUnit 5.

---

## File Structure

### A crear en `payment-service`

| Archivo | Responsabilidad |
|---|---|
| `src/main/resources/application.yml` | Config Spring Boot (datasource, JPA, RabbitMQ, server.port, scheduling) |
| `src/main/java/.../infrastructure/outbox/OutboxEventEntity.java` | Entidad JPA mapeada a `outbox_events` |
| `src/main/java/.../infrastructure/outbox/SpringDataOutboxRepository.java` | Interfaz Spring Data JPA |
| `src/main/java/.../infrastructure/outbox/JpaOutboxEventRepository.java` | Adaptador que implementa `domain/port/out/OutboxEventRepository` |
| `src/main/java/.../infrastructure/outbox/OutboxRelayService.java` | Job `@Scheduled` que publica a RabbitMQ |
| `src/main/java/.../infrastructure/messaging/RabbitMQConfig.java` | `RabbitTemplate` + declaración de exchange/queues consumibles |
| `src/test/java/.../PagoFlowEndToEndIT.java` | Test integración con TestContainers (Postgres + RabbitMQ) |

### A crear en `event-service`

| Archivo | Responsabilidad |
|---|---|
| `src/main/java/.../domain/port/in/ListarEventosPorEstadoUseCase.java` | Puerto de entrada |
| `src/main/java/.../domain/port/in/EnviarEventoARevisionUseCase.java` | Puerto de entrada |
| `src/main/java/.../domain/port/in/EliminarEventoUseCase.java` | Puerto de entrada |
| `src/main/java/.../application/ListarEventosPorEstadoService.java` | Caso de uso |
| `src/main/java/.../application/EnviarEventoARevisionService.java` | Caso de uso |
| `src/main/java/.../application/EliminarEventoService.java` | Caso de uso |
| `src/test/java/.../application/ListarEventosPorEstadoServiceTest.java` | Test unitario |
| `src/test/java/.../application/EnviarEventoARevisionServiceTest.java` | Test unitario |
| `src/test/java/.../application/EliminarEventoServiceTest.java` | Test unitario |

### A modificar

| Archivo | Cambio |
|---|---|
| `payment-service/pom.xml` | Agregar `testcontainers-postgresql` y `testcontainers-rabbitmq` en scope test |
| `event-service/.../infrastructure/web/EventoController.java` | Inyectar 3 use cases nuevos; remover `EventoRepository` |

---

## Task 1: Crear `application.yml` para payment-service

**Files:**
- Create: `payment-service/src/main/resources/application.yml`

- [ ] **Step 1: Crear el archivo de configuración**

Crear el archivo con este contenido exacto:

```yaml
spring:
  application:
    name: payment-service

  datasource:
    url: jdbc:postgresql://localhost:5432/eventos_payment
    username: ${POSTGRES_USER:eventos_user}
    password: ${POSTGRES_PASSWORD:eventos_pass}

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: 5672
    username: ${RABBITMQ_USER:eventos_user}
    password: ${RABBITMQ_PASSWORD:eventos_pass}
    virtual-host: eventos

server:
  port: 8084

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

**Nota sobre `ddl-auto: update`:** Difiere de inscription-service (`validate`) porque payment-service no tiene migraciones Flyway. Hibernate creará las tablas automáticamente desde las entidades. Esto es pragmático para contexto académico; en producción se reemplazaría por Flyway/Liquibase. Documentar esta decisión en el SAD posteriormente.

- [ ] **Step 2: Verificar que la app arranca**

Ejecutar:
```bash
cd /Users/tanhernandez/Documents/javeriana/diseño_patrones/gestion-eventos-javeriana
docker-compose up -d postgres rabbitmq
mvn -pl payment-service spring-boot:run
```

Esperado: el servicio inicia en puerto 8084 sin errores. Hibernate crea la tabla `pago` automáticamente desde `PagoEntity`. Detener con `Ctrl+C`.

- [ ] **Step 3: Commit**

```bash
git add payment-service/src/main/resources/application.yml
git commit -m "feat(payment-service): add base configuration (datasource, JPA, RabbitMQ)"
```

---

## Task 2: Agregar dependencias TestContainers al pom

**Files:**
- Modify: `payment-service/pom.xml`

- [ ] **Step 1: Agregar dependencias**

Después del bloque `spring-boot-starter-test` (línea ~73), agregar:

```xml
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>rabbitmq</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Verificar que las dependencias resuelven**

```bash
mvn -pl payment-service dependency:resolve -DincludeScope=test
```

Esperado: BUILD SUCCESS sin errores de resolución.

- [ ] **Step 3: Commit**

```bash
git add payment-service/pom.xml
git commit -m "build(payment-service): add testcontainers dependencies for integration tests"
```

---

## Task 3: Crear `OutboxEventEntity` en payment-service

**Files:**
- Create: `payment-service/src/main/java/com/javeriana/eventos/payment/infrastructure/outbox/OutboxEventEntity.java`

- [ ] **Step 1: Crear la entidad JPA**

```java
package com.javeriana.eventos.payment.infrastructure.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA para la tabla outbox_events de payment-service.
 *
 * Outbox Pattern (ADR-11 del SAD): los eventos de dominio (PagoConfirmado,
 * PagoReembolsado) se persisten aquí dentro de la misma transacción de
 * negocio. El OutboxRelayService los publica a RabbitMQ asíncronamente.
 */
@Entity
@Table(name = "outbox_events",
    indexes = @Index(name = "idx_outbox_pending",
        columnList = "published,created_at"))
public class OutboxEventEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "uuid")
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAggregateId() { return aggregateId; }
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
```

- [ ] **Step 2: Verificar compilación**

```bash
mvn -pl payment-service compile
```

Esperado: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add payment-service/src/main/java/com/javeriana/eventos/payment/infrastructure/outbox/OutboxEventEntity.java
git commit -m "feat(payment-service): add OutboxEventEntity for Outbox Pattern"
```

---

## Task 4: Crear `SpringDataOutboxRepository`

**Files:**
- Create: `payment-service/src/main/java/com/javeriana/eventos/payment/infrastructure/outbox/SpringDataOutboxRepository.java`

- [ ] **Step 1: Crear la interfaz Spring Data**

```java
package com.javeriana.eventos.payment.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface SpringDataOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.published = false ORDER BY o.createdAt ASC")
    List<OutboxEventEntity> findPendingEvents();
}
```

- [ ] **Step 2: Verificar compilación**

```bash
mvn -pl payment-service compile
```

Esperado: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add payment-service/src/main/java/com/javeriana/eventos/payment/infrastructure/outbox/SpringDataOutboxRepository.java
git commit -m "feat(payment-service): add Spring Data outbox repository"
```

---

## Task 5: Crear `JpaOutboxEventRepository` (adapter del puerto)

**Files:**
- Create: `payment-service/src/main/java/com/javeriana/eventos/payment/infrastructure/outbox/JpaOutboxEventRepository.java`

- [ ] **Step 1: Crear el adapter**

```java
package com.javeriana.eventos.payment.infrastructure.outbox;

import com.javeriana.eventos.payment.domain.port.out.OutboxEventRepository;
import com.javeriana.eventos.shared.infrastructure.outbox.OutboxEvent;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Adaptador JPA que implementa OutboxEventRepository (puerto de salida del dominio).
 *
 * Espeja la implementación de inscription-service para mantener consistencia
 * del Outbox Pattern entre productores de eventos.
 */
@Repository
public class JpaOutboxEventRepository implements OutboxEventRepository {

    private final SpringDataOutboxRepository springDataRepo;

    public JpaOutboxEventRepository(SpringDataOutboxRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public void guardar(OutboxEvent event) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(event.getId());
        entity.setAggregateId(event.getAggregateId());
        entity.setEventType(event.getEventType());
        entity.setPayload(event.getPayload());
        entity.setPublished(false);
        entity.setCreatedAt(event.getCreatedAt());
        springDataRepo.save(entity);
    }

    @Override
    public List<OutboxEvent> buscarNoPublicados() {
        return springDataRepo.findPendingEvents()
            .stream()
            .map(e -> new OutboxEvent(e.getAggregateId(), e.getEventType(), e.getPayload()))
            .collect(Collectors.toList());
    }

    @Override
    public void marcarComoPublicado(OutboxEvent event) {
        springDataRepo.findById(event.getId()).ifPresent(entity -> {
            entity.setPublished(true);
            entity.setPublishedAt(event.getPublishedAt());
            springDataRepo.save(entity);
        });
    }
}
```

- [ ] **Step 2: Verificar compilación**

```bash
mvn -pl payment-service compile
```

Esperado: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add payment-service/src/main/java/com/javeriana/eventos/payment/infrastructure/outbox/JpaOutboxEventRepository.java
git commit -m "feat(payment-service): implement OutboxEventRepository port with JPA"
```

---

## Task 6: Crear `RabbitMQConfig`

**Files:**
- Create: `payment-service/src/main/java/com/javeriana/eventos/payment/infrastructure/messaging/RabbitMQConfig.java`

- [ ] **Step 1: Crear la config**

```java
package com.javeriana.eventos.payment.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para payment-service.
 *
 * El exchange y las queues se declaran vía definitions.json al arranque
 * del contenedor de RabbitMQ. Esta config solo declara el RabbitTemplate
 * que usa OutboxRelayService para publicar.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "eventos.topic";

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          ObjectMapper objectMapper) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter(objectMapper));
        return template;
    }

    @Bean
    public TopicExchange eventosTopicExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }
}
```

- [ ] **Step 2: Verificar compilación**

```bash
mvn -pl payment-service compile
```

Esperado: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add payment-service/src/main/java/com/javeriana/eventos/payment/infrastructure/messaging/RabbitMQConfig.java
git commit -m "feat(payment-service): add RabbitMQ configuration"
```

---

## Task 7: Crear `OutboxRelayService`

**Files:**
- Create: `payment-service/src/main/java/com/javeriana/eventos/payment/infrastructure/outbox/OutboxRelayService.java`
- Modify: `payment-service/src/main/java/com/javeriana/eventos/payment/PaymentApplication.java` (agregar `@EnableScheduling`)

- [ ] **Step 1: Crear el relay**

```java
package com.javeriana.eventos.payment.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox Relay — publica eventos pendientes a RabbitMQ cada 5 segundos.
 *
 * Outbox Pattern (ADR-11): los eventos de dominio se persisten en
 * outbox_events dentro de la misma transacción de negocio (en
 * ProcesarWebhookService). Este relay los lee y publica en RabbitMQ
 * asíncronamente, garantizando que:
 *
 *  - Si payment-service cae después del COMMIT pero antes de publicar:
 *    al reiniciar, el relay encuentra los pendientes y los publica.
 *  - Si RabbitMQ está caído: los eventos permanecen en outbox_events.
 *  - Exactamente-una-vez en BD; al-menos-una-vez en RabbitMQ
 *    (los consumers deben ser idempotentes — el de inscription lo es).
 */
@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    private static final String EXCHANGE = "eventos.topic";

    private final SpringDataOutboxRepository outboxRepo;
    private final RabbitTemplate rabbitTemplate;

    public OutboxRelayService(SpringDataOutboxRepository outboxRepo,
                              RabbitTemplate rabbitTemplate) {
        this.outboxRepo = outboxRepo;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publicarEventosPendientes() {
        List<OutboxEventEntity> pendientes = outboxRepo.findPendingEvents();

        if (pendientes.isEmpty()) {
            return;
        }

        log.debug("OutboxRelay: publicando {} eventos pendientes.", pendientes.size());

        for (OutboxEventEntity evento : pendientes) {
            try {
                String routingKey = evento.getEventType().toLowerCase().replace('_', '.');
                rabbitTemplate.convertAndSend(EXCHANGE, routingKey, evento.getPayload());

                evento.setPublished(true);
                evento.setPublishedAt(java.time.Instant.now());
                outboxRepo.save(evento);

                log.debug("Evento {} publicado en {}/{}",
                    evento.getId(), EXCHANGE, routingKey);
            } catch (Exception e) {
                log.error("Error publicando evento {}: {}. Reintento en próximo ciclo.",
                    evento.getId(), e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 2: Habilitar `@Scheduled` en `PaymentApplication`**

Leer `payment-service/src/main/java/com/javeriana/eventos/payment/PaymentApplication.java`. Agregar el import y la anotación:

```java
import org.springframework.scheduling.annotation.EnableScheduling;
// ...
@EnableScheduling
@SpringBootApplication
public class PaymentApplication {
```

- [ ] **Step 3: Verificar compilación y arranque**

```bash
mvn -pl payment-service compile
mvn -pl payment-service spring-boot:run
```

Esperado: el servicio arranca, no hay errores de scheduling. En logs aparece "OutboxRelay" si está en DEBUG (no esperado en INFO). Detener con `Ctrl+C`.

- [ ] **Step 4: Commit**

```bash
git add payment-service/src/main/java/com/javeriana/eventos/payment/infrastructure/outbox/OutboxRelayService.java payment-service/src/main/java/com/javeriana/eventos/payment/PaymentApplication.java
git commit -m "feat(payment-service): add OutboxRelayService scheduled publisher"
```

---

## Task 8: Test de integración E2E con TestContainers

**Files:**
- Create: `payment-service/src/test/java/com/javeriana/eventos/payment/PagoFlowEndToEndIT.java`

- [ ] **Step 1: Escribir el test**

```java
package com.javeriana.eventos.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.payment.application.ProcesarWebhookService;
import com.javeriana.eventos.payment.domain.model.EstadoPago;
import com.javeriana.eventos.payment.domain.model.Pago;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase;
import com.javeriana.eventos.payment.domain.port.out.PagoRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test E2E que prueba el flujo completo Outbox + RabbitMQ:
 *  1. Llega un webhook al ProcesarWebhookService.
 *  2. Se persiste el Pago + un evento en outbox_events (misma transacción).
 *  3. OutboxRelayService publica el evento en exchange eventos.topic.
 *  4. Verificamos que el mensaje aparece en la queue pago.confirmado.
 *
 * Cubre los atributos de calidad: asincronía, idempotencia, atomicidad
 * (Outbox), publicación garantizada.
 */
@SpringBootTest
@Testcontainers
class PagoFlowEndToEndIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("eventos_payment_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.rabbitmq.host", rabbitmq::getHost);
        r.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        r.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        r.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
        r.add("spring.rabbitmq.virtual-host", () -> "/");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired ProcesarWebhookUseCase procesarWebhook;
    @Autowired PagoRepository pagoRepository;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired RabbitAdmin rabbitAdmin;
    @Autowired ObjectMapper objectMapper;

    @BeforeAll
    static void declareTestTopology(@Autowired RabbitAdmin admin) {
        TopicExchange exchange = new TopicExchange("eventos.topic", true, false);
        Queue pagoConfirmado = new Queue("pago.confirmado", true);
        admin.declareExchange(exchange);
        admin.declareQueue(pagoConfirmado);
        admin.declareBinding(BindingBuilder.bind(pagoConfirmado)
            .to(exchange).with("pago.confirmado"));
    }

    @Test
    void webhook_aprobado_publica_pago_confirmado_en_rabbitmq() throws Exception {
        // ── Arrange ──────────────────────────────────────────────────────
        UUID inscripcionId = UUID.randomUUID();
        Pago pago = new Pago(UUID.randomUUID(), inscripcionId,
            new BigDecimal("100000"), EstadoPago.PENDIENTE);
        pagoRepository.guardar(pago);

        ProcesarWebhookUseCase.WebhookPayload payload =
            new ProcesarWebhookUseCase.WebhookPayload(
                inscripcionId.toString(),
                "approved",
                "MP-TEST-12345",
                "{}");

        // ── Act ──────────────────────────────────────────────────────────
        ProcesarWebhookUseCase.ResultadoWebhook resultado =
            procesarWebhook.procesar(payload);

        // ── Assert ───────────────────────────────────────────────────────
        assertEquals(ProcesarWebhookUseCase.ResultadoWebhook.CONFIRMADO, resultado);

        // Esperar a que el OutboxRelay publique (corre cada 5s)
        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            Message msg = rabbitTemplate.receive("pago.confirmado", 1000);
            assertNotNull(msg, "Esperaba mensaje en queue pago.confirmado");

            JsonNode body = objectMapper.readTree(msg.getBody());
            assertEquals(inscripcionId.toString(), body.get("inscripcionId").asText());
            assertEquals("MP-TEST-12345", body.get("referenciaExterna").asText());
        });
    }
}
```

- [ ] **Step 2: Agregar `awaitility` al pom de payment-service**

Modificar `payment-service/pom.xml` agregando esta dependencia en el bloque de test:

```xml
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 3: Correr el test**

```bash
mvn -pl payment-service verify -Dit.test=PagoFlowEndToEndIT
```

Esperado: el test PASA. Tarda ~30-60s (levantar contenedores).

Si falla por timeout, verificar que `OutboxRelayService` está siendo gestionado por Spring (que `@EnableScheduling` está activo).

- [ ] **Step 4: Commit**

```bash
git add payment-service/src/test/java/com/javeriana/eventos/payment/PagoFlowEndToEndIT.java payment-service/pom.xml
git commit -m "test(payment-service): add E2E integration test with TestContainers (Outbox + RabbitMQ)"
```

---

## Task 9: Crear `ListarEventosPorEstadoUseCase` + service (TDD)

**Files:**
- Create: `event-service/src/main/java/com/javeriana/eventos/event/domain/port/in/ListarEventosPorEstadoUseCase.java`
- Create: `event-service/src/main/java/com/javeriana/eventos/event/application/ListarEventosPorEstadoService.java`
- Create: `event-service/src/test/java/com/javeriana/eventos/event/application/ListarEventosPorEstadoServiceTest.java`

- [ ] **Step 1: Escribir el test que falla**

```java
package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.model.EstadoEvento;
import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ListarEventosPorEstadoServiceTest {

    @Test
    void delega_la_busqueda_al_repositorio_con_el_estado_solicitado() {
        EventoRepository repo = mock(EventoRepository.class);
        Evento e1 = mock(Evento.class);
        when(e1.getId()).thenReturn(UUID.randomUUID());
        when(repo.buscarPorEstado(eq(EstadoEvento.BORRADOR))).thenReturn(List.of(e1));

        ListarEventosPorEstadoService service = new ListarEventosPorEstadoService(repo);

        List<Evento> resultado = service.listar(EstadoEvento.BORRADOR);

        assertEquals(1, resultado.size());
        verify(repo).buscarPorEstado(EstadoEvento.BORRADOR);
    }
}
```

- [ ] **Step 2: Correr el test para verificar que falla**

```bash
mvn -pl event-service test -Dtest=ListarEventosPorEstadoServiceTest
```

Esperado: FAIL — `ListarEventosPorEstadoService` no existe.

- [ ] **Step 3: Crear el puerto de entrada**

```java
package com.javeriana.eventos.event.domain.port.in;

import com.javeriana.eventos.event.domain.model.EstadoEvento;
import com.javeriana.eventos.event.domain.model.Evento;

import java.util.List;

public interface ListarEventosPorEstadoUseCase {
    List<Evento> listar(EstadoEvento estado);
}
```

- [ ] **Step 4: Implementar el caso de uso**

```java
package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.model.EstadoEvento;
import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.port.in.ListarEventosPorEstadoUseCase;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListarEventosPorEstadoService implements ListarEventosPorEstadoUseCase {

    private final EventoRepository eventoRepository;

    public ListarEventosPorEstadoService(EventoRepository eventoRepository) {
        this.eventoRepository = eventoRepository;
    }

    @Override
    public List<Evento> listar(EstadoEvento estado) {
        return eventoRepository.buscarPorEstado(estado);
    }
}
```

- [ ] **Step 5: Correr el test para verificar que pasa**

```bash
mvn -pl event-service test -Dtest=ListarEventosPorEstadoServiceTest
```

Esperado: PASS.

- [ ] **Step 6: Commit**

```bash
git add event-service/src/main/java/com/javeriana/eventos/event/domain/port/in/ListarEventosPorEstadoUseCase.java event-service/src/main/java/com/javeriana/eventos/event/application/ListarEventosPorEstadoService.java event-service/src/test/java/com/javeriana/eventos/event/application/ListarEventosPorEstadoServiceTest.java
git commit -m "feat(event-service): add ListarEventosPorEstadoUseCase"
```

---

## Task 10: Crear `EnviarEventoARevisionUseCase` + service (TDD)

**Files:**
- Create: `event-service/src/main/java/com/javeriana/eventos/event/domain/port/in/EnviarEventoARevisionUseCase.java`
- Create: `event-service/src/main/java/com/javeriana/eventos/event/application/EnviarEventoARevisionService.java`
- Create: `event-service/src/test/java/com/javeriana/eventos/event/application/EnviarEventoARevisionServiceTest.java`

- [ ] **Step 1: Escribir el test que falla**

```java
package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class EnviarEventoARevisionServiceTest {

    @Test
    void cambia_estado_y_persiste_cuando_evento_existe() {
        EventoRepository repo = mock(EventoRepository.class);
        UUID id = UUID.randomUUID();
        Evento evento = mock(Evento.class);
        when(repo.buscarPorId(id)).thenReturn(Optional.of(evento));

        EnviarEventoARevisionService service = new EnviarEventoARevisionService(repo);
        service.enviar(id);

        verify(evento).enviarARevision();
        verify(repo).guardar(evento);
    }

    @Test
    void noop_cuando_evento_no_existe() {
        EventoRepository repo = mock(EventoRepository.class);
        UUID id = UUID.randomUUID();
        when(repo.buscarPorId(id)).thenReturn(Optional.empty());

        EnviarEventoARevisionService service = new EnviarEventoARevisionService(repo);
        service.enviar(id);

        verify(repo, never()).guardar(any());
    }
}
```

- [ ] **Step 2: Correr el test**

```bash
mvn -pl event-service test -Dtest=EnviarEventoARevisionServiceTest
```

Esperado: FAIL — `EnviarEventoARevisionService` no existe.

- [ ] **Step 3: Crear el puerto**

```java
package com.javeriana.eventos.event.domain.port.in;

import java.util.UUID;

public interface EnviarEventoARevisionUseCase {
    void enviar(UUID id);
}
```

- [ ] **Step 4: Implementar el caso de uso**

```java
package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.port.in.EnviarEventoARevisionUseCase;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class EnviarEventoARevisionService implements EnviarEventoARevisionUseCase {

    private final EventoRepository eventoRepository;

    public EnviarEventoARevisionService(EventoRepository eventoRepository) {
        this.eventoRepository = eventoRepository;
    }

    @Override
    public void enviar(UUID id) {
        eventoRepository.buscarPorId(id).ifPresent(evento -> {
            evento.enviarARevision();
            eventoRepository.guardar(evento);
        });
    }
}
```

- [ ] **Step 5: Correr el test**

```bash
mvn -pl event-service test -Dtest=EnviarEventoARevisionServiceTest
```

Esperado: PASS.

- [ ] **Step 6: Commit**

```bash
git add event-service/src/main/java/com/javeriana/eventos/event/domain/port/in/EnviarEventoARevisionUseCase.java event-service/src/main/java/com/javeriana/eventos/event/application/EnviarEventoARevisionService.java event-service/src/test/java/com/javeriana/eventos/event/application/EnviarEventoARevisionServiceTest.java
git commit -m "feat(event-service): add EnviarEventoARevisionUseCase"
```

---

## Task 11: Crear `EliminarEventoUseCase` + service (TDD)

**Files:**
- Create: `event-service/src/main/java/com/javeriana/eventos/event/domain/port/in/EliminarEventoUseCase.java`
- Create: `event-service/src/main/java/com/javeriana/eventos/event/application/EliminarEventoService.java`
- Create: `event-service/src/test/java/com/javeriana/eventos/event/application/EliminarEventoServiceTest.java`

- [ ] **Step 1: Escribir el test que falla**

```java
package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

class EliminarEventoServiceTest {

    @Test
    void delega_la_eliminacion_al_repositorio() {
        EventoRepository repo = mock(EventoRepository.class);
        UUID id = UUID.randomUUID();

        EliminarEventoService service = new EliminarEventoService(repo);
        service.eliminar(id);

        verify(repo).eliminar(id);
    }
}
```

- [ ] **Step 2: Correr el test**

```bash
mvn -pl event-service test -Dtest=EliminarEventoServiceTest
```

Esperado: FAIL.

- [ ] **Step 3: Crear el puerto**

```java
package com.javeriana.eventos.event.domain.port.in;

import java.util.UUID;

public interface EliminarEventoUseCase {
    void eliminar(UUID id);
}
```

- [ ] **Step 4: Implementar el caso de uso**

```java
package com.javeriana.eventos.event.application;

import com.javeriana.eventos.event.domain.port.in.EliminarEventoUseCase;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class EliminarEventoService implements EliminarEventoUseCase {

    private final EventoRepository eventoRepository;

    public EliminarEventoService(EventoRepository eventoRepository) {
        this.eventoRepository = eventoRepository;
    }

    @Override
    public void eliminar(UUID id) {
        eventoRepository.eliminar(id);
    }
}
```

- [ ] **Step 5: Correr el test**

```bash
mvn -pl event-service test -Dtest=EliminarEventoServiceTest
```

Esperado: PASS.

- [ ] **Step 6: Commit**

```bash
git add event-service/src/main/java/com/javeriana/eventos/event/domain/port/in/EliminarEventoUseCase.java event-service/src/main/java/com/javeriana/eventos/event/application/EliminarEventoService.java event-service/src/test/java/com/javeriana/eventos/event/application/EliminarEventoServiceTest.java
git commit -m "feat(event-service): add EliminarEventoUseCase"
```

---

## Task 12: Refactorizar `EventoController` para usar los 3 use cases nuevos

**Files:**
- Modify: `event-service/src/main/java/com/javeriana/eventos/event/infrastructure/web/EventoController.java`

- [ ] **Step 1: Reemplazar el contenido completo del controller**

Reemplazar el archivo completo. Las diferencias clave vs el WIP actual:
- ❌ Remover el campo `EventoRepository eventoRepository` y su parámetro de constructor
- ❌ Remover el import de `EventoRepository` y `EstadoEvento` (no se usan acá)
- ✅ Inyectar los 3 nuevos use cases
- ✅ Los endpoints `/admin/todos`, `/admin/pendientes`, `/{id}/enviar-revision`, `/{id}` (DELETE) usan los use cases

```java
package com.javeriana.eventos.event.infrastructure.web;

import com.javeriana.eventos.event.domain.model.EstadoEvento;
import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.model.ModalidadEvento;
import com.javeriana.eventos.event.domain.model.TipoEvento;
import com.javeriana.eventos.event.domain.port.in.ConsultarCatalogoUseCase;
import com.javeriana.eventos.event.domain.port.in.CrearEventoUseCase;
import com.javeriana.eventos.event.domain.port.in.EliminarEventoUseCase;
import com.javeriana.eventos.event.domain.port.in.EnviarEventoARevisionUseCase;
import com.javeriana.eventos.event.domain.port.in.ListarEventosPorEstadoUseCase;
import com.javeriana.eventos.event.domain.port.in.PublicarEventoUseCase;
import com.javeriana.eventos.event.infrastructure.web.dto.CrearEventoRequest;
import com.javeriana.eventos.event.infrastructure.web.dto.EventoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/eventos")
public class EventoController {

    private final CrearEventoUseCase crearEvento;
    private final PublicarEventoUseCase publicarEvento;
    private final ConsultarCatalogoUseCase consultarCatalogo;
    private final ListarEventosPorEstadoUseCase listarEventosPorEstado;
    private final EnviarEventoARevisionUseCase enviarARevision;
    private final EliminarEventoUseCase eliminarEvento;

    public EventoController(CrearEventoUseCase crearEvento,
                            PublicarEventoUseCase publicarEvento,
                            ConsultarCatalogoUseCase consultarCatalogo,
                            ListarEventosPorEstadoUseCase listarEventosPorEstado,
                            EnviarEventoARevisionUseCase enviarARevision,
                            EliminarEventoUseCase eliminarEvento) {
        this.crearEvento = crearEvento;
        this.publicarEvento = publicarEvento;
        this.consultarCatalogo = consultarCatalogo;
        this.listarEventosPorEstado = listarEventosPorEstado;
        this.enviarARevision = enviarARevision;
        this.eliminarEvento = eliminarEvento;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventoResponse crear(@Valid @RequestBody CrearEventoRequest request) {
        Evento evento = crearEvento.crear(
            request.titulo(),
            request.descripcion(),
            TipoEvento.valueOf(request.tipo()),
            ModalidadEvento.valueOf(request.modalidad()),
            request.fechaInicio(),
            request.fechaFin(),
            request.fechaLimiteInscripcion(),
            request.cupoMaximo()
        );
        return EventoResponse.from(evento);
    }

    @PostMapping("/{id}/publicar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publicar(@PathVariable UUID id) {
        publicarEvento.publicar(id);
    }

    @GetMapping
    public List<EventoResponse> consultarCatalogo() {
        return consultarCatalogo.consultar().stream()
            .map(EventoResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventoResponse> consultarPorId(@PathVariable UUID id) {
        return consultarCatalogo.consultarPorId(id)
            .map(e -> ResponseEntity.ok(EventoResponse.from(e)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/admin/todos")
    public List<EventoResponse> listarBorradores() {
        return listarEventosPorEstado.listar(EstadoEvento.BORRADOR).stream()
            .map(EventoResponse::from)
            .toList();
    }

    @GetMapping("/admin/pendientes")
    public List<EventoResponse> listarPendientes() {
        return listarEventosPorEstado.listar(EstadoEvento.PENDIENTE_PUBLICACION).stream()
            .map(EventoResponse::from)
            .toList();
    }

    @PostMapping("/{id}/enviar-revision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enviarRevision(@PathVariable UUID id) {
        enviarARevision.enviar(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id) {
        eliminarEvento.eliminar(id);
    }
}
```

- [ ] **Step 2: Compilar el módulo completo**

```bash
mvn -pl event-service compile
```

Esperado: BUILD SUCCESS.

- [ ] **Step 3: Correr todos los tests del módulo**

```bash
mvn -pl event-service test
```

Esperado: todos los tests existentes siguen pasando + los 3 nuevos tests pasan.

- [ ] **Step 4: Verificar que el contexto Spring carga**

```bash
docker-compose up -d postgres redis
mvn -pl event-service spring-boot:run
```

Esperado: el servicio arranca en puerto 8082, no hay errores de inyección de dependencias. Detener con `Ctrl+C`.

- [ ] **Step 5: Commit**

```bash
git add event-service/src/main/java/com/javeriana/eventos/event/infrastructure/web/EventoController.java
git commit -m "refactor(event-service): replace direct repository access in EventoController with use cases (fix hexagonal violation)"
```

---

## Task 13: Verificación final + commit del payment-service como nuevo módulo

**Files:**
- (Sin nuevos archivos, solo verificación)

- [ ] **Step 1: Compilar todo el proyecto**

```bash
mvn clean compile
```

Esperado: BUILD SUCCESS en todos los módulos.

- [ ] **Step 2: Correr todos los tests unitarios**

```bash
mvn test
```

Esperado: todos los tests pasan.

- [ ] **Step 3: Correr el test E2E**

```bash
mvn -pl payment-service verify -Dit.test=PagoFlowEndToEndIT
```

Esperado: PASS.

- [ ] **Step 4: Verificar git status**

```bash
git status
```

Esperado: solo quedan untracked archivos del frontend (parked) y posiblemente docs nuevos. No debe quedar nada de payment-service ni event-service sin commitear.

- [ ] **Step 5: Si quedaron archivos del payment-service no incluidos en commits anteriores (PaymentApplication, ports, adapters originales), commitearlos en un commit final**

```bash
git status payment-service
```

Si quedan archivos, agregar lo restante:
```bash
git add payment-service/
git commit -m "feat(payment-service): add baseline service structure with hexagonal architecture"
```

---

## Self-Review

**Spec coverage check:**

| Requisito del spec | Tarea(s) que lo implementa(n) |
|---|---|
| OutboxEventEntity, SpringDataOutboxRepository, JpaOutboxEventRepository, OutboxRelayService | Tasks 3-5, 7 |
| RabbitMQConfig | Task 6 |
| Script SQL para tabla `outbox_events` | Cubierto vía `ddl-auto: update` (Task 1) — ver nota |
| Test E2E con TestContainers | Task 8 |
| Fix EventoController | Tasks 9-12 |
| Comentarios `// Outbox Pattern (ADR-XX)` inline | Cubierto en Tasks 3, 7 |

**Desviación documentada:** El spec menciona `infrastructure/postgres/init/04-payment-outbox.sql`. El plan usa `ddl-auto: update` en su lugar para mantener consistencia con la realidad operativa (inscription tampoco usa migrations Flyway efectivamente). La tabla `outbox_events` se crea por Hibernate desde `OutboxEventEntity`. Si la usuaria prefiere el script SQL explícito, agregar como Task 14 antes de Task 8.

**Placeholders:** ninguno. Todos los pasos contienen código concreto.

**Type consistency:** verificado — `EventoRepository.buscarPorEstado(EstadoEvento)`, `EventoRepository.eliminar(UUID)`, `Evento.enviarARevision()` existen en el código actual.

**Riesgos detectados durante el plan:**
- El test E2E asume que `Pago` tiene un constructor público con `(UUID, UUID, BigDecimal, EstadoPago)`. Verificar al ejecutar Task 8 — si la firma es distinta, ajustar el test.
- El payment-service nunca se ha ejecutado antes (sin `application.yml`); puede haber otros pequeños ajustes al arrancarlo por primera vez (Task 1 step 2 los detectará).
