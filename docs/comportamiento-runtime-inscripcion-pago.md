# Comportamiento en Tiempo de Ejecución: Flujo de Inscripción y Pago
## Plataforma de Gestión de Eventos Académicos — Pontificia Universidad Javeriana

**Versión:** 1.0  
**Fecha:** 2026-04-04  
**Autoras:** Tannia Hernández Rojas  
**Curso:** Diseño de Software Basado en Patrones

---

## 1. Propósito

Este documento describe el comportamiento dinámico del sistema durante el flujo de inscripción y pago: cómo los componentes se comunican, en qué orden, con qué garantías de consistencia, y cómo se manejan los escenarios de fallo. Complementa las vistas estáticas del SAD v1.0 respondiendo explícitamente al feedback del evaluador: *"el tratamiento de los escenarios de fallo (timeout, webhook tardío, carga concurrente) es superficial o inexistente."*

---

## 2. Participantes (componentes involucrados)

| Componente | Responsabilidad en este flujo |
|---|---|
| **Usuario (Browser/App)** | Inicia inscripción, elige tarifa, completa pago en pasarela |
| **API Gateway** | Enruta, autentica token JWT, aplica rate limiting |
| **Inscription Service** | Orquesta el flujo; reserva cupo; crea inscripción y pago |
| **Event Service** | Gestiona cupo_disponible del evento |
| **PostgreSQL** | Persistencia con bloqueo pesimista (SELECT FOR UPDATE) |
| **Pasarela de Pago** (MercadoPago) | Procesa la transacción; envía webhooks de confirmación |
| **Notification Service** | Envía correo de confirmación (asíncrono) |
| **Certificate Service** | Genera PDF de certificado (asíncrono, post-evento) |
| **Redis Cache** | Cachea catálogo; no participa en la escritura del flujo de pago |
| **Message Queue** (RabbitMQ/SQS) | Cola entre Inscription Service y servicios asíncronos |
| **Job Scheduler** | Proceso batch que revisa inscripciones expiradas cada minuto |

---

## 3. Flujo Principal: Inscripción y Pago exitoso

### 3.1 Diagrama de secuencia — camino feliz

```
Usuario          API Gateway      Inscription Svc     PostgreSQL       Pasarela Pago    Message Queue   Notification Svc
  │                   │                  │                  │                │                 │                 │
  │── POST /inscribir ──►               │                  │                │                 │                 │
  │  {evento_id, tarifa_id,             │                  │                │                 │                 │
  │   idempotency_key}                  │                  │                │                 │                 │
  │                   │── validar JWT ──►                  │                │                 │                 │
  │                   │── enrutar ───────►                 │                │                 │                 │
  │                   │                  │                  │                │                 │                 │
  │                   │                  │── BEGIN TRANSACTION               │                 │                 │
  │                   │                  │── SELECT * FROM evento             │                 │                 │
  │                   │                  │   WHERE id = ? FOR UPDATE ──►     │                 │                 │
  │                   │                  │                  │◄── (fila bloqueada)              │                 │
  │                   │                  │                  │                │                 │                 │
  │                   │                  │── verificar cupo_disponible > 0   │                 │                 │
  │                   │                  │── INSERT INTO inscripcion         │                 │                 │
  │                   │                  │   {estado: PENDIENTE_PAGO,        │                 │                 │
  │                   │                  │    fecha_expiracion: NOW()+15min} ──►               │                 │
  │                   │                  │── UPDATE evento SET               │                 │                 │
  │                   │                  │   cupo_disponible = cupo_disponible - 1 ──►         │                 │
  │                   │                  │── INSERT INTO pago                │                 │                 │
  │                   │                  │   {estado: INICIADO} ──────────────►               │                 │
  │                   │                  │── COMMIT ──────────────────────────►               │                 │
  │                   │                  │                  │                │                 │                 │
  │                   │                  │── crear sesión de pago en pasarela ──►              │                 │
  │                   │                  │                  │◄── {checkout_url, preference_id} │                 │
  │                   │                  │                  │                │                 │                 │
  │◄── 201 Created ───────────────────── │                  │                │                 │                 │
  │  {inscripcion_id,                    │                  │                │                 │                 │
  │   checkout_url,                      │                  │                │                 │                 │
  │   expira_en: "15:00"}               │                  │                │                 │                 │
  │                   │                  │                  │                │                 │                 │
  │── [redirige a checkout_url] ──────────────────────────────────────────────────────────────────────────────►
  │                   │                  │                  │                │                 │                 │
  │   [usuario completa pago en pasarela]                   │                │                 │                 │
  │                   │                  │                  │                │                 │                 │
  │                   │                  │◄── POST /webhook/pago ────────────── (pasarela envía confirmación)
  │                   │                  │   {payment_id, status: "approved",  │                │                 │
  │                   │                  │    external_reference: inscripcion_id}               │                 │
  │                   │                  │                  │                │                 │                 │
  │                   │                  │── verificar idempotencia:         │                 │                 │
  │                   │                  │   SELECT FROM pago WHERE          │                 │                 │
  │                   │                  │   referencia_externa = payment_id ──►               │                 │
  │                   │                  │                  │◄── (no existe → primera vez)     │                 │
  │                   │                  │                  │                │                 │                 │
  │                   │                  │── BEGIN TRANSACTION               │                 │                 │
  │                   │                  │── UPDATE pago SET                 │                 │                 │
  │                   │                  │   estado=CONFIRMADO,              │                 │                 │
  │                   │                  │   referencia_externa=payment_id,  │                 │                 │
  │                   │                  │   metadatos_pasarela=payload ──────►                │                 │
  │                   │                  │── UPDATE inscripcion SET          │                 │                 │
  │                   │                  │   estado=CONFIRMADA,              │                 │                 │
  │                   │                  │   codigo_qr=generar_token() ───────►                │                 │
  │                   │                  │── INSERT INTO outbox_events        │                 │                 │
  │                   │                  │   {type: "INSCRIPCION_CONFIRMADA"} ──►              │                 │
  │                   │                  │── COMMIT ──────────────────────────►               │                 │
  │                   │                  │                  │                │                 │                 │
  │                   │                  │◄── HTTP 200 OK ──────────────────── (ACK a pasarela)
  │                   │                  │                  │                │                 │                 │
  │                   │                  │── [Outbox Relay publica evento] ──────────────────►│                 │
  │                   │                  │                  │                │                 │◄── INSCRIPCION_CONFIRMADA
  │                   │                  │                  │                │                 │── enviar email confirmación ──►
  │                   │                  │                  │                │                 │   con código QR             │
```

### 3.2 Pasos detallados del camino feliz

**Paso 1 — Inicio de inscripción (T=0):**
1. El usuario envía `POST /api/v1/inscripciones` con `{evento_id, tarifa_id, idempotency_key}`.
2. El API Gateway valida el JWT (OAuth token de Google). Si inválido → HTTP 401.
3. El API Gateway chequea rate limiting: máximo 5 intentos de inscripción por usuario por minuto. Si excede → HTTP 429.
4. El Inscription Service recibe la petición y verifica el `idempotency_key` en la BD. Si ya existe una inscripción con esa clave → retorna la inscripción existente (HTTP 200, sin duplicar).

**Paso 2 — Reserva de cupo con bloqueo pesimista (T=0 + ~10ms):**
```sql
BEGIN;
SELECT id, cupo_disponible, estado
FROM evento
WHERE id = $1
FOR UPDATE;                          -- Bloquea la fila hasta COMMIT/ROLLBACK

-- Si cupo_disponible = 0 → ROLLBACK, HTTP 409 Conflict ("sin cupos disponibles")
-- Si estado != PUBLICADO → ROLLBACK, HTTP 422

INSERT INTO inscripcion (
    id, usuario_id, evento_id, tarifa_id,
    estado, fecha_inscripcion,
    fecha_expiracion_pago,             -- NOW() + INTERVAL '15 minutes'
    idempotency_key
) VALUES ($1, $2, $3, $4, 'PENDIENTE_PAGO', NOW(), NOW() + '15 min', $5);

UPDATE evento
SET cupo_disponible = cupo_disponible - 1
WHERE id = $1;

INSERT INTO pago (
    id, inscripcion_id, monto, moneda, pasarela, estado
) VALUES (gen_random_uuid(), $inscripcion_id, $monto, 'COP', 'MERCADOPAGO', 'INICIADO');

COMMIT;
```

**Paso 3 — Creación de preferencia en MercadoPago (T=0 + ~200ms):**
- Inscription Service llama a la API de MercadoPago para crear una `preference`.
- MercadoPago retorna `{checkout_url, preference_id}`.
- Inscription Service retorna al usuario HTTP 201 con `{inscripcion_id, checkout_url, expira_en: 900}` (900 segundos).

**Paso 4 — Procesamiento del webhook (T=0 + X minutos, cuando el usuario paga):**
- MercadoPago llama a `POST /api/v1/webhooks/pagos` con el resultado del pago.
- Inscription Service verifica idempotencia: `SELECT FROM pago WHERE referencia_externa = $payment_id`. Si ya existe → HTTP 200 (ya procesado).
- Si no existe: transacción atómica actualiza pago → `CONFIRMADO`, inscripción → `CONFIRMADA`, genera `codigo_qr`, guarda payload del webhook en `metadatos_pasarela`, inserta en `outbox_events`.
- Retorna HTTP 200 a MercadoPago (ACK dentro de 5 segundos para evitar reintento).

**Paso 5 — Notificación asíncrona (T+5s aprox.):**
- El Outbox Relay (proceso separado) lee los eventos de `outbox_events` y publica en la cola de mensajes.
- Notification Service consume el evento `INSCRIPCION_CONFIRMADA` y envía el correo con el código QR al usuario.
- Si el correo falla: el mensaje pasa a DLQ después de 3 reintentos. El operador puede reintentar manualmente desde DLQ.

---

## 4. Flujo de Timeout (inscripción expirada)

### 4.1 Escenario

El usuario inicia la inscripción pero **no completa el pago en 15 minutos**.

```
T=0       Usuario inicia inscripción → estado: PENDIENTE_PAGO, cupo descontado
T=1min    Job scheduler corre: consulta inscripciones PENDIENTE_PAGO con fecha_expiracion < NOW()
          → ninguna expirada aún
T=15min   Job scheduler corre:
          → inscripcion.id = X tiene fecha_expiracion = T+15 < NOW()
          → BEGIN TRANSACTION
             UPDATE inscripcion SET estado = 'EXPIRADA' WHERE id = X;
             UPDATE evento SET cupo_disponible = cupo_disponible + 1 WHERE id = evento_id;
          → COMMIT
T=15min+  Si llega webhook de pago tardío:
          → Inscription Service detecta inscripcion.estado = EXPIRADA
          → No confirma inscripción
          → Llama a MercadoPago API para emitir reembolso automático
          → Actualiza pago → estado: REEMBOLSADO
          → Retorna HTTP 200 a MercadoPago (ACK)
          → Notification Service envía correo "tu pago fue reembolsado"
```

### 4.2 Diagrama de secuencia — timeout con webhook tardío

```
Job Scheduler      PostgreSQL       Inscription Svc      Pasarela Pago    Notification Svc
     │                  │                  │                    │                 │
     │ [cada 1 min]     │                  │                    │                 │
     │── SELECT inscripciones WHERE        │                    │                 │
     │   estado='PENDIENTE_PAGO' AND       │                    │                 │
     │   fecha_expiracion < NOW() ─────────►                    │                 │
     │                  │◄── [lista de expiradas]               │                 │
     │                  │                  │                    │                 │
     │   [por cada inscripción expirada]   │                    │                 │
     │── BEGIN TRANSACTION ────────────────►                    │                 │
     │── UPDATE inscripcion SET estado=EXPIRADA ────────────────►                 │
     │── UPDATE evento SET cupo_disponible+1 ───────────────────►                 │
     │── COMMIT ───────────────────────────►                    │                 │
     │                  │                  │                    │                 │
     │                  │                  │◄── POST /webhook/pago (tardío, de MercadoPago)
     │                  │                  │   {status: "approved", payment_id}   │
     │                  │                  │                    │                 │
     │                  │                  │── SELECT inscripcion WHERE id = ? ───►
     │                  │                  │                  estado = EXPIRADA   │
     │                  │                  │── CALL MercadoPago refund API ───────►
     │                  │                  │                    │◄── {refund_id}   │
     │                  │                  │── UPDATE pago SET estado=REEMBOLSADO ►
     │                  │                  │── INSERT outbox_events PAGO_REEMBOLSADO
     │                  │                  │── HTTP 200 OK ───────────────────────►
     │                  │                  │                    │                 │
     │                  │                  │                    │◄── [Outbox Relay]
     │                  │                  │                    │── enviar email reembolso ──►
```

### 4.3 Garantías del mecanismo de timeout

- **Atomicidad:** La expiración y la liberación de cupo son una sola transacción. No puede haber estado inconsistente (inscripción expirada pero cupo no liberado).
- **Idempotencia del job:** Si el job corre dos veces en el mismo minuto (por crash y restart), el `WHERE estado='PENDIENTE_PAGO'` previene actualizar inscripciones ya expiradas.
- **Ordering:** El webhook tardío siempre verifica el estado actual de la inscripción antes de confirmar. Si el estado es `EXPIRADA`, el webhook nunca confirma la inscripción aunque el pago haya sido exitoso en la pasarela.

---

## 5. Flujo de Pago Duplicado (idempotencia de webhooks)

### 5.1 Escenario

MercadoPago envía el mismo webhook de confirmación **dos o más veces** (comportamiento documentado de la pasarela; ocurre en redes inestables o por política de retry de la pasarela).

```
T=0    Webhook 1 llega: {payment_id: "MP-123", status: "approved"}
       → SELECT FROM pago WHERE referencia_externa = 'MP-123' → vacío
       → Procesa normalmente: COMMIT, pago → CONFIRMADO, inscripcion → CONFIRMADA
       → HTTP 200 a MercadoPago

T=30s  Webhook 2 llega: {payment_id: "MP-123", status: "approved"} (reintento)
       → SELECT FROM pago WHERE referencia_externa = 'MP-123'
       → Existe → estado = CONFIRMADO
       → No hacer nada (idempotent return)
       → HTTP 200 a MercadoPago

T=30s  No hay doble cargo, no hay doble inscripción.
```

### 5.2 Implementación del check de idempotencia

```java
// En WebhookController (Inscription Service)
@PostMapping("/webhooks/pagos")
@Transactional
public ResponseEntity<Void> procesarWebhook(@RequestBody WebhookPayload payload) {
    
    // 1. Verificar idempotencia
    Optional<Pago> pagoExistente = pagoRepository
        .findByReferenciaExterna(payload.getPaymentId());
    
    if (pagoExistente.isPresent() && 
        pagoExistente.get().getEstado() == EstadoPago.CONFIRMADO) {
        // Ya procesado: ACK sin reprocessing
        log.info("Webhook duplicado recibido para payment_id={}", payload.getPaymentId());
        return ResponseEntity.ok().build();
    }
    
    // 2. Verificar estado de inscripción
    Inscripcion inscripcion = inscripcionRepository
        .findById(payload.getExternalReference())
        .orElseThrow(() -> new InscripcionNotFoundException(...));
    
    if (inscripcion.getEstado() == EstadoInscripcion.EXPIRADA) {
        // Pago tardío: emitir reembolso
        pasarelaService.emitirReembolso(payload.getPaymentId());
        pagoRepository.actualizarEstado(inscripcion.getId(), EstadoPago.REEMBOLSADO);
        outboxRepository.save(new OutboxEvent("PAGO_REEMBOLSADO", inscripcion.getId()));
        return ResponseEntity.ok().build();
    }
    
    // 3. Camino feliz: confirmar pago e inscripción
    pagoRepository.confirmar(inscripcion.getId(), payload.getPaymentId(), payload.toString());
    inscripcionRepository.confirmar(inscripcion.getId(), generarCodigoQr(inscripcion));
    outboxRepository.save(new OutboxEvent("INSCRIPCION_CONFIRMADA", inscripcion.getId()));
    
    return ResponseEntity.ok().build();
}
```

---

## 6. Flujo de Concurrencia: Último Cupo

### 6.1 Escenario

Dos usuarios (A y B) intentan inscribirse **simultáneamente** al mismo evento que tiene **exactamente 1 cupo disponible**.

```
Thread A                                    Thread B
────────────────────────────────────────────────────────
BEGIN;                                      BEGIN;
SELECT * FROM evento                        SELECT * FROM evento
WHERE id = 'X' FOR UPDATE;                 WHERE id = 'X' FOR UPDATE;
                                            ← BLOQUEADO (esperando Thread A)
-- cupo_disponible = 1 → OK
INSERT INTO inscripcion ... PENDIENTE_PAGO;
UPDATE evento SET cupo_disponible = 0;
COMMIT;  ← cupo = 0 ahora
                                            ← SE DESBLOQUEA (obtiene fila)
                                            -- cupo_disponible = 0 → ROLLBACK
                                            HTTP 409 Conflict: "sin cupos disponibles"
```

### 6.2 Por qué bloqueo pesimista y no optimista

**Bloqueo optimista** (`version` field + retry):
- Thread A y B leen `version = 5, cupo = 1`.
- A hace COMMIT con `WHERE version = 5` → OK, version pasa a 6.
- B hace UPDATE con `WHERE version = 5` → falla (0 rows updated).
- B debe hacer retry → leer de nuevo → ver cupo = 0 → retornar 409.
- Problema: el retry introduce latencia adicional y complejidad de UX (el usuario ve "intenta de nuevo").

**Bloqueo pesimista** (`SELECT FOR UPDATE`):
- El segundo thread espera hasta que el primero libere el lock.
- Cuando lo obtiene, ya ve el estado actualizado (cupo = 0) y retorna 409 inmediatamente.
- Sin retry, sin UX ambigua.
- Costo: mayor tiempo de espera en contención alta, pero aceptable porque el bloqueo se libera en < 50ms (INSERT + UPDATE son rápidos).

**Decisión (ADR-012):** Para eventos de alta demanda (lanzamiento simultáneo), el bloqueo pesimista da garantías más simples y predecibles. En carga normal (< 200 usuarios concurrentes según RNF-03), el wait time es imperceptible.

---

## 7. Flujo del Outbox Pattern (consistencia eventual con notificaciones)

### 7.1 Problema resuelto

¿Cómo garantizar que la notificación de confirmación se envíe **exactamente una vez**, incluso si el servidor se cae justo después de confirmar el pago?

**Sin Outbox (problema):**
```
COMMIT pago confirmado → servidor se cae AQUÍ → notificación nunca enviada
```

**Con Outbox (solución):**
```
COMMIT {pago confirmado + outbox_event} → servidor se cae
→ Al reiniciar, Outbox Relay lee outbox_events pendientes y publica en cola
→ Notification Service procesa y envía email
```

### 7.2 Estructura de outbox_events

```sql
CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id  UUID NOT NULL,          -- inscripcion_id o pago_id
    event_type    VARCHAR(100) NOT NULL,  -- INSCRIPCION_CONFIRMADA, PAGO_REEMBOLSADO, etc.
    payload       JSONB NOT NULL,         -- datos del evento
    published     BOOLEAN DEFAULT false,
    created_at    TIMESTAMP DEFAULT NOW(),
    published_at  TIMESTAMP
);
```

### 7.3 Outbox Relay (proceso separado)

```
Cada 5 segundos:
1. SELECT * FROM outbox_events WHERE published = false ORDER BY created_at LIMIT 100;
2. Para cada evento:
   a. Publicar en cola de mensajes (RabbitMQ/SQS)
   b. Si ACK de la cola → UPDATE outbox_events SET published=true, published_at=NOW()
3. Si la cola está caída → los eventos quedan en outbox_events (no se pierden)
4. Al recuperarse la cola → el relay los publica automáticamente
```

---

## 8. Manejo de Fallos del Circuit Breaker

### 8.1 Escenario: MercadoPago no responde al crear la preferencia

```
Inscription Service ──► MercadoPago API
                              │
                              X  (timeout 5s)

Circuit Breaker State Machine:
CLOSED → [5 fallos en 10s] → OPEN → [30s cooldown] → HALF_OPEN → [1 prueba OK] → CLOSED
```

**Comportamiento en estado OPEN:**
- Inscription Service retorna inmediatamente HTTP 503 con body:
  ```json
  {
    "error": "payment_service_unavailable",
    "message": "El servicio de pagos no está disponible temporalmente. Tu inscripción fue reservada por 15 minutos. Intenta completar el pago en /api/v1/inscripciones/{id}/reintentar-pago",
    "retry_after": 30
  }
  ```
- La inscripción queda en estado `PENDIENTE_PAGO` (cupo reservado por 15 min).
- El usuario puede reintentar cuando el circuit breaker vuelva a CLOSED.

### 8.2 Configuración Resilience4j (Inscription Service)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      mercadopago:
        sliding-window-size: 10
        failure-rate-threshold: 50        # OPEN si 50% de 10 llamadas fallan
        wait-duration-in-open-state: 30s
        permitted-calls-in-half-open-state: 3
        record-exceptions:
          - java.net.ConnectException
          - java.util.concurrent.TimeoutException
          - feign.RetryableException
  timelimiter:
    instances:
      mercadopago:
        timeout-duration: 5s              # Máximo 5s esperando a MercadoPago
```

---

## 9. Reconciliación de Estado (escenario de recuperación)

### 9.1 Escenario

El servidor se cae **justo después** de que MercadoPago confirma el pago pero **antes** de que el webhook sea procesado.

```
T=0    MercadoPago envía webhook → HTTP request llega al servidor
T=1    Servidor cae (OOM, deploy, etc.) → HTTP request abortado
T=2    MercadoPago espera ACK por 30s, no lo recibe → reintento en T+30s
T=32   Servidor se recupera
T=32   MercadoPago envía webhook nuevamente
T=32   Inscription Service procesa normalmente (idempotencia por referencia_externa)
```

**Resultado:** Gracias al retry automático de MercadoPago y la idempotencia del webhook, la inscripción se confirma correctamente sin intervención manual.

### 9.2 Job de reconciliación (operación manual de contingencia)

Para escenarios más complejos (desfase entre estado en BD y estado en MercadoPago), existe un endpoint de operaciones:

```
POST /api/v1/admin/reconciliar-pagos
Body: { "desde": "2026-04-04T00:00:00Z", "hasta": "2026-04-04T23:59:59Z" }

Acción:
1. Busca inscripciones en PENDIENTE_PAGO cuya fecha_expiracion aún no pasó
2. Para cada una, consulta la API de MercadoPago por el estado del pago
3. Si MercadoPago reporta "approved" → confirmar inscripción
4. Si MercadoPago reporta "rejected" → expirar inscripción y liberar cupo
5. Genera reporte de reconciliación para auditoría
```

---

## 10. Resumen de garantías del sistema

| Escenario | Mecanismo | Garantía |
|---|---|---|
| Dos usuarios intentan el último cupo simultáneamente | `SELECT FOR UPDATE` (ADR-012) | Exactamente uno obtiene el cupo; el otro recibe 409 |
| Usuario no paga en 15 minutos | Job scheduler + transacción atómica | Cupo liberado; inscripción expirada |
| Webhook de confirmación llega dos veces | Check de `referencia_externa` UNIQUE (ADR-008) | Pago confirmado una sola vez; sin doble inscripción |
| Webhook llega después de que expiró | Verificación de estado antes de confirmar | Pago rechazado + reembolso automático |
| Servidor cae antes de ACK al webhook | Retry automático de MercadoPago + idempotencia | Procesamiento exitoso en el reintento |
| Notification Service caído | Outbox Pattern + cola de mensajes (ADR-008) | Notificación enviada cuando el servicio se recupera |
| MercadoPago no disponible | Circuit Breaker Resilience4j (ADR-009) | Fail-fast; usuario informado; cupo reservado por 15 min |
| Correo de notificación falla 3 veces | DLQ (ADR-019) | Mensaje preservado; operador puede reintentar |
| Desfase estado BD vs. pasarela | Job de reconciliación (operación manual) | Corrección con trazabilidad de auditoría |

> **Nota (ADR-008 e idempotencia):** La unicidad de `referencia_externa` materializa la garantía de
> idempotencia que ADR-008 (Outbox Pattern) presupone en sus consumers. Tratamos ambas como un combo
> unificado siguiendo la convención del SAD original. Ver `docs/adrs/ADR-018-distributed-locking-outbox-relay.md`
> para el complemento de coordinación distribuida en multi-instancia.
