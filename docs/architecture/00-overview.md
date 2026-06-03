# Visión Arquitectónica — Plataforma de Gestión de Eventos Académicos

**Pontificia Universidad Javeriana — Sede Bogotá**  
**Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## 1. Propósito del sistema

La Plataforma de Gestión de Eventos Académicos es un sistema institucional que soporta el ciclo de vida completo de eventos académicos: desde la creación por parte de organizadores hasta la inscripción, pago y confirmación por parte de participantes.

## 2. Características arquitectónicas principales

| Característica | Decisión | Justificación |
|---|---|---|
| Descomposición | Microservicios (4 servicios core) | Despliegue independiente, equipos separados por dominio |
| Comunicación | Síncrona REST (usuario←→SPA←→servicios) + asíncrona AMQP (entre servicios) | Desacoplamiento de flujos no críticos |
| Persistencia | PostgreSQL por servicio (esquema propio) | Autonomía de datos, sin acoplamiento de BD |
| Autenticación | JWT RS256 + JWKS | Validación stateless, preparado para Azure AD |
| Resiliencia | Circuit Breaker + Retry-After + Outbox Pattern | Tolerancia a fallos parciales |
| Frontend | SPA React + TypeScript | Cliente rico, sin sesiones servidor |

## 3. Diagrama de contexto (C1)

```
┌──────────────────────────────────────────────────────────┐
│                  Pontificia Universidad Javeriana         │
│                                                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │ Participante│  │ Organizador │  │  Administrador  │  │
│  │ (estudiante)│  │ (profesor)  │  │  (TI / coord.)  │  │
│  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘  │
│         └────────────────┼──────────────────┘           │
│                          │ HTTPS / JWT                   │
│                          ▼                               │
│          ┌───────────────────────────────┐               │
│          │  Plataforma de Gestión de     │               │
│          │  Eventos Académicos           │               │
│          │  (AWS: CloudFront + EC2 + RDS)│               │
│          └───────────────────────────────┘               │
└──────────────────────────────────────────────────────────┘
```

## 4. Diagrama de contenedores (C2)

```
┌────────────────────────────────────────────────────────────────┐
│                     Sistema de Eventos                         │
│                                                                │
│  ┌──────────────────┐    HTTPS    ┌──────────────────────┐    │
│  │   Aplicación SPA  │ ◄──────►  │   Application LB      │    │
│  │  (React/TS, S3)   │            │   (AWS ALB)           │    │
│  └──────────────────┘            └──────────┬───────────┘    │
│                                             │                  │
│                         ┌───────────────────┼────────────┐   │
│                         ▼                   ▼            ▼   │
│               ┌───────────────┐ ┌─────────────┐ ┌──────────┐ │
│               │ auth-service  │ │event-service│ │inscription│ │
│               │ JWT + JWKS    │ │ Catálogo    │ │+payment  │ │
│               │ :8081         │ │ :8082       │ │:8083/8084│ │
│               └───────────────┘ └─────────────┘ └──────────┘ │
│                                         │                      │
│                              ┌──────────┼──────────┐          │
│                              ▼          ▼          ▼          │
│                           ┌──────┐  ┌──────┐  ┌──────────┐   │
│                           │ RDS  │  │Redis │  │Amazon MQ │   │
│                           │Postgr│  │Cache │  │RabbitMQ  │   │
│                           └──────┘  └──────┘  └──────────┘   │
└────────────────────────────────────────────────────────────────┘
```

## 5. Decisiones arquitectónicas clave

Las decisiones de diseño se documentan como ADRs en [`02-decisiones/`](02-decisiones/). Las más relevantes:

| ADR | Decisión |
|---|---|
| ADR-014 | Cabeceras AMQP con tipo de evento para routing |
| ADR-018 | Distributed Locking + Outbox para consistencia eventual |
| ADR-019 | Dead Letter Queue para mensajes envenenados |
| ADR-021 | HMAC-SHA256 para QR de asistencia |

## 6. Atributos de calidad principales

| Atributo | Escenario | Mecanismo |
|---|---|---|
| **Disponibilidad** | Caída de un microservicio | Circuit Breaker, degradación controlada en SPA |
| **Rendimiento** | Lectura del catálogo | Cache Redis, CDN CloudFront |
| **Seguridad** | Acceso no autorizado | JWT RS256, RBAC por rol, HTTPS |
| **Escalabilidad** | Pico de inscripciones | Arquitectura preparada para Auto Scaling |
| **Trazabilidad** | Auditoría de pagos | Outbox Pattern, logs estructurados (JSON) |

## 7. Documentos relacionados

- [`01-componentes.md`](01-componentes.md) — Diagrama C4 nivel 3 por servicio
- [`03-modelo-datos.md`](03-modelo-datos.md) — ERD y diccionario de datos
- [`04-integracion.md`](04-integracion.md) — APIs REST y eventos asíncronos
- [`05-seguridad.md`](05-seguridad.md) — Modelo de seguridad y autenticación
- [`06-deployment.md`](06-deployment.md) — Topología de despliegue en AWS
