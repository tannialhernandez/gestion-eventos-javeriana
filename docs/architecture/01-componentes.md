# Componentes del Sistema — Descripción por Servicio

**Plataforma de Gestión de Eventos Académicos · PUJ**  
**Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## Aplicación SPA (Frontend)

**Tecnología:** React 18 + TypeScript + Vite  
**Repositorio:** `frontend/`

| Módulo | Responsabilidad |
|---|---|
| `features/auth` | Inicio y cierre de sesión, contexto de usuario |
| `features/catalog` | Listado y búsqueda de eventos |
| `features/events` | Detalle de evento, inscripción, flujo de pago |
| `features/checkout` | Resumen de pago y confirmación |
| `features/event-management` | Creación y edición de eventos (ORGANIZADOR/ADMIN) |

---

## auth-service

**Puerto:** 8081  
**Responsabilidad:** Autenticación de usuarios y emisión de JWT RS256.

| Endpoint | Descripción |
|---|---|
| `POST /api/v1/auth/login` | Autentica credenciales y emite JWT |
| `GET /api/v1/auth/.well-known/jwks.json` | Publica clave pública RSA para validación |

**Notas:** Actualmente integra credenciales locales. La integración con Azure AD institucional está planificada para Fase 2.

---

## event-service

**Puerto:** 8082  
**Responsabilidad:** Gestión completa del ciclo de vida de eventos.

| Endpoint | Rol mínimo | Descripción |
|---|---|---|
| `GET /api/v1/eventos` | Público | Catálogo con filtros por rol |
| `POST /api/v1/eventos` | ORGANIZADOR | Crear evento en BORRADOR |
| `PUT /api/v1/eventos/{id}` | ORGANIZADOR | Actualizar evento propio |
| `DELETE /api/v1/eventos/{id}` | ORGANIZADOR | Eliminar (solo BORRADOR) |
| `POST /api/v1/eventos/{id}/enviar-revision` | ORGANIZADOR | BORRADOR → PENDIENTE_PUBLICACION |
| `POST /api/v1/eventos/{id}/aprobar` | ADMIN | PENDIENTE_PUBLICACION → PUBLICADO |
| `POST /api/v1/eventos/{id}/rechazar` | ADMIN | PENDIENTE_PUBLICACION → RECHAZADO |
| `POST /api/v1/eventos/{id}/cancelar` | ORGANIZADOR/ADMIN | → CANCELADO |
| `GET /api/v1/tarifas` | Público | Tarifas de un evento |

**Ciclo de vida del evento:**

```
BORRADOR ──enviar-revision──► PENDIENTE_PUBLICACION
                                      │
                         ┌────────────┴────────────┐
                         ▼                         ▼
                      aprobar                  rechazar
                         │                         │
                         ▼                         ▼
                     PUBLICADO               RECHAZADO
                         │                         │
                         └──────────cancelar───────┘
                                        │
                                    CANCELADO
```

---

## inscription-service

**Puerto:** 8083  
**Responsabilidad:** Inscripciones con control de cupos y gestión de pagos pendientes.

| Endpoint | Rol mínimo | Descripción |
|---|---|---|
| `POST /api/v1/inscripciones` | PARTICIPANTE | Crear inscripción (reserva cupo) |
| `GET /api/v1/inscripciones` | PARTICIPANTE | Mis inscripciones |
| `DELETE /api/v1/inscripciones/{id}` | PARTICIPANTE | Cancelar inscripción |

**Patrones:** Idempotencia por `idempotencyKey` · Outbox para notificación de inscripción confirmada.

---

## payment-service

**Puerto:** 8084  
**Responsabilidad:** Preferencias de pago, webhook de confirmación y auditoría.

| Endpoint | Rol mínimo | Descripción |
|---|---|---|
| `POST /api/v1/pagos/preferencias` | PARTICIPANTE | Crear preferencia de pago |
| `POST /api/v1/webhooks/pagos` | Servicio | Confirmar pago desde pasarela |
| `GET /api/v1/pagos/{id}` | PARTICIPANTE | Estado de un pago |

**Integración de pagos:** Actualmente usa pasarela en modo de prueba. La integración con Mercado Pago en producción está planificada para Fase 2.

---

## Infraestructura de datos

| Componente | Uso |
|---|---|
| PostgreSQL (RDS) | Persistencia principal — esquema separado por servicio |
| Redis (ElastiCache) | Caché del catálogo de eventos (TTL: 5 min) |
| RabbitMQ (Amazon MQ) | Bus de eventos — inscripción confirmada, pago procesado |
