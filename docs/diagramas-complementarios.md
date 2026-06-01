# Diagramas Complementarios — Modelo E-R, Topología RabbitMQ y Actividad Outbox

**Plataforma de Gestión de Eventos Académicos — Javeriana 2026**  
Maestría en Ingeniería de Software · Diseño de Software Basado en Patrones · Entrega 3

---

## Índice

1. [Modelo Entidad-Relación Consolidado](#1-modelo-entidad-relación-consolidado)
2. [Topología de Mensajería RabbitMQ](#2-topología-de-mensajería-rabbitmq)
3. [Diagrama de Actividad: OutboxRelayService](#3-diagrama-de-actividad-outboxrelayservice)

---

## 1. Modelo Entidad-Relación Consolidado

**Cabecera:** Modelo Entidad-Relación Consolidado — Plataforma de Gestión de Eventos Académicos | PostgreSQL 15 | DB per Service Pattern

### 1.1 PlantUML (crow's foot)

Archivo: [er-consolidado.puml](diagramas/er-consolidado.puml)

```plantuml
@startuml er-consolidado
!theme plain
hide circle
skinparam linetype ortho
skinparam defaultFontSize 9
skinparam shadowing false

' event_db
package "event_db  (event-service)" as db_event #C8EDCB {
  entity "categoria" as cat {
    * id : UUID <<PK>>
    --
    nombre : VARCHAR(100) <<UNIQUE>>
    descripcion : TEXT
    creado_en : TIMESTAMPTZ
  }
  entity "evento" as evt {
    * id : UUID <<PK>>
    --
    nombre : VARCHAR(200)
    cupos_totales : INT
    cupos_disponibles : INT
    precio : NUMERIC(10,2)
    estado : VARCHAR(20)
    organizador_id : UUID
    categoria_id : UUID <<FK>>
    creado_en : TIMESTAMPTZ
    actualizado_en : TIMESTAMPTZ
    --
    INDEX idx_evento_estado_fecha
  }
  entity "outbox_event [event_db]" as outbox_evt {
    * id : UUID <<PK>>
    --
    aggregate_type : VARCHAR(50)
    aggregate_id : UUID
    event_type : VARCHAR(100)
    payload : JSONB
    estado : VARCHAR(20)
    intentos : INT DEFAULT 0
    creado_en : TIMESTAMPTZ
    enviado_en : TIMESTAMPTZ
    --
    INDEX idx_outbox_estado_creado
  }
  evt }o--|| cat : "categoria_id"
}

' inscription_db
package "inscription_db  (inscription-service)" as db_ins #FFD7D7 {
  entity "evento_proyeccion" as evt_proj {
    * id : UUID <<PK>>
    --
    nombre : VARCHAR(200)
    cupos_disponibles : INT
    estado : VARCHAR(20)
    --
    SELECT FOR UPDATE (ADR-012)
  }
  entity "inscripcion" as ins {
    * id : UUID <<PK>>
    --
    usuario_id : UUID
    evento_id : UUID <<FK>>
    estado : VARCHAR(20)
    creado_en : TIMESTAMPTZ
    expira_en : TIMESTAMPTZ
    confirmado_en : TIMESTAMPTZ
    referencia_pago : VARCHAR(100)
    --
    UNIQUE (usuario_id, evento_id)
    INDEX idx_inscripcion_expira
  }
  entity "outbox_event [inscription_db]" as outbox_ins {
    * id : UUID <<PK>>
    --
    aggregate_type : VARCHAR(50)
    aggregate_id : UUID
    event_type : VARCHAR(100)
    payload : JSONB
    estado : VARCHAR(20)
    intentos : INT DEFAULT 0
    creado_en : TIMESTAMPTZ
    enviado_en : TIMESTAMPTZ
    --
    INDEX idx_outbox_estado_creado
  }
  entity "shedlock  [ADR-018]" as shedlock {
    * name : VARCHAR(64) <<PK>>
    --
    lock_until : TIMESTAMP
    locked_at : TIMESTAMP
    locked_by : VARCHAR(255)
  }
  ins }o--|| evt_proj : "evento_id"
}

' payment_db
package "payment_db  (payment-service)" as db_pay #FFE0B2 {
  entity "pago" as pago {
    * id : UUID <<PK>>
    --
    inscripcion_id : UUID <<UNIQUE>>
    monto : NUMERIC(10,2)
    moneda : VARCHAR(3)
    estado : VARCHAR(20)
    referencia_pasarela : VARCHAR(100)
    intentos : INT DEFAULT 0
    creado_en : TIMESTAMPTZ
    confirmado_en : TIMESTAMPTZ
  }
  entity "mensaje_procesado  [idempotencia]" as msg_proc {
    * message_id : UUID <<PK>>
    --
    procesado_en : TIMESTAMPTZ
  }
  entity "outbox_event [payment_db]\n[PENDIENTE]" as outbox_pay {
    * id : UUID <<PK>>
    --
    aggregate_type : VARCHAR(50)
    aggregate_id : UUID
    event_type : VARCHAR(100)
    payload : JSONB
    estado : VARCHAR(20)
    intentos : INT DEFAULT 0
    creado_en : TIMESTAMPTZ
    enviado_en : TIMESTAMPTZ
    --
    INDEX idx_outbox_estado_creado
  }
}

' notification_db
package "notification_db  (notification-service)" as db_notif #FFF9C4 {
  entity "notificacion" as notif {
    * id : UUID <<PK>>
    --
    message_id : UUID <<UNIQUE>>
    tipo : VARCHAR(50)
    canal : VARCHAR(20)
    destinatario : VARCHAR(255)
    contenido : TEXT
    estado : VARCHAR(20)
    intentos : INT DEFAULT 0
    creado_en : TIMESTAMPTZ
    enviado_en : TIMESTAMPTZ
    --
    INDEX idx_notif_message_id
  }
}

' Correlaciones logicas cross-database
evt      ..> evt_proj  : "<<correlacion logica>>\nevento.id (projection AMQP)"
ins      ..> pago      : "<<correlacion logica>>\ninscripcion_id (sin FK)"
outbox_ins ..> msg_proc : "<<correlacion logica>>\noutbox.id = message_id"
outbox_pay ..> notif   : "<<correlacion logica>>\noutbox.id = message_id"

@enduml
```

### 1.2 Mermaid erDiagram

```mermaid
erDiagram

  %% ── event_db ──────────────────────────────────────────
  CATEGORIA {
    UUID id PK
    VARCHAR nombre UK
    TEXT descripcion
    TIMESTAMPTZ creado_en
  }

  EVENTO {
    UUID id PK
    VARCHAR nombre
    TEXT descripcion
    TIMESTAMPTZ fecha_inicio
    TIMESTAMPTZ fecha_fin
    INT cupos_totales
    INT cupos_disponibles
    NUMERIC precio
    VARCHAR estado
    UUID organizador_id
    UUID categoria_id FK
    TIMESTAMPTZ creado_en
    TIMESTAMPTZ actualizado_en
  }

  OUTBOX_EVENT_EVT {
    UUID id PK
    VARCHAR aggregate_type
    UUID aggregate_id
    VARCHAR event_type
    JSONB payload
    VARCHAR estado
    INT intentos
    TIMESTAMPTZ creado_en
    TIMESTAMPTZ enviado_en
  }

  EVENTO }o--|| CATEGORIA : "categoria_id"

  %% ── inscription_db ────────────────────────────────────
  EVENTO_PROYECCION {
    UUID id PK
    VARCHAR nombre
    INT cupos_disponibles
    VARCHAR estado
  }

  INSCRIPCION {
    UUID id PK
    UUID usuario_id
    UUID evento_id FK
    VARCHAR estado
    TIMESTAMPTZ creado_en
    TIMESTAMPTZ expira_en
    TIMESTAMPTZ confirmado_en
    VARCHAR referencia_pago
  }

  OUTBOX_EVENT_INS {
    UUID id PK
    VARCHAR aggregate_type
    UUID aggregate_id
    VARCHAR event_type
    JSONB payload
    VARCHAR estado
    INT intentos
    TIMESTAMPTZ creado_en
    TIMESTAMPTZ enviado_en
  }

  SHEDLOCK {
    VARCHAR name PK
    TIMESTAMP lock_until
    TIMESTAMP locked_at
    VARCHAR locked_by
  }

  INSCRIPCION }o--|| EVENTO_PROYECCION : "evento_id"

  %% ── payment_db ────────────────────────────────────────
  PAGO {
    UUID id PK
    UUID inscripcion_id UK
    NUMERIC monto
    VARCHAR moneda
    VARCHAR estado
    VARCHAR referencia_pasarela
    INT intentos
    TIMESTAMPTZ creado_en
    TIMESTAMPTZ confirmado_en
  }

  MENSAJE_PROCESADO {
    UUID message_id PK
    TIMESTAMPTZ procesado_en
  }

  OUTBOX_EVENT_PAY {
    UUID id PK
    VARCHAR aggregate_type
    UUID aggregate_id
    VARCHAR event_type
    JSONB payload
    VARCHAR estado
    INT intentos
    TIMESTAMPTZ creado_en
    TIMESTAMPTZ enviado_en
  }

  %% ── notification_db ───────────────────────────────────
  NOTIFICACION {
    UUID id PK
    UUID message_id UK
    VARCHAR tipo
    VARCHAR canal
    VARCHAR destinatario
    TEXT contenido
    VARCHAR estado
    INT intentos
    TIMESTAMPTZ creado_en
    TIMESTAMPTZ enviado_en
  }

  %% Correlaciones logicas (no FK reales)
  INSCRIPCION ||--o| PAGO : "inscripcion_id (logica)"
  OUTBOX_EVENT_INS ||--o| MENSAJE_PROCESADO : "id = message_id (logica)"
  OUTBOX_EVENT_PAY ||--o| NOTIFICACION : "id = message_id (logica)"
```

### 1.3 Tabla de tablas críticas

| Tabla | Base de datos | Propósito | Volumen estimado | Índice clave | Restricción de integridad |
|---|---|---|---|---|---|
| `evento` | event_db | Catálogo de eventos publicados | ~1.000 filas / año | `idx_evento_estado_fecha(estado, fecha_inicio)` | — |
| `categoria` | event_db | Taxonomía de eventos | ~20 filas estable | — | `UNIQUE(nombre)` |
| `outbox_event` (event) | event_db | Buffer durable para Outbox Relay | ~5.000 filas / día (purgado) | `idx_outbox_estado_creado(estado, creado_en)` | — |
| `inscripcion` | inscription_db | Registro de inscripciones con estado | ~10.000 filas / año | `idx_inscripcion_expira(estado, expira_en)` | `UNIQUE(usuario_id, evento_id)` |
| `evento_proyeccion` | inscription_db | Proyección local del catálogo para control de cupos | ~1.000 filas | — | `SELECT FOR UPDATE` (ADR-012) |
| `shedlock` | inscription_db | Coordinación distribuida de jobs @Scheduled | 2 filas fijas | — | `PK(name)` (ADR-018) |
| `pago` | payment_db | Registro de pagos y su estado | ~10.000 filas / año | — | `UNIQUE(inscripcion_id)` |
| `mensaje_procesado` | payment_db | Idempotencia de mensajes AMQP consumidos | ~20.000 filas / año | — | `PK(message_id)` |
| `notificacion` | notification_db | Log de notificaciones enviadas | ~30.000 filas / año | `idx_notif_message_id(message_id)` | `UNIQUE(message_id)` |

### 1.4 Relaciones cross-database (correlaciones lógicas — sin FK real)

| ID origen | Tabla origen | Base datos origen | ID destino | Tabla destino | Base datos destino | Mecanismo de correlación |
|---|---|---|---|---|---|---|
| `evento.id` | evento | event_db | `evento_proyeccion.id` | evento_proyeccion | inscription_db | Proyección replicada vía `event.published` AMQP |
| `inscripcion.id` | inscripcion | inscription_db | `pago.inscripcion_id` | pago | payment_db | `inscription.created` AMQP — payment-service recibe el ID |
| `outbox_event.id` | outbox_event | inscription_db | `mensaje_procesado.message_id` | mensaje_procesado | payment_db | `messageId = outbox.id` en el header AMQP |
| `outbox_event.id` | outbox_event | payment_db | `notificacion.message_id` | notificacion | notification_db | `messageId = outbox.id` en el header AMQP |

### 1.5 Decisiones clave del modelo

**DB per Service — sin FKs entre bases de datos**  
Cada microservicio posee su esquema de forma exclusiva. La consistencia entre bases se logra mediante eventos AMQP (choreography) y el patrón Outbox, nunca mediante joins ni transacciones distribuidas. Esto materializa ADR-012 (Hexagonal) y ADR-011 (Outbox).

**`UNIQUE(usuario_id, evento_id)` como invariante de negocio**  
La constraint de unicidad en `inscripcion` evita doble inscripción incluso en escenarios de alta concurrencia donde el `SELECT FOR UPDATE` podría no ser suficiente (e.g., retries de la API con idempotency-key diferente). Es la última línea de defensa en la base de datos.

**Tabla `outbox_event` replicada en 3 servicios productores**  
event-service, inscription-service y payment-service tienen cada uno su propia tabla `outbox_event`. Esta replicación del esquema es intencional: cada servicio es independiente. El patrón Outbox requiere que el evento se persista en la misma transacción que el estado de negocio — imposible con una tabla compartida sin violer el DB per Service pattern.

**Tabla `mensaje_procesado` / `UNIQUE(message_id)` para idempotencia**  
Los consumers AMQP pueden recibir el mismo mensaje más de una vez (at-least-once delivery). La tabla `mensaje_procesado` en payment_db y el campo `message_id UNIQUE` en `notificacion` garantizan que el procesamiento sea idempotente: si el mensaje llega dos veces, la segunda inserción falla con `UNIQUE violation` y se descarta.

**`shedlock` como mecanismo de coordinación distribuida (ADR-018)**  
La tabla `shedlock` en inscription_db tiene exactamente 2 filas activas: una para `expirar-pendientes` y otra para `outbox-relay`. Son los dos jobs `@Scheduled` que no deben ejecutarse en paralelo en instancias múltiples. ShedLock usa `UPDATE ... WHERE lock_until < NOW()` atómico para adquirir el lock.

### 1.6 Scripts Flyway sugeridos

```sql
-- V1__init.sql (inscription_db)
CREATE TABLE evento_proyeccion (
    id UUID PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    cupos_disponibles INT NOT NULL,
    estado VARCHAR(20) NOT NULL
);

CREATE TABLE inscripcion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    evento_id UUID NOT NULL REFERENCES evento_proyeccion(id),
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expira_en TIMESTAMPTZ NOT NULL,
    confirmado_en TIMESTAMPTZ,
    referencia_pago VARCHAR(100),
    CONSTRAINT uq_usuario_evento UNIQUE (usuario_id, evento_id)
);
```

```sql
-- V2__outbox.sql (inscription_db / event_db / payment_db)
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    intentos INT NOT NULL DEFAULT 0,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    enviado_en TIMESTAMPTZ
);
```

```sql
-- V3__shedlock.sql (solo inscription_db — ADR-018)
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
```

```sql
-- V4__indices.sql
CREATE INDEX idx_outbox_estado_creado
    ON outbox_events (estado, creado_en)
    WHERE estado = 'PENDIENTE';

CREATE INDEX idx_inscripcion_expira
    ON inscripcion (estado, expira_en)
    WHERE estado = 'PENDIENTE';

CREATE INDEX idx_evento_estado_fecha
    ON evento (estado, fecha_inicio)
    WHERE estado = 'PUBLICADO';
```

---

## 2. Topología de Mensajería RabbitMQ

**Cabecera:** Topología de Mensajería RabbitMQ — Plataforma de Gestión de Eventos Académicos | Amazon MQ for RabbitMQ | Patrón Pub-Sub Fan-Out vía Transactional Outbox

### 2.1 PlantUML

Archivo: [topologia-rabbitmq.puml](diagramas/topologia-rabbitmq.puml)

```plantuml
@startuml topologia-rabbitmq
!theme plain
skinparam backgroundColor #FAFAFA
skinparam defaultFontSize 9
skinparam shadowing false
skinparam roundcorner 6

title
  <b>Topologia RabbitMQ -- Plataforma Gestion Eventos Academicos</b>
  Amazon MQ | Pub-Sub Fan-Out | Transactional Outbox
end title

' Productores
rectangle "event-service\n[Producer]" as PROD_EVT #FFE0B2
rectangle "inscription-service\n[Producer]" as PROD_INS #FFE0B2
rectangle "payment-service\n[Producer 70%]" as PROD_PAY #FFCCCC

' Exchanges
rectangle "events.exchange\n[TOPIC]" as EX_EVT #BBDEFB
rectangle "inscriptions.exchange\n[TOPIC]" as EX_INS #BBDEFB
rectangle "payments.exchange\n[TOPIC]" as EX_PAY #BBDEFB
rectangle "dlx.exchange\n[DLX]" as DLX #FFCDD2

' Queues seleccionadas (principales)
rectangle "payment.inscription-created.queue" as Q1 #C8E6C9
rectangle "notification.inscription-created.queue" as Q2 #C8E6C9
rectangle "inscription.payment-confirmed.queue" as Q3 #C8E6C9
rectangle "notification.payment-confirmed.queue" as Q4 #C8E6C9
rectangle "inscription.payment-failed.queue" as Q5 #C8E6C9
rectangle "notification.payment-failed.queue" as Q6 #C8E6C9

' DLQs
rectangle "*.dlq" as DLQS #FFCDD2

' Consumidores
rectangle "inscription-service\n[Consumer]" as CONS_INS #E1BEE7
rectangle "payment-service\n[Consumer]" as CONS_PAY #E1BEE7
rectangle "notification-service\n[Consumer only]" as CONS_NOT #E1BEE7

PROD_EVT --> EX_EVT : "via Outbox Relay"
PROD_INS --> EX_INS : "via Outbox Relay"
PROD_PAY --> EX_PAY : "PENDIENTE"

EX_INS --> Q1 : "inscription.created [FAN-OUT]"
EX_INS --> Q2 : "inscription.created [FAN-OUT]"
EX_PAY --> Q3 : "payment.confirmed [FAN-OUT]"
EX_PAY --> Q4 : "payment.confirmed [FAN-OUT]"
EX_PAY --> Q5 : "payment.failed"
EX_PAY --> Q6 : "payment.failed"

Q1 --> CONS_PAY
Q2 --> CONS_NOT
Q3 --> CONS_INS
Q4 --> CONS_NOT
Q5 --> CONS_INS
Q6 --> CONS_NOT

Q1 ..> DLX : "3 reintentos"
Q3 ..> DLX : "3 reintentos"
DLX --> DLQS : "routing original"

@enduml
```

### 2.2 Mermaid flowchart

```mermaid
flowchart LR
  subgraph PROD["Productores"]
    EVT_SVC["event-service\nProducer"]
    INS_SVC["inscription-service\nProducer"]
    PAY_SVC["payment-service\nProducer 70%"]
  end

  subgraph EX["Exchanges TOPIC"]
    EX_EVT["events.exchange"]
    EX_INS["inscriptions.exchange"]
    EX_PAY["payments.exchange"]
    DLX["dlx.exchange\nDead Letter"]
  end

  subgraph Q_EVT["Queues event-service"]
    Q_PROJ["inscription.event-projection.queue"]
    Q_CANC_I["inscription.event-cancelled.queue"]
    Q_CANC_N["notification.event-cancelled.queue"]
  end

  subgraph Q_INS["Queues inscription-service"]
    Q_INS_P["payment.inscription-created.queue"]
    Q_INS_N["notification.inscription-created.queue"]
    Q_EXP["notification.inscription-expired.queue"]
    Q_CNC_N["notification.inscription-cancelled.queue"]
    Q_CNC_P["payment.inscription-cancelled.queue"]
  end

  subgraph Q_PAY["Queues payment-service"]
    Q_CONF_I["inscription.payment-confirmed.queue"]
    Q_CONF_N["notification.payment-confirmed.queue"]
    Q_FAIL_I["inscription.payment-failed.queue"]
    Q_FAIL_N["notification.payment-failed.queue"]
  end

  subgraph DLQ["Dead Letter Queues"]
    DLQ1["*.dlq"]
  end

  subgraph CONS["Consumidores"]
    INS_C["inscription-service"]
    PAY_C["payment-service"]
    NOT_C["notification-service"]
  end

  EVT_SVC -->|"via Outbox"| EX_EVT
  INS_SVC -->|"via Outbox"| EX_INS
  PAY_SVC -->|"PENDIENTE"| EX_PAY

  EX_EVT -->|"event.published"| Q_PROJ
  EX_EVT -->|"event.cancelled"| Q_CANC_I & Q_CANC_N

  EX_INS -->|"inscription.created FAN-OUT"| Q_INS_P & Q_INS_N
  EX_INS -->|"inscription.expired"| Q_EXP
  EX_INS -->|"inscription.cancelled"| Q_CNC_N & Q_CNC_P

  EX_PAY -->|"payment.confirmed FAN-OUT"| Q_CONF_I & Q_CONF_N
  EX_PAY -->|"payment.failed"| Q_FAIL_I & Q_FAIL_N

  Q_PROJ & Q_CANC_I --> INS_C
  Q_CANC_N & Q_INS_N & Q_EXP & Q_CNC_N & Q_CONF_N & Q_FAIL_N --> NOT_C
  Q_INS_P & Q_CNC_P --> PAY_C
  Q_CONF_I & Q_FAIL_I --> INS_C

  Q_INS_P & Q_INS_N & Q_CONF_I & Q_CONF_N & Q_FAIL_I & Q_FAIL_N -.->|"3 reintentos"| DLX
  DLX --> DLQ1
```

### 2.3 Tabla maestra Exchange / Queue

| Exchange | Routing Key | Queue | Productor | Consumidor | DLQ asociada | Estado |
|---|---|---|---|---|---|---|
| events.exchange | event.published | inscription.event-projection.queue | event-service | inscription-service | inscription.event-projection.dlq | PROD |
| events.exchange | event.cancelled | inscription.event-cancelled.queue | event-service | inscription-service | inscription.event-cancelled.dlq | PROD |
| events.exchange | event.cancelled | notification.event-cancelled.queue | event-service | notification-service | notification.event-cancelled.dlq | PROD |
| inscriptions.exchange | inscription.created | payment.inscription-created.queue | inscription-service | payment-service | payment.inscription-created.dlq | PROD |
| inscriptions.exchange | inscription.created | notification.inscription-created.queue | inscription-service | notification-service | notification.inscription-created.dlq | PROD |
| inscriptions.exchange | inscription.expired | notification.inscription-expired.queue | inscription-service | notification-service | notification.inscription-expired.dlq | PROD |
| inscriptions.exchange | inscription.cancelled | notification.inscription-cancelled.queue | inscription-service | notification-service | notification.inscription-cancelled.dlq | PROD |
| inscriptions.exchange | inscription.cancelled | payment.inscription-cancelled.queue | inscription-service | payment-service | payment.inscription-cancelled.dlq | PROD |
| payments.exchange | payment.confirmed | inscription.payment-confirmed.queue | payment-service | inscription-service | inscription.payment-confirmed.dlq | **PENDIENTE** |
| payments.exchange | payment.confirmed | notification.payment-confirmed.queue | payment-service | notification-service | notification.payment-confirmed.dlq | **PENDIENTE** |
| payments.exchange | payment.failed | inscription.payment-failed.queue | payment-service | inscription-service | inscription.payment-failed.dlq | **PENDIENTE** |
| payments.exchange | payment.failed | notification.payment-failed.queue | payment-service | notification-service | notification.payment-failed.dlq | **PENDIENTE** |

> **PENDIENTE:** Las queues de payment-service están configuradas en `RabbitMqTopologyConfig`, pero `OutboxRelayService` de payment-service no está implementado (rama `feat/payment-outbox-e2e`). Los mensajes no llegarán hasta completar esa rama.

### 2.4 Tabla de fan-out

| Evento AMQP | Routing key | Nº consumidores | Queues | Justificación |
|---|---|---|---|---|
| InscripcionCreada | `inscription.created` | 2 | payment + notification | payment-service inicia el proceso de pago; notification-service informa al estudiante |
| PaymentConfirmed | `payment.confirmed` | 2 | inscription + notification | inscription-service confirma la inscripción; notification-service envía recibo |
| PaymentFailed | `payment.failed` | 2 | inscription + notification | inscription-service cancela la inscripción y libera cupo; notification-service informa rechazo |
| EventoCancelado | `event.cancelled` | 2 | inscription + notification | inscription-service cancela inscripciones activas; notification-service informa a estudiantes |

### 2.5 Garantías de entrega

| Garantía | Mecanismo | Dónde se configura |
|---|---|---|
| **Publisher confirms** | `RabbitTemplate.setConfirmCallback()` — el broker confirma recepción antes de marcar el Outbox como ENVIADO | `RabbitMqConfig` en cada servicio productor |
| **Consumer ACK manual** | `AcknowledgeMode.MANUAL` — el listener hace ACK solo después de persistir el resultado en BD | `SimpleRabbitListenerContainerFactory` |
| **Idempotencia** | `UNIQUE(message_id)` en `notificacion`; tabla `mensaje_procesado` en payment_db | Base de datos + consumer logic |
| **Reintentos** | `RetryTemplate` con backoff exponencial, 3 intentos antes de DLQ | `RabbitMqTopologyConfig` + `x-dead-letter-exchange` |
| **At-least-once** | El Outbox garantiza que el evento se publique al menos una vez al recuperarse el broker | `OutboxRelayService` @Scheduled |

### 2.6 Naming convention

| Elemento | Patrón | Ejemplo |
|---|---|---|
| Exchange | `<dominio>.exchange` | `inscriptions.exchange` |
| Routing key | `<agregado>.<evento-en-pasado>` | `inscription.created` |
| Queue | `<servicio-consumidor>.<evento-origen>.queue` | `payment.inscription-created.queue` |
| DLQ | `<queue-original>.dlq` | `payment.inscription-created.dlq` |
| DLX | `dlx.exchange` (único global) | `dlx.exchange` |

### 2.7 Estado payment-service

Las queues `inscription.payment-confirmed.queue` y `notification.payment-confirmed.queue` están **declaradas y configuradas** en `RabbitMqTopologyConfig` de inscription-service y notification-service (como consumidores). Sin embargo, el **productor** (payment-service) no publica a `payments.exchange` porque `OutboxRelayService` está pendiente de implementar en la rama `feat/payment-outbox-e2e`. Esto bloquea:
- La confirmación automática de inscripciones tras pago exitoso.
- Las notificaciones de pago confirmado/rechazado.
- Los tests E2E del flujo completo.

---

## 3. Diagrama de Actividad: OutboxRelayService

**Cabecera:** Diagrama de Actividad — OutboxRelayService | Transactional Outbox Pattern (ADR-011) | Plataforma de Gestión de Eventos Académicos

### 3.1 PlantUML (actividad con swimlanes)

Archivo: [actividad-outbox-relay.puml](diagramas/actividad-outbox-relay.puml)

```plantuml
@startuml actividad-outbox-relay
!theme plain
skinparam backgroundColor #FAFAFA
skinparam defaultFontSize 9

title
  <b>Diagrama de Actividad -- OutboxRelayService (ADR-011)</b>
  event-service (OK) | inscription-service (OK) | payment-service (PENDIENTE)
end title

|OutboxRelayService|
start
:Trigger @Scheduled(fixedDelay=2000ms);

|PostgreSQL|
:BEGIN TRANSACTION;
:SELECT outbox_events WHERE estado='PENDIENTE'
 AND intentos < 5
 FOR UPDATE SKIP LOCKED LIMIT 50;

|OutboxRelayService|
if (hay eventos pendientes?) then (SI)
  while (quedan eventos?) is (SI)
    :Construir mensaje AMQP
     messageId = outbox.id;
    |RabbitMQ|
    :Publicar via RabbitTemplate
     publisher confirms activos;
    if (broker ACK?) then (SI)
      |PostgreSQL|
      :UPDATE SET estado='ENVIADO';
      |CloudWatch|
      :outbox.published.rate++;
      |OutboxRelayService|
    else (NACK)
      |PostgreSQL|
      :UPDATE SET intentos=intentos+1;
      if (intentos >= 5?) then (SI)
        :UPDATE SET estado='FALLIDO';
        |CloudWatch|
        :outbox.failed.count++;
        :Alarma CloudWatch;
        |RabbitMQ|
        :Publicar a dlx.exchange;
        |OutboxRelayService|
      else (NO)
        |CloudWatch|
        :Log WARN reintento;
        |OutboxRelayService|
      endif
    endif
  endwhile (NO)
  |PostgreSQL|
  :COMMIT;
else (NO)
  |PostgreSQL|
  :COMMIT vacio;
endif

|CloudWatch|
:outbox.poll.duration (histogram);
|OutboxRelayService|
stop

@enduml
```

### 3.2 Mermaid flowchart TD

```mermaid
flowchart TD
  START([Trigger @Scheduled\nfixedDelay=2000ms]) --> TX

  subgraph PG["PostgreSQL"]
    TX["BEGIN TRANSACTION\nSELECT outbox_events\nWHERE estado=PENDIENTE\nFOR UPDATE SKIP LOCKED\nLIMIT 50"]
  end

  TX --> CHECK{hay eventos\npendientes?}
  CHECK -->|NO| COMMIT_EMPTY["COMMIT vacio"]
  COMMIT_EMPTY --> METRIC_EMPTY["outbox.pending.count=0"]
  METRIC_EMPTY --> STOP([fin iteracion])

  CHECK -->|SI| LOOP_START["Iniciar loop\nsobre el lote"]

  LOOP_START --> HAS_MORE{quedan\neventos?}
  HAS_MORE -->|NO| COMMIT["COMMIT\nlibera SKIP LOCKED"]
  COMMIT --> METRIC_DUR["outbox.poll.duration\nhistogram"]
  METRIC_DUR --> STOP

  HAS_MORE -->|SI| BUILD["Construir mensaje AMQP\nmessageId = outbox.id"]
  BUILD --> PUBLISH["Publicar a exchange\nvia RabbitTemplate\npublisher confirms activos"]
  PUBLISH --> ACK{broker\nconfirma?}

  ACK -->|ACK| SET_SENT["UPDATE outbox SET\nestado=ENVIADO\nenviado_en=NOW()"]
  SET_SENT --> METRIC_OK["outbox.published.rate++\nLog INFO"]
  METRIC_OK --> HAS_MORE

  ACK -->|NACK/timeout| INC["UPDATE outbox SET\nintentos=intentos+1"]
  INC --> MAX{intentos\n>= 5?}

  MAX -->|NO| LOG_WARN["Log WARN:\nreintento en siguiente ciclo\nbackoff implicito 2s"]
  LOG_WARN --> HAS_MORE

  MAX -->|SI| SET_FAILED["UPDATE outbox SET\nestado=FALLIDO"]
  SET_FAILED --> ALARM["outbox.failed.count++\nAlarma CloudWatch\nLog ERROR"]
  ALARM --> POISON["Publicar a dlx.exchange\nevenvenado: intervencion manual"]
  POISON --> HAS_MORE

  style START fill:#2E7D32,color:#fff
  style STOP fill:#C62828,color:#fff
  style CHECK fill:#FFF9C4
  style ACK fill:#FFF9C4
  style MAX fill:#FFF9C4
  style HAS_MORE fill:#FFF9C4
```

### 3.3 Tabla de estados de un OutboxEvent

| Estado | Significado | Transiciones permitidas | Acción del relay |
|---|---|---|---|
| `PENDIENTE` | Evento registrado en la transacción de negocio, pendiente de publicar | → `ENVIADO`, → `PENDIENTE` (incrementa intentos) | SELECT FOR UPDATE SKIP LOCKED + intento de publicación |
| `ENVIADO` | Publicado exitosamente a RabbitMQ con publisher confirm | — (estado final exitoso) | Ignorado en el SELECT (filtro `WHERE estado='PENDIENTE'`) |
| `FALLIDO` | Máximo de intentos alcanzado (5). Requiere intervención | — (estado final de error) | Publicado a `dlx.exchange`; ignorado en próximos polls |

### 3.4 Métricas a exponer en CloudWatch

| Métrica | Tipo | Descripción | Umbral de alerta |
|---|---|---|---|
| `outbox.pending.count` | Gauge | Número de eventos en estado `PENDIENTE` en la tabla outbox | > 100 durante > 5 min |
| `outbox.published.rate` | Counter | Eventos publicados exitosamente por segundo | — (informativa) |
| `outbox.failed.count` | Counter | Eventos que alcanzaron `intentos >= 5` | > 0 (cualquier fallo) |
| `outbox.poll.duration` | Histogram (ms) | Tiempo de cada iteración del relay (SELECT + publish + UPDATE) | p99 > 500 ms |

### 3.5 Pseudocódigo Java del OutboxRelayService

```java
@Component
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxRepository outboxRepository;   // Puerto hexagonal
    private final EventPublisher eventPublisher;        // Puerto hexagonal
    private static final int BATCH_SIZE = 50;
    private static final int MAX_INTENTOS = 5;

    @Scheduled(fixedDelay = 2000)
    @SchedulerLock(                                     // ADR-018 (solo inscription-service)
        name = "outbox-relay",
        lockAtMostFor = "PT5S",
        lockAtLeastFor = "PT2S"
    )
    @Transactional
    public void procesarEventosPendientes() {
        List<OutboxEvent> pendientes = outboxRepository.buscarPendientes(BATCH_SIZE);
        // Internamente: SELECT ... FOR UPDATE SKIP LOCKED LIMIT 50

        for (OutboxEvent evento : pendientes) {
            try {
                eventPublisher.publicar(evento);
                // Internamente: RabbitTemplate con publisher confirms
                outboxRepository.marcarProcesado(evento.getId());
                // UPDATE SET estado='ENVIADO', enviado_en=NOW()

            } catch (AmqpException ex) {
                evento.incrementarIntentos();
                if (evento.getIntentos() >= MAX_INTENTOS) {
                    evento.marcarFallido();
                    // Alerta CloudWatch + publicar a dlx.exchange
                }
                outboxRepository.guardar(evento);
                // UPDATE SET intentos++ (y estado si FALLIDO)
            }
        }
        // COMMIT al salir del @Transactional — libera SKIP LOCKED
    }
}

// Puerto de dominio
interface OutboxRepository {
    List<OutboxEvent> buscarPendientes(int limite);
    OutboxEvent guardar(OutboxEvent evento);
    void marcarProcesado(UUID id);
}

// Adaptador JPA (driven adapter)
@Repository
class OutboxJpaAdapter implements OutboxRepository {

    @Override
    public List<OutboxEvent> buscarPendientes(int limite) {
        return jpaRepository.findPendientesConLock(
            PageRequest.of(0, limite)
        ).stream().map(mapper::toDomain).toList();
    }
}

// Spring Data repository con SKIP LOCKED
interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
        SELECT o FROM OutboxEventEntity o
        WHERE o.estado = 'PENDIENTE'
          AND o.intentos < 5
        ORDER BY o.creado_en ASC
        """)
    List<OutboxEventEntity> findPendientesConLock(Pageable pageable);
    // Nota: el hint -2 activa SKIP LOCKED en PostgreSQL
}
```

### 3.6 Garantías del patrón

| Garantía | Descripción |
|---|---|
| **At-least-once delivery** | El relay puede publicar el mismo evento más de una vez si falla después del publish pero antes del UPDATE. Los consumidores deben ser idempotentes (`UNIQUE(message_id)`). |
| **Consistencia eventual** | El estado de dominio (inscripción confirmada) y la propagación del evento son consistentes eventualmente. En condiciones normales, el delay es < 2 segundos. |
| **Resistencia a caídas del broker** | Si Amazon MQ cae, los eventos permanecen en `PENDIENTE` en PostgreSQL. Al recuperarse, el relay los publica automáticamente sin pérdida de datos. |
| **Escalabilidad horizontal** | `SKIP LOCKED` permite que N instancias del mismo servicio ejecuten el relay en paralelo, tomando subconjuntos disjuntos del backlog. Sin bloqueos entre instancias. |

### 3.7 Patrones GoF involucrados

| Patrón GoF | Categoría | Rol en OutboxRelayService |
|---|---|---|
| **Template Method** | Comportamiento | El esqueleto del polling (BEGIN → SELECT → loop → publish → COMMIT) es idéntico en los 3 servicios productores. Cada uno puede variar el tipo de exchange/routing key. |
| **Strategy** | Comportamiento | La estrategia de publicación puede variar por tipo de evento: `EventoPublicado` va a `events.exchange`, `InscripcionCreada` a `inscriptions.exchange`. Implementable como `PublicationStrategy` por tipo. |
| **Repository** | Arquitectural | `OutboxRepository` como puerto de dominio abstrae el mecanismo de persistencia del relay. El servicio nunca conoce JPA ni SQL directamente. |
| **Adapter** | Estructural | `OutboxJpaAdapter` adapta Spring Data JPA (con `@Lock(PESSIMISTIC_WRITE)` y SKIP LOCKED) al puerto `OutboxRepository` del dominio. |
| **Singleton** (Spring) | Creacional | `OutboxRelayService` es un bean Spring `@Component` — instancia única por contexto. En multi-instancia (varias JVMs), ShedLock previene ejecución paralela. |

---

*Generado: 2026-05-22 · Autor: Tannia Hernández · Entrega 3 — Diseño de Software Basado en Patrones · Pontificia Universidad Javeriana*
