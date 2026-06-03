# Modelo de Datos — Plataforma de Gestión de Eventos Académicos

**PUJ · Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## Esquemas por servicio

Cada servicio mantiene su propio esquema en PostgreSQL. No hay joins entre esquemas: la comunicación entre servicios ocurre exclusivamente por API REST o mensajes AMQP.

| Servicio | Esquema PostgreSQL | Tablas principales |
|---|---|---|
| event-service | `public` | `evento`, `tarifa` |
| inscription-service | `inscription` | `inscripcion`, `outbox_event`, `processed_messages` |
| payment-service | `payment` | `pago`, `outbox_event`, `processed_messages` |

---

## Entidad: `evento` (event-service)

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador único |
| `titulo` | VARCHAR(255) | Nombre del evento |
| `descripcion` | TEXT | Descripción completa |
| `tipo` | ENUM | CONGRESO, SIMPOSIO, SEMINARIO, TALLER |
| `modalidad` | ENUM | PRESENCIAL, VIRTUAL, HIBRIDO |
| `fecha_inicio` | DATE | Fecha de inicio del evento |
| `fecha_fin` | DATE | Fecha de cierre del evento |
| `fecha_limite_inscripcion` | TIMESTAMP | Límite para inscribirse |
| `cupo_maximo` | INT | Capacidad total |
| `cupo_disponible` | INT | Cupos restantes (decrementado con `SELECT FOR UPDATE`) |
| `estado` | ENUM | BORRADOR, PENDIENTE_PUBLICACION, PUBLICADO, RECHAZADO, FINALIZADO, CANCELADO |
| `organizador_id` | UUID | ID del usuario organizador (del JWT) |
| `version` | BIGINT | Control de versión optimista |

## Entidad: `tarifa` (event-service)

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador único |
| `evento_id` | UUID | FK a `evento` |
| `nombre` | VARCHAR | Nombre de la tarifa |
| `precio` | DECIMAL(10,2) | Valor en la moneda indicada |
| `moneda` | VARCHAR(3) | COP, USD |
| `aplica_a` | ENUM | ESTUDIANTE_JAVERIANA, EXTERNO, etc. |
| `activa` | BOOLEAN | Si la tarifa está disponible |

## Entidad: `inscripcion` (inscription-service)

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador único |
| `usuario_id` | UUID | ID del participante (del JWT) |
| `evento_id` | UUID | ID del evento (referencia lógica, sin FK cross-schema) |
| `tarifa_id` | UUID | ID de la tarifa seleccionada |
| `estado` | ENUM | PENDIENTE_PAGO, CONFIRMADA, CANCELADA, EXPIRADA |
| `idempotency_key` | UUID | Clave de idempotencia (evita duplicados) |
| `fecha_inscripcion` | TIMESTAMP | Cuándo se creó la inscripción |
| `fecha_expiracion_pago` | TIMESTAMP | Límite para completar el pago (15 min) |
| `codigo_qr` | VARCHAR | Código QR de asistencia |
| `version` | BIGINT | Control de versión optimista |

**Constraint clave:** `UNIQUE (usuario_id, evento_id)` — un participante no puede inscribirse dos veces al mismo evento.

## Entidad: `pago` (payment-service)

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador único |
| `inscripcion_id` | UUID | Referencia lógica a la inscripción |
| `monto` | DECIMAL(10,2) | Valor pagado |
| `moneda` | VARCHAR(3) | COP, USD |
| `estado` | ENUM | PENDIENTE, APROBADO, RECHAZADO, REEMBOLSADO |
| `preferencia_id` | VARCHAR | ID de la preferencia en la pasarela externa |
| `checkout_url` | TEXT | URL de pago devuelta al cliente |
| `fecha_creacion` | TIMESTAMP | Cuándo se inició el pago |
| `fecha_confirmacion` | TIMESTAMP | Cuándo se confirmó |

---

## Diagrama simplificado (relaciones lógicas)

```
evento ──[1:N]──► tarifa
   │
   │ (referencia lógica, sin FK real)
   ▼
inscripcion ──[1:1]──► pago
```

Las referencias entre servicios se resuelven por ID en tiempo de ejecución, no mediante claves foráneas de base de datos.
