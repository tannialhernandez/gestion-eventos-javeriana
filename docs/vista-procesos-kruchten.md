# Vista de Procesos (Kruchten 4+1)
## Plataforma de Gestión de Eventos Académicos — Pontificia Universidad Javeriana

> **Propósito:** Muestra cómo el sistema se comporta en tiempo de ejecución: procesos, threads,
> comunicación entre servicios, concurrencia y manejo de fallos.
> **Responde:** *¿Qué procesos existen, cómo interactúan y cómo se garantiza la consistencia bajo concurrencia?*
> **Audiencia:** arquitectos de software, evaluador académico.

**Stack:** Java 17 · Spring Boot 3.x · PostgreSQL 15 (RDS) · Redis 7 (ElastiCache) · RabbitMQ 3.x (Amazon MQ) · React 18/Vite (S3+CloudFront) · WireMock (mock pasarela)

---

## 1. Inventario de Procesos y Threads en Runtime

| Servicio / Componente | Tipo de proceso | Thread(s) relevantes | Responsabilidad |
|---|---|---|---|
| **inscription-service** | Tomcat HTTP worker pool | hasta 200 (NIO default) | Atender `POST/GET/DELETE /api/v1/inscripciones` |
| **inscription-service** | `OutboxRelayService` | `scheduler-1` (fixedDelay=2 s) | Poll tabla `outbox`, publicar a RabbitMQ, marcar procesado |
| **inscription-service** | `ExpirarInscripcionesPendientesService` | `scheduler-1` (fixedDelay=60 s) | Detectar inscripciones `PENDING` vencidas, expirar y devolver cupo |
| **inscription-service** | RabbitMQ AMQP listener | 1–5 (SimpleMessageListenerContainer) | Consumir `PaymentConfirmed`, `PaymentFailed` |
| **inscription-service** | ShedLock heartbeat | background | Renovar locks en tabla `shedlock` de PostgreSQL |
| **inscription-service** | HikariCP connection pool | 10 conexiones (default) | Pool JDBC hacia `inscription_db` |
| **payment-service** | Tomcat HTTP worker pool | hasta 200 | Atender `POST /api/v1/pagos/{id}/confirmar` |
| **payment-service** | RabbitMQ AMQP listener | 1–5 | Consumir `InscriptionCreated` |
| **payment-service** | `OutboxRelayService` ⚠️ | `scheduler-1` (fixedDelay=2 s) — **PENDIENTE** | Publicar `PaymentConfirmed` / `PaymentFailed` a RabbitMQ |
| **payment-service** | WireMock HTTP client | HTTP worker (sync) | Llamada síncrona a mock de pasarela de pago |
| **payment-service** | HikariCP connection pool | 10 conexiones | Pool JDBC hacia `payment_db` |
| **event-service** | Tomcat HTTP worker pool | hasta 200 | Atender consultas de catálogo público |
| **event-service** | Lettuce (Redis client) | event-loop (async) | Cache-Aside GET/SET contra Redis 7 (ElastiCache) |
| **event-service** | HikariCP connection pool | 10 conexiones | Pool JDBC hacia `event_db` |
| **notification-service** | RabbitMQ AMQP listener | 1–5 (concurrent consumers) | Consumir `InscriptionCreated`, `PaymentConfirmed`, `PaymentFailed`, `InscriptionExpired` |
| **notification-service** | Strategy + Template Method | worker thread (sync dentro de listener) | Seleccionar canal (EMAIL/SMS/PUSH) y construir notificación |
| **notification-service** | WireMock SMTP client | HTTP worker (sync) | Envío mock de email (WireMock SMTP) |
| **RabbitMQ** (Amazon MQ) | Broker externo gestionado | N/A — managed service | Routing de mensajes, persistencia de colas, DLQ, publisher confirms |
| **PostgreSQL** (RDS) | Motor de base de datos | WAL writer, autovacuum, lock manager | Persistencia ACID, gestión de `SELECT FOR UPDATE`, tabla `shedlock` |
| **Redis** (ElastiCache) | Cache en memoria | Event loop single-thread (Redis) | Caché de catálogo de eventos para event-service |

> **Estado de madurez:**
> - `inscription-service` 100% · `event-service` 100% (deuda hexagonal en EventoController)
> - `payment-service` 70% — `OutboxRelayService` en rama `feat/payment-outbox-e2e` ⚠️
> - `notification-service` en implementación

---

## 2. Escenario A: Inscripción con Bloqueo Pesimista + Outbox + Fan-out

### Descripción

1. El estudiante envía `POST /api/v1/inscripciones` a través del ALB.
2. `CrearInscripcionUseCase` abre una transacción y ejecuta `SELECT ... FOR UPDATE` sobre la fila del evento (**ADR-003**), bloqueándola hasta el commit.
3. En la **misma transacción ACID**: decrementa cupo, crea la inscripción `PENDING` con `expira_en = NOW() + 15 min`, e inserta un `OutboxEvent{InscriptionCreated}` (**ADR-011**).
4. El `OutboxRelayService` (hilo `scheduler-1`, cada 2 s) hace poll con `SKIP LOCKED` (**ADR-018**), publica al exchange `inscripcion.events` y marca el evento como procesado.
5. **Fan-out:** RabbitMQ entrega el evento a dos colas independientes. `payment-service` crea el registro de cobro; `notification-service` envía la confirmación al estudiante.

### PlantUML — Escenario A
> Render local: `java -jar ~/plantuml.jar docs/diagramas/vp-escenario-a-fanout.puml`

```plantuml
@startuml vp-escenario-a-fanout
!theme plain
skinparam maxMessageSize 120
skinparam sequenceMessageAlign left
skinparam responseMessageBelowArrow true
skinparam defaultFontSize 10
scale 0.85

title Escenario A — Inscripción: Bloqueo Pesimista + Outbox + Fan-out

actor       "Estudiante"                                as STU
participant "React SPA / ALB"                           as ALB   #EEEEEE
participant "InscripcionRest\nController"               as CTRL  #BDE5FF
participant "CrearInscripcion\nUseCase @Transactional"  as SVC   #C8EDCB
database    "PostgreSQL\n(inscription_db)"              as PG    #FFF9C4
participant "OutboxRelay\nService @Scheduled(2s)"       as RELAY #FFE0B2
queue       "RabbitMQ\n(inscripcion.events)"            as MQ    #F3E5F5
participant "InscripcionCreada\nListener (payment-svc)" as PAY   #FFD7D7
participant "InscripcionCreada\nListener (notif-svc)"   as NOTIF #EDE7F6

STU -> ALB : POST /api/v1/inscripciones {usuarioId, eventoId}
ALB -> CTRL : forward
activate CTRL
CTRL -> SVC : ejecutar(CrearInscripcionCommand)
activate SVC
note over SVC,PG #C8EDCB
  BEGIN TRANSACTION
end note
SVC -> PG : SELECT * FROM eventos\nWHERE id=:eventoId FOR UPDATE
activate PG
PG --> SVC : EventoEntity (fila bloqueada)
deactivate PG
SVC -> SVC : assert cuposDisponibles > 0
SVC -> PG : UPDATE eventos SET cupos_disponibles=cupos-1
activate PG
PG --> SVC : 1 row updated
deactivate PG
SVC -> PG : INSERT INTO inscripciones\n(PENDING, expira_en=NOW()+15min)
activate PG
PG --> SVC : InscripcionEntity saved
deactivate PG
SVC -> PG : INSERT INTO outbox\n(InscriptionCreated, processed_at=NULL)
activate PG
PG --> SVC : OutboxEvent saved
deactivate PG
note over SVC,PG #C8EDCB
  COMMIT — cupo+inscripcion+outbox ACID
end note
SVC --> CTRL : InscripcionDto {id, PENDING, expiraEn}
deactivate SVC
CTRL --> ALB : HTTP 201 Created
ALB --> STU : "Tenés 15 min para pagar"
deactivate CTRL

|||
== Asíncrono: OutboxRelayService (scheduler-1) ==
|||
activate RELAY
RELAY -> PG : SELECT * FROM outbox\nWHERE processed_at IS NULL\nLIMIT 10 FOR UPDATE SKIP LOCKED
activate PG
PG --> RELAY : [{InscriptionCreated, payload}]
deactivate PG
RELAY -> MQ : basicPublish(routingKey='inscripcion.created',\nmessageId=outboxId, PERSISTENT)
activate MQ
MQ --> RELAY : publisher confirm ack
deactivate MQ
RELAY -> PG : UPDATE outbox SET processed_at=NOW()
activate PG
PG --> RELAY : 1 row updated
deactivate PG
deactivate RELAY

|||
== Fan-out: 2 queues independientes ==
|||
MQ -> PAY : deliver(InscriptionCreated)\n[payment.inscripcion.created]
activate PAY
PAY -> PAY : idempotency check by messageId
PAY -> PAY : BEGIN TX → PaymentRecord(AWAITING) → COMMIT
PAY -> MQ : basicAck
deactivate PAY

MQ -> NOTIF : deliver(InscriptionCreated)\n[notification.inscripcion.created]
activate NOTIF
NOTIF -> NOTIF : idempotency check by messageId
NOTIF -> NOTIF : Strategy+TemplateMethod\n→ notif "Inscripción recibida" [WireMock SMTP]
NOTIF -> MQ : basicAck
deactivate NOTIF
@enduml
```

### Mermaid — Escenario A

```mermaid
sequenceDiagram
    actor STU as Estudiante
    participant ALB as React SPA / ALB (AWS)
    participant CTRL as InscripcionRestController
    participant SVC as CrearInscripcionUseCase<br/>@Transactional
    participant PG as PostgreSQL (inscription_db)
    participant RELAY as OutboxRelayService<br/>@Scheduled(2s)
    participant MQ as RabbitMQ<br/>inscripcion.events
    participant PAY as InscripcionCreadaListener<br/>(payment-svc)
    participant NOTIF as InscripcionCreadaListener<br/>(notif-svc)

    STU->>ALB: POST /api/v1/inscripciones {usuarioId, eventoId}
    ALB->>CTRL: forward request
    activate CTRL
    CTRL->>SVC: ejecutar(CrearInscripcionCommand)
    activate SVC
    Note over SVC,PG: BEGIN TRANSACTION
    SVC->>PG: SELECT * FROM eventos WHERE id=? FOR UPDATE (ADR-003)
    PG-->>SVC: EventoEntity (fila bloqueada)
    SVC->>SVC: assert cuposDisponibles > 0
    SVC->>PG: UPDATE eventos SET cupos_disponibles = cupos - 1
    PG-->>SVC: 1 row updated
    SVC->>PG: INSERT INTO inscripciones (PENDING, expira_en=NOW()+15min)
    PG-->>SVC: InscripcionEntity saved
    SVC->>PG: INSERT INTO outbox (InscriptionCreated, processed_at=NULL) (ADR-011)
    PG-->>SVC: OutboxEvent saved
    Note over SVC,PG: COMMIT — cupo + inscripcion + outbox ACID
    SVC-->>CTRL: InscripcionDto {id, PENDING, expiraEn}
    deactivate SVC
    CTRL-->>ALB: HTTP 201 Created
    ALB-->>STU: "Tenés 15 min para completar el pago"
    deactivate CTRL

    Note over RELAY,MQ: Asíncrono — OutboxRelayService polling cada 2 s (ADR-018)
    RELAY->>PG: SELECT * FROM outbox WHERE processed_at IS NULL LIMIT 10 FOR UPDATE SKIP LOCKED
    PG-->>RELAY: [{InscriptionCreated, payload}]
    RELAY->>MQ: basicPublish(routingKey=inscripcion.created, messageId=outboxId, PERSISTENT)
    MQ-->>RELAY: publisher confirm ack
    RELAY->>PG: UPDATE outbox SET processed_at=NOW()

    Note over MQ,NOTIF: Fan-out — RabbitMQ entrega a 2 queues independientes
    par payment-service
        MQ->>PAY: deliver(InscriptionCreated) [payment.inscripcion.created]
        activate PAY
        PAY->>PAY: idempotency check by messageId
        PAY->>PAY: BEGIN TX → PaymentRecord(AWAITING) → COMMIT
        PAY->>MQ: basicAck
        deactivate PAY
    and notification-service
        MQ->>NOTIF: deliver(InscriptionCreated) [notification.inscripcion.created]
        activate NOTIF
        NOTIF->>NOTIF: idempotency check by messageId
        NOTIF->>NOTIF: Strategy → canal EMAIL<br/>Template Method → "Inscripción recibida" [WireMock]
        NOTIF->>MQ: basicAck
        deactivate NOTIF
    end
```

---

## 3. Escenario B: Confirmación de Pago

### Descripción

1. El cliente confirma el pago vía REST. `ConfirmarPagoUseCase` llama a **WireMock** (mock de la pasarela), actualiza el estado a `CONFIRMED` e inserta `OutboxEvent{PaymentConfirmed}` en la misma transacción.
2. **⚠️ PENDIENTE:** El `OutboxRelayService` de `payment-service` debe publicar el evento a RabbitMQ (rama `feat/payment-outbox-e2e`).
3. **Fan-out:** `inscription-service` confirma la inscripción (`PENDING → CONFIRMADA`); `notification-service` envía el recibo de pago al estudiante.

### PlantUML — Escenario B
> Render local: `java -jar ~/plantuml.jar docs/diagramas/vp-escenario-b-pago.puml`

```plantuml
@startuml vp-escenario-b-pago
!theme plain
skinparam maxMessageSize 120
skinparam sequenceMessageAlign left
skinparam responseMessageBelowArrow true
skinparam defaultFontSize 10
scale 0.85

title Escenario B — Confirmación de Pago + Fan-out

participant "Cliente / ALB"                                as ALB   #EEEEEE
participant "PagoController\n(payment-svc)"                as PCTL  #FFE0B2
participant "ConfirmarPago\nUseCase @Transactional"        as PSVC  #FFE0B2
participant "WireMock\n(mock pasarela)"                    as WIRE  #E0E0E0
database    "PostgreSQL\n(payment_db)"                     as PPG   #FFF9C4
participant "OutboxRelay\n(payment-svc)\n⚠️ PENDIENTE"    as PRLY  #FFB3B3
queue       "RabbitMQ\n(pago.events)"                      as MQ    #F3E5F5
participant "PaymentConfirmed\nListener (inscription-svc)" as ILIST #BDE5FF
participant "ConfirmarInscripcion\nUseCase @Transactional" as ISVC  #C8EDCB
database    "PostgreSQL\n(inscription_db)"                 as IPG   #FFF9C4
participant "PaymentConfirmed\nListener (notif-svc)"       as NLIST #EDE7F6

ALB -> PCTL : POST /api/v1/pagos/{id}/confirmar {token}
activate PCTL
PCTL -> PSVC : confirmarPago(pagoId, token)
activate PSVC
note over PSVC,PPG #C8EDCB
  BEGIN TRANSACTION
end note
PSVC -> WIRE : POST /mock/gateway/charge {amount, token}
activate WIRE
WIRE --> PSVC : 200 {status:'approved', transactionId:'mock-txn-123'}
deactivate WIRE
PSVC -> PSVC : pago.confirmar() PENDING→CONFIRMED
PSVC -> PPG : UPDATE pagos SET estado='CONFIRMED', transaction_id=:txnId
activate PPG
PPG --> PSVC : 1 row updated
deactivate PPG
PSVC -> PPG : INSERT INTO outbox (PaymentConfirmed, processed_at=NULL)
activate PPG
PPG --> PSVC : OutboxEvent saved
deactivate PPG
note over PSVC,PPG #C8EDCB
  COMMIT — pago + outbox ACID
end note
PSVC --> PCTL : ok
deactivate PSVC
PCTL --> ALB : HTTP 200 OK {CONFIRMED}
deactivate PCTL

note over PRLY #FFB3B3
  ⚠️ PENDIENTE DE IMPLEMENTAR
  OutboxRelayService (payment-svc)
  rama: feat/payment-outbox-e2e
end note
PRLY -[#red,dashed]-> MQ : [⚠️PENDIENTE] basicPublish(\nroutingKey='pago.confirmed',\nmessageId=outboxId)

MQ -> ILIST : deliver(PaymentConfirmed)\n[inscripcion.pago.confirmado]
activate ILIST
ILIST -> ILIST : idempotency check by messageId
ILIST -> ISVC : ejecutar(inscripcionId, txnId)
activate ISVC
note over ISVC,IPG #C8EDCB
  BEGIN TRANSACTION
end note
ISVC -> IPG : SELECT * FROM inscripciones WHERE id=? FOR UPDATE
activate IPG
IPG --> ISVC : {PENDING}
deactivate IPG
ISVC -> ISVC : inscripcion.confirmar() PENDING→CONFIRMADA
ISVC -> IPG : UPDATE inscripciones SET estado='CONFIRMADA'
activate IPG
IPG --> ISVC : 1 row updated
deactivate IPG
ISVC -> IPG : INSERT INTO outbox (InscriptionConfirmed)
activate IPG
IPG --> ISVC : 1 row inserted
deactivate IPG
note over ISVC,IPG #C8EDCB
  COMMIT
end note
ISVC --> ILIST : ok
deactivate ISVC
ILIST -> MQ : basicAck
deactivate ILIST

MQ -> NLIST : deliver(PaymentConfirmed)\n[notification.pago.confirmado]
activate NLIST
NLIST -> NLIST : idempotency check by messageId
NLIST -> NLIST : Strategy→EMAIL TemplateMethod\n"Pago confirmado, inscripción activa" [WireMock]
NLIST -> MQ : basicAck
deactivate NLIST
@enduml
```

### Mermaid — Escenario B

```mermaid
sequenceDiagram
    participant ALB as Cliente / ALB (AWS)
    participant PCTL as PagoController<br/>(payment-svc)
    participant PSVC as ConfirmarPagoUseCase<br/>@Transactional
    participant WIRE as WireMock<br/>(mock pasarela)
    participant PPG as PostgreSQL<br/>(payment_db)
    participant PRLY as OutboxRelayService<br/>⚠️ PENDIENTE
    participant MQ as RabbitMQ pago.events
    participant ILIST as PaymentConfirmedListener<br/>(inscription-svc)
    participant ISVC as ConfirmarInscripcionUseCase<br/>@Transactional
    participant IPG as PostgreSQL<br/>(inscription_db)
    participant NLIST as PaymentConfirmedListener<br/>(notif-svc)

    ALB->>PCTL: POST /api/v1/pagos/{id}/confirmar {token}
    activate PCTL
    PCTL->>PSVC: confirmarPago(pagoId, token)
    activate PSVC
    Note over PSVC,PPG: BEGIN TRANSACTION
    PSVC->>WIRE: POST /mock/gateway/charge {amount, token}
    WIRE-->>PSVC: 200 {status:approved, transactionId:mock-txn-123}
    PSVC->>PSVC: pago.confirmar() PENDING→CONFIRMED
    PSVC->>PPG: UPDATE pagos SET estado=CONFIRMED, transaction_id=mock-txn-123
    PPG-->>PSVC: 1 row updated
    PSVC->>PPG: INSERT INTO outbox (PaymentConfirmed, processed_at=NULL)
    PPG-->>PSVC: OutboxEvent saved
    Note over PSVC,PPG: COMMIT — pago + outbox ACID
    PSVC-->>PCTL: ok
    deactivate PSVC
    PCTL-->>ALB: 200 OK {CONFIRMED}
    deactivate PCTL

    Note over PRLY: ⚠️ PENDIENTE: OutboxRelayService(payment-svc)<br/>rama feat/payment-outbox-e2e<br/>SELECT outbox SKIP LOCKED → basicPublish → mark processed

    Note over MQ,NLIST: Fan-out — inscription-svc + notification-svc
    par inscription-service
        MQ->>ILIST: deliver(PaymentConfirmed) [inscripcion.pago.confirmado]
        activate ILIST
        ILIST->>ILIST: idempotency check by messageId
        ILIST->>ISVC: ejecutar(inscripcionId, txnId)
        activate ISVC
        Note over ISVC,IPG: BEGIN TRANSACTION
        ISVC->>IPG: SELECT * FROM inscripciones WHERE id=? FOR UPDATE
        IPG-->>ISVC: InscripcionEntity {PENDING}
        ISVC->>ISVC: inscripcion.confirmar() → CONFIRMADA
        ISVC->>IPG: UPDATE inscripciones SET estado=CONFIRMADA
        IPG-->>ISVC: 1 row updated
        ISVC->>IPG: INSERT INTO outbox (InscriptionConfirmed)
        IPG-->>ISVC: 1 row inserted
        Note over ISVC,IPG: COMMIT
        ISVC-->>ILIST: ok
        deactivate ISVC
        ILIST->>MQ: basicAck
        deactivate ILIST
    and notification-service
        MQ->>NLIST: deliver(PaymentConfirmed) [notification.pago.confirmado]
        activate NLIST
        NLIST->>NLIST: idempotency check by messageId
        NLIST->>NLIST: Strategy→EMAIL / Template Method<br/>"Pago confirmado — inscripción activa" [WireMock]
        NLIST->>MQ: basicAck
        deactivate NLIST
    end
```

---

## 4. Escenario C: Job de Expiración de Inscripciones

### Descripción

Cada 60 s, `ExpirarInscripcionesPendientesService` se activa bajo un **lock ShedLock distribuido** en la tabla `shedlock` de PostgreSQL (**ADR-018**). Usa `SKIP LOCKED` para evitar contención entre nodos. Por cada inscripción vencida: devuelve el cupo con `SELECT FOR UPDATE` sobre el evento (**ADR-003**), marca la inscripción como `EXPIRADA` y registra un `OutboxEvent{InscriptionExpired}` (**ADR-011**) en la misma transacción.

### PlantUML — Escenario C (Activity)
> Render local: `java -jar ~/plantuml.jar docs/diagramas/vp-escenario-c-expiracion.puml`

```plantuml
@startuml vp-escenario-c-expiracion
!theme plain
skinparam defaultFontSize 10
skinparam roundcorner 6
scale 0.9

title Escenario C — Job de Expiración @Scheduled(60s) + SKIP LOCKED

start
#FFE0B2:ExpirarInscripcionesPendientesService.ejecutar()
  @Scheduled(fixedDelay=60_000 ms);
#BDE5FF:ShedLock: adquiere lock distribuido
  WHERE name='expiracion-inscripciones-job'
  AND lock_until <= NOW();
if (¿lock adquirido?) then (NO)
  #FFB3B3:log: job omitido, otro nodo activo;
  stop
else (SÍ)
endif
#C8EDCB:SELECT id, evento_id FROM inscripciones
  WHERE estado='PENDING' AND expira_en < NOW()
  LIMIT 50 FOR UPDATE SKIP LOCKED;
if (¿lote vacío?) then (SÍ)
  #BDE5FF:ShedLock libera lock;
  stop
else (NO)
endif
while (por cada inscripción del lote) is (quedan)
  #C8EDCB:BEGIN TRANSACTION;
  #FFF9C4:SELECT * FROM eventos
    WHERE id=:eventoId FOR UPDATE;
  #C8EDCB:evento.incrementarCupo()
    cupos = cupos + 1;
  #C8EDCB:UPDATE eventos SET cupos_disponibles=cupos+1;
  #C8EDCB:inscripcion.expirar() PENDING→EXPIRADA;
  #C8EDCB:UPDATE inscripciones SET estado='EXPIRADA';
  #FFE0B2:INSERT INTO outbox
    (InscriptionExpired, payload=JSON);
  if (¿COMMIT ok?) then (SÍ)
    #C8EDCB:COMMIT;
  else (NO)
    #FFB3B3:ROLLBACK; log.error;
  endif
endwhile (fin lote)
#BDE5FF:ShedLock libera lock;
note right #F3E5F5
  OutboxRelayService (2s):
  publica InscriptionExpired →
  notification-svc notifica al estudiante
end note
stop
@enduml
```

### Mermaid — Escenario C (Flowchart)

```mermaid
flowchart TD
    A([START: @Scheduled fixedDelay=60s]) --> B["ShedLock: UPDATE shedlock<br/>SET lock_until=NOW()+90s<br/>WHERE name='expiracion-inscripciones-job'<br/>AND lock_until &lt;= NOW()"]
    B --> C{¿Lock adquirido?}
    C -- NO --> D[log: job omitido,<br/>otro nodo activo]
    D --> Z([END])
    C -- SÍ --> E["SELECT id, evento_id FROM inscripciones<br/>WHERE estado='PENDING' AND expira_en &lt; NOW()<br/>LIMIT 50 FOR UPDATE SKIP LOCKED"]
    E --> F{¿Lote vacío?}
    F -- SÍ --> SL1[ShedLock libera lock]
    SL1 --> Z
    F -- NO --> LOOP[Por cada inscripción del lote]
    LOOP --> I[BEGIN TRANSACTION]
    I --> J["SELECT * FROM eventos WHERE id=:eventoId FOR UPDATE<br/>(recuperar cupo — ADR-003)"]
    J --> K["evento.incrementarCupo()<br/>cupos_disponibles = cupos + 1"]
    K --> L["UPDATE eventos SET cupos_disponibles = cupos + 1"]
    L --> M["inscripcion.expirar() PENDING → EXPIRADA"]
    M --> N["UPDATE inscripciones SET estado='EXPIRADA'"]
    N --> O["INSERT INTO outbox<br/>(InscriptionExpired, payload=JSON) — ADR-011"]
    O --> P{¿COMMIT ok?}
    P -- SÍ --> Q[COMMIT]
    P -- NO --> R[ROLLBACK + log.error<br/>reintento en próximo ciclo]
    Q --> LOOP
    R --> LOOP
    LOOP -- fin lote --> SL2[ShedLock libera lock]
    SL2 --> PUB["OutboxRelayService (2s)<br/>publica InscriptionExpired<br/>→ notification-svc notifica al estudiante:<br/>'Tu inscripción venció. El cupo fue liberado.'"]
    PUB --> Z
```

---

## 5. Tabla de Comunicación entre Procesos (IPC)

| Origen | Destino | Sincronía | Canal | Garantía de entrega | Idempotencia |
|---|---|---|---|---|---|
| React SPA (browser) | ALB → inscription-service | **Síncrona** | HTTPS/REST | At-most-once | N/A (idempotente por diseño REST) |
| React SPA (browser) | ALB → event-service | **Síncrona** | HTTPS/REST | At-most-once | N/A |
| React SPA (browser) | ALB → payment-service | **Síncrona** | HTTPS/REST | At-most-once | N/A |
| event-service | Redis 7 (ElastiCache) | **Síncrona** | RESP (Lettuce) | Best-effort (cache) | N/A — lectura |
| inscription-service | PostgreSQL (inscription_db) | **Síncrona** | JDBC/pgwire | ACID | `@Transactional` Spring |
| payment-service | PostgreSQL (payment_db) | **Síncrona** | JDBC/pgwire | ACID | `@Transactional` Spring |
| notification-service | PostgreSQL (inscription_db) | **Síncrona** | JDBC/pgwire | ACID | Check `processed_messages` por `message_id` |
| inscription-service | RabbitMQ (Amazon MQ) | **Asíncrona** | AMQP 0-9-1 | **At-least-once** (Outbox+publisher confirms) | Outbox garantiza exactamente-una-vez en publicación |
| payment-service | RabbitMQ (Amazon MQ) | **Asíncrona** | AMQP 0-9-1 | **At-least-once** ⚠️ PENDIENTE | ⚠️ Outbox pendiente |
| RabbitMQ | payment-service (consumer) | **Asíncrona** | AMQP 0-9-1 | At-least-once con ack manual | `SELECT 1 FROM processed_messages WHERE message_id=?` |
| RabbitMQ | inscription-service (consumer) | **Asíncrona** | AMQP 0-9-1 | At-least-once con ack manual | `SELECT 1 FROM processed_messages WHERE message_id=?` |
| RabbitMQ | notification-service (consumer) | **Asíncrona** | AMQP 0-9-1 | At-least-once con ack manual | `INSERT INTO processed_messages ... ON CONFLICT DO NOTHING` |
| payment-service | WireMock (mock pasarela) | **Síncrona** | HTTP | At-most-once (mock local) | `x-idempotency-key` en header |
| notification-service | WireMock SMTP (mock email) | **Síncrona** | HTTP | At-most-once (mock local) | messageId check antes de llamar |
| ShedLock heartbeat | PostgreSQL (`shedlock` table) | **Síncrona** | JDBC | ACID | `UPDATE ... WHERE lock_until <= NOW()` atómica |

---

## 6. Modelo de Concurrencia por Microservicio

### 6.1 inscription-service

| Mecanismo | Implementación | Propósito |
|---|---|---|
| **Pessimistic Row Lock** | `SELECT * FROM eventos WHERE id=? FOR UPDATE` en `EventoJpaRepository.buscarPorIdConBloqueoPesimista()` | Serializar el acceso a `cupos_disponibles`; impide sobrecupo bajo concurrencia alta |
| **SKIP LOCKED** | En poll de `outbox` y en `SELECT` del job de expiración | Permite múltiples instancias sin contención: cada nodo procesa filas no bloqueadas |
| **ShedLock** | `@SchedulerLock(name="expiracion-inscripciones-job", lockAtMostFor="90s")` | Garantiza que el job de expiración se ejecute en exactamente un nodo a la vez |
| **Transactional Outbox** | `OutboxRelayService` + tabla `outbox` | Atomicidad entre persistencia del dominio y publicación al broker |
| **Idempotencia AMQP** | Check `message_id` en tabla `processed_messages` antes de procesar | Tolerar re-delivery de RabbitMQ sin efectos secundarios |
| **Pool Tomcat** | 200 workers (default Spring Boot) | Paralelismo en requests HTTP sin estado compartido |
| **HikariCP** | 10 conexiones por instancia (default) | Límite de conexiones simultáneas a PostgreSQL |

### 6.2 payment-service

| Mecanismo | Implementación | Propósito |
|---|---|---|
| **Idempotencia webhook** | `x-idempotency-key` + check en tabla `pagos` | Tolerar reintentos del cliente sin procesar el pago dos veces |
| **@Transactional** | Spring `@Transactional(rollbackFor=Exception.class)` | Atomicidad entre actualización del pago y registro en `outbox` |
| **WireMock timeout** | `RestTemplate`/`WebClient` con `connectTimeout=3s`, `readTimeout=5s` | Evitar threads colgados ante demora del mock |
| **Idempotencia AMQP** | Check `message_id` al consumir `InscriptionCreated` | Tolerar re-delivery |
| **OutboxRelayService** ⚠️ | **PENDIENTE** — rama `feat/payment-outbox-e2e` | Garantizar publicación de `PaymentConfirmed` incluso si el servicio cae tras COMMIT |

### 6.3 event-service

| Mecanismo | Implementación | Propósito |
|---|---|---|
| **Cache-Aside** | `@Cacheable(value="eventos")` + Redis 7 (ElastiCache) | Reducir latencia y carga en PostgreSQL para catálogo público (read-heavy) |
| **@CacheEvict** | Ante modificación de evento | Mantener consistencia caché-BD |
| **Lettuce async** | Cliente Redis no bloqueante (Lettuce) | Cache GET/SET no bloquea el thread HTTP worker |
| **Lecturas optimistas** | Sin locking en consultas de catálogo | El catálogo es read-heavy; actualizaciones son infrecuentes y coordinadas |

### 6.4 notification-service

| Mecanismo | Implementación | Propósito |
|---|---|---|
| **Concurrent consumers** | `concurrency="1-5"` en `@RabbitListener` container | Paralelismo de procesamiento de notificaciones por tipo de evento |
| **Idempotencia** | `INSERT INTO processed_messages ... ON CONFLICT DO NOTHING` | Tolerar re-delivery de cualquier evento |
| **Strategy Pattern** | `NotificationStrategy` por canal: `EmailStrategy`, `SmsStrategy`, `PushStrategy` | Seleccionar canal en runtime sin condicionales |
| **Template Method** | `AbstractNotificationHandler.handle()` | Estructura común: validar → construir → enviar → registrar |
| **Circuit Breaker (futuro)** | Resilience4j sobre llamada a WireMock SMTP | Tolerar indisponibilidad del canal externo |

---

## 7. Tabla de Manejo de Fallos en Runtime

| Fallo | Detección | Mitigación |
|---|---|---|
| **Caída de RabbitMQ** | `AmqpException` / `ConnectException` en `OutboxRelayService.publish()` | El `OutboxEvent` permanece en PostgreSQL (`processed_at=NULL`). El relay reintenta en próximo poll (2 s). Sin pérdida de mensajes mientras la BD esté disponible. |
| **Caída de un microservicio** | HTTP 503 desde ALB / falla de health check | Mensajes AMQP se acumulan en la cola (persistente). Al reiniciar, el consumer retoma desde el primer mensaje no `ack`-eado. Idempotencia evita duplicados. |
| **Mensaje envenenado → DLQ** | `x-death` count supera `maxRetries` (configurable, default 3) | RabbitMQ mueve el mensaje a `<queue>.dlq`. Requiere revisión manual o proceso de remediation automatizado. |
| **Deadlock en PostgreSQL** | `PSQLException` código `40001` (serialization failure) | Spring `@Transactional` hace rollback automático. El cliente recibe HTTP 409 o reintenta. `SELECT FOR UPDATE` serializa el acceso y reduce la probabilidad de deadlock entre inscripciones. |
| **Mensaje AMQP duplicado (reentrega)** | Mismo `messageId` recibido dos veces | Check `SELECT 1 FROM processed_messages WHERE message_id=?` antes de procesar. Si existe: `basicAck` sin reprocesar. |
| **Lock ShedLock no liberado (nodo muerto)** | `lock_until` en el pasado detectado por el siguiente nodo activo | `lock_until` expira automáticamente en 90 s. El siguiente poll de cualquier nodo adquiere el lock y reanuda el job. |
| **Timeout de WireMock (pasarela mock)** | `ConnectTimeoutException` / `ReadTimeoutException` en `ConfirmarPagoUseCase` | `@Transactional` hace rollback. El cliente recibe HTTP 502 Bad Gateway. La BD permanece consistente (sin cambios). El cliente puede reintentar (con `x-idempotency-key`). |
| **OutboxRelayService caído (eventos acumulados)** | Tabla `outbox` crece con registros `processed_at=NULL` | Al reiniciar, el relay procesa el backlog ordenado por `created_at ASC`. Publisher confirms garantizan que cada publicación sea exitosa antes de marcar procesado. No hay pérdida de eventos. |

---

## 8. Mapeo a Requerimientos No Funcionales (RNF)

| RNF | Cómo lo soporta esta Vista de Procesos |
|---|---|
| **Consistencia eventual entre servicios** | Transactional Outbox (ADR-011): la BD garantiza la existencia del evento; RabbitMQ garantiza la entrega. La consistencia entre servicios se alcanza en segundos (poll ≤ 2 s). |
| **Cero sobrecupo bajo concurrencia** | `SELECT ... FOR UPDATE` sobre la fila del evento (ADR-003) serializa el acceso. Solo una transacción a la vez puede decrementar `cupos_disponibles`. Validación de cupo > 0 dentro del mismo TX. |
| **Liberación automática de cupos vencidos** | Job `@Scheduled(60s)` con ShedLock (ADR-018) detecta inscripciones `PENDING` con `expira_en < NOW()` y ejecuta el rollback de cupo transaccionalmente (ADR-003). |
| **Resiliencia ante caídas del broker** | Outbox persiste mensajes en PostgreSQL antes de publicar. Si RabbitMQ cae, los mensajes esperan en la BD y se entregan al volver. DLQs capturan mensajes envenenados. |
| **Escalabilidad horizontal stateless** | HTTP workers sin estado compartido. `SKIP LOCKED` evita hot-rows en acceso concurrente al `outbox`. ShedLock garantiza que los jobs no se dupliquen entre instancias. |
| **Extensibilidad (nuevos consumidores sin tocar productores)** | Pub-Sub fan-out en RabbitMQ: agregar un nuevo consumidor implica solo crear una nueva queue y binding en el exchange existente. Los productores (`OutboxRelayService`) no cambian. Ejemplo: `certificate-service` puede suscribirse a `InscriptionConfirmed` sin modificar `inscription-service`. |

---

## 9. Referencia a ADRs

| ADR | Mecanismo runtime | Dónde se materializa |
|---|---|---|
| **ADR-003** — Pessimistic Locking | `SELECT * FROM eventos WHERE id=? FOR UPDATE` | `EventoJpaRepository.buscarPorIdConBloqueoPesimista()` en inscription-service · Job de expiración (devolución de cupo) |
| **ADR-008** — Cache-Aside con Redis | `@Cacheable` + Redis 7 (ElastiCache) | event-service: catálogo de eventos públicos · Fallback automático a PostgreSQL ante fallo de Redis |
| **ADR-011** — Transactional Outbox Pattern | Tabla `outbox` + `OutboxRelayService` + publisher confirms | inscription-service (completo) · payment-service ⚠️ PENDIENTE rama `feat/payment-outbox-e2e` |
| **ADR-012** — Arquitectura Hexagonal | Puertos (`*Repository`, `EventPublisher`) + Adaptadores JPA/AMQP/REST | Los 4 microservicios: domain.port / infrastructure.persistence / infrastructure.messaging / infrastructure.rest |
| **ADR-018** — ShedLock para jobs distribuidos | `@SchedulerLock` + tabla `shedlock` en PostgreSQL | inscription-service: `ExpirarInscripcionesPendientesService` + `OutboxRelayService` |

---

## Apéndice: Archivos generados

| Archivo | Tipo | Herramienta de render |
|---|---|---|
| `docs/diagramas/vp-escenario-a-fanout.puml` | Sequence con fan-out | `java -jar plantuml.jar` |
| `docs/diagramas/vp-escenario-b-pago.puml` | Sequence con ⚠️ pendiente | `java -jar plantuml.jar` |
| `docs/diagramas/vp-escenario-c-expiracion.puml` | Activity con SKIP LOCKED | `java -jar plantuml.jar` |

**Render todos de una vez:**
```bash
java -jar ~/plantuml.jar docs/diagramas/vp-escenario-*.puml
```

**Mermaid:** pegar bloques en [mermaid.live](https://mermaid.live) o preview de GitHub — sin límite de tamaño.
