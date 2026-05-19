# Plataforma de Gestión de Eventos Académicos

**Proyecto:** Diseño de Software Basado en Patrones  
**Institución:** Pontificia Universidad Javeriana  
**Autoras:** Tannia Hernández Rojas  
**Stack:** Java 17 · Spring Boot 3.x · PostgreSQL 16 · Redis 7 · RabbitMQ 3.13 · Docker Compose

---

## Descripción general

Sistema de gestión de eventos académicos (congresos, simposios, talleres, seminarios) para la comunidad javeriana. Arquitectura de microservicios con Hexagonal Architecture (Ports & Adapters), Domain-Driven Design, y un catálogo de patrones GoF documentado.

---

## Inicio rápido

**Prerrequisitos:** Docker, Docker Compose, Java 17+, Maven 3.9+

```bash
# 1. Levantar infraestructura (PostgreSQL, Redis, RabbitMQ, MailHog)
docker-compose up -d

# 2. Compilar todos los módulos
mvn clean package -DskipTests

# 3. Iniciar un microservicio (ejemplo: event-service)
cd event-service && mvn spring-boot:run

# 4. Verificar salud de la infraestructura
curl http://localhost:8082/actuator/health
```

Credenciales por defecto en `.env.example`. MailHog disponible en http://localhost:8025.

---

## Arquitectura

El sistema está compuesto por 7 microservicios independientes, cada uno con su propia base de datos (Database per Service pattern):

```
api-gateway (:8080)
    ├── auth-service        (:8081)  ← OAuth2 Google, JWT, RBAC
    ├── event-service       (:8082)  ← Eventos, catálogo (CQRS + Redis)
    ├── inscription-service (:8083)  ← Cupos, pagos, expiración
    ├── payment-service     (:8084)  ← MercadoPago, webhooks, Outbox
    ├── notification-service(:8085)  ← Emails transaccionales (async)
    └── certificate-service (:8086)  ← PDFs iText, Amazon S3, QR
```

Diagrama completo: [docs/sad-vista-componentes-patrones.md](docs/sad-vista-componentes-patrones.md)

---

## Documentación arquitectónica

| Documento | Descripción |
|---|---|
| [docs/adrs/](docs/adrs/) | Architecture Decision Records (ADR-001 a ADR-019) en formato MADR |
| [docs/referencias/](docs/referencias/) | PDFs de referencia normativa (SRS v3.0, SAD v3.0, materiales del curso) |
| [docs/sad-vista-componentes-patrones.md](docs/sad-vista-componentes-patrones.md) | SAD v2.0: C4 Nivel 2-3, catálogo de patrones, vista de despliegue |
| [docs/sad-vistas-arquitectonicas.md](docs/sad-vistas-arquitectonicas.md) | *(placeholder)* SAD v3.0: vistas Kruchten 4+1 para Entrega 3 |
| [docs/flujos-usuario.md](docs/flujos-usuario.md) | Flujos por actor (Asistente, Organizador, Admin, Ponente, Revisor) |
| [docs/srs-seccion9-modelo-datos-seccion10-trazabilidad.md](docs/srs-seccion9-modelo-datos-seccion10-trazabilidad.md) | SRS §9-10: modelo de datos, estados, trazabilidad RF→ADR |
| [docs/srs-casos-uso-pendientes.md](docs/srs-casos-uso-pendientes.md) | CU detallados: QR, reportes, call for papers, certificados |
| [docs/comportamiento-runtime-inscripcion-pago.md](docs/comportamiento-runtime-inscripcion-pago.md) | Diagramas de secuencia runtime: flujo inscripción-pago |
| [docs/matriz-trazabilidad.md](docs/matriz-trazabilidad.md) | Matriz RF → RN → CU → CA → ADR |
| [docs/modelo-datos-conceptual.md](docs/modelo-datos-conceptual.md) | ERD conceptual de las 20 entidades del dominio |

---

## Estado del proyecto

### Servicios (backend)

| Servicio | Dominio | Persistencia | API REST | Mensajería | Tests | % |
|---|---|---|---|---|---|---|
| **event-service** | ✅ completo | ✅ JPA + Redis | ✅ CRUD + catálogo | ✅ Outbox | ✅ unitarios | **80%** |
| **inscription-service** | ✅ completo | ✅ JPA + SELECT FOR UPDATE | ✅ CRUD + asistencia | ✅ Outbox + Consumer | ✅ unitarios | **80%** |
| **payment-service** | ✅ completo | ✅ JPA + Outbox | ✅ webhook + reembolso | ✅ Outbox Publisher | ✅ unitarios + E2E | **75%** |
| **auth-service** | ❌ no iniciado | ❌ | ❌ | — | ❌ | **0%** |
| **notification-service** | ❌ no iniciado | — | ❌ | ❌ sin consumer | ❌ | **0%** |
| **certificate-service** | ❌ no iniciado | ❌ | ❌ | ❌ sin consumer | ❌ | **0%** |
| **api-gateway** | — | — | ❌ no configurado | — | ❌ | **0%** |

### Frontend

| Módulo | Estado |
|---|---|
| Scaffolding React + Vite + Tailwind | ✅ |
| Catálogo de eventos (lectura) | ⏳ en progreso |
| Flujo inscripción + pago | ❌ pendiente |
| Dashboard organizador | ❌ pendiente |

### Documentación

| Artefacto | Estado |
|---|---|
| SRS v3.0 (§1-10 + trazabilidad) | ✅ completo |
| SAD v2.0 (C4 N2-N3, catálogo patrones) | ✅ completo |
| ADRs individuales (MADR, ADR-001 a ADR-019) | ✅ migrados |
| Diagramas C4 (PlantUML / Excalidraw) | ⏳ pendiente Entrega 3 |
| Vista Kruchten 4+1 | ⏳ pendiente Entrega 3 |

---

## Patrones de diseño implementados

| Patrón | Categoría | Localización |
|---|---|---|
| Hexagonal (Ports & Adapters) | Arquitectónico | Todos los servicios: `domain/port/`, `application/`, `infrastructure/` |
| Repository | Datos | `*/domain/port/out/*Repository.java` |
| CQRS | Arquitectónico | `event-service/domain/port/in/` |
| Observer (Domain Events) | GoF Behavioral | `shared/domain/AggregateRoot.java`, `*/domain/events/` |
| Outbox Pattern | Integración | `*/infrastructure/outbox/OutboxRelayService.java` |
| Strategy | GoF Behavioral | `payment-service/infrastructure/pasarela/` |
| Factory Method | GoF Creacional | `payment-service` — `PasarelaPagoFactory` |
| Template Method | GoF Behavioral | `shared/domain/AggregateRoot.java` |
| Circuit Breaker | Resiliencia | `payment-service` — Resilience4j |
| Builder (Records) | GoF Creacional | `*/domain/events/*.java` (Java 17 Records) |
| Dead Letter Channel | EIP | RabbitMQ `eventos.dlq.exchange` (ADR-019) |

---

## Estructura del repositorio

```
.
├── shared/                    ← Módulo compartido: AggregateRoot, DomainEvent, excepciones
├── event-service/             ← Gestión de eventos y catálogo
├── inscription-service/       ← Inscripciones con bloqueo pesimista
├── payment-service/           ← Pagos MercadoPago con Outbox
├── infrastructure/
│   ├── postgres/init/         ← Scripts de creación de bases de datos
│   └── rabbitmq/              ← definitions.json (exchanges, queues, DLQ)
├── frontend/                  ← React + Vite + Tailwind CSS
├── scripts/                   ← Scripts de utilidad (CI, smoke tests)
├── docs/
│   ├── adrs/                  ← Architecture Decision Records (MADR)
│   ├── diagramas/             ← Diagramas PlantUML / Excalidraw (Entrega 3)
│   └── referencias/           ← PDFs de referencia normativa
└── docker-compose.yml
```

---

## Build & Quality

### Comandos principales

```bash
# Build completo con tests unitarios y cobertura JaCoCo
mvn clean verify

# Build de un módulo específico
mvn -pl payment-service clean verify

# Solo tests unitarios (excluye E2E que requieren Docker)
mvn -pl payment-service test -Dsurefire.excludes="**/*IT.java"
```

### Reporte de cobertura JaCoCo

```bash
mvn -pl payment-service clean verify
open payment-service/target/site/jacoco/index.html
```

Thresholds por capa de arquitectura hexagonal (modo soft — reporta sin fallar el build):

| Capa | Líneas | Ramas |
|---|---|---|
| `domain` | 90% | 85% |
| `application` | 85% | 75% |
| `infrastructure` | 60% | — |

Ver [docs/build-conventions.md](docs/build-conventions.md) para la política completa.

### API Documentation (Swagger UI)

Una vez levantado `payment-service` (puerto 8084):

```
http://localhost:8084/swagger-ui.html   ← UI interactiva
http://localhost:8084/api-docs          ← OpenAPI spec JSON
```

> Disponible desde el Commit 7 (OpenAPI) — placeholder hasta entonces.

---

## Contribución

Rama activa de código: `feat/payment-outbox-e2e`  
Rama activa de documentación: `feat/docs-baseline-e3`  
Base: `master`

> **Restricción de ramas:** No mergear `feat/docs-baseline-e3` a `master` hasta que `feat/payment-outbox-e2e` esté lista, para evitar conflictos en `pom.xml` y `definitions.json`.
