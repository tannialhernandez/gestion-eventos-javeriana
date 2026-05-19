# Trazabilidad Drivers Arquitectónicos → Decisiones → Validación
## Plataforma de Gestión de Eventos Académicos — Pontificia Universidad Javeriana

**Versión:** 1.0 (Entrega 3)  
**Fecha:** 2026-05-18  
**Autora:** Tannia Hernández Rojas  
**Curso:** Diseño de Software Basado en Patrones

---

## 1. Propósito

Este documento materializa la cadena:

> **Necesidad de negocio → Requisito → Driver arquitectónico → Decisión (ADR) → Componente → Validación**

Responde directamente al feedback del profesor sobre la Entrega 2:
*"La arquitectura parece avanzar más rápido que la especificación, generando tensiones metodológicas"* y *"Los mecanismos de concurrencia y escalabilidad quedaron enunciados pero no suficientemente claros en términos operativos"*.

**Cómo leer este documento:**
1. La sección 2 establece los 4 drivers que dominan TODAS las decisiones del proyecto.
2. La sección 3 es la tabla maestra: una fila por RF, con la cadena completa desde la necesidad hasta la validación.
3. Las secciones 4-6 profundizan los puntos que el profesor marcó como insuficientes.

> **Nota sobre numeración de ADRs:** Los ADRs en este documento siguen la numeración del índice `docs/adrs/README.md` (3 dígitos: ADR-001..ADR-019). El archivo `docs/matriz-trazabilidad.md` usa numeración heredada (2 dígitos: ADR-01..ADR-20) con algunas asignaciones diferentes. La reconciliación de ambos esquemas está documentada en `docs/srs-sad-gap-analysis.md` y se resolverá en Prompt 8.

---

## 2. Drivers Arquitectónicos

Un driver arquitectónico es un requisito que tiene impacto desproporcionado sobre las decisiones de diseño: si cambia, cambia la arquitectura. Este proyecto tiene cuatro.

---

### DA-01 — Concurrencia masiva en inscripciones

| Atributo | Valor |
|---|---|
| **Origen documental** | RNF-03 (tiempo inscripción <2s p95 bajo 200 concurrentes), RNF-02 (disponibilidad ≥99.5%), RN-01 (cupo_disponible nunca negativo) |
| **Métrica concreta** | Inscripción confirmada en <2s p95 con 200 usuarios simultáneos por el mismo evento; tasa de error <1% |
| **Restricción de contexto** | Eventos de alta demanda (lanzamiento de convocatorias populares) concentran tráfico en ventanas de segundos, no minutos |
| **Decisiones que dispara** | ADR-012 (bloqueo pesimista SELECT FOR UPDATE), ADR-008 (Inscription Service aislado), ADR-018 (ShedLock para no duplicar eventos en escala horizontal) |
| **Forma de validación** | Test de concurrencia: 200 hilos simultáneos intentan inscribirse a un evento con 1 cupo. Exactamente 1 inscripción confirmada; las otras 199 reciben HTTP 409. |

**Análisis de la decisión clave (ADR-012):** El bloqueo pesimista (`SELECT FOR UPDATE`) garantiza que cuando 200 usuarios intentan el último cupo simultáneamente, solo uno de ellos puede ejecutar el `UPDATE evento SET cupo_disponible = cupo_disponible - 1`. El segundo usuario, al obtener el lock, lee `cupo_disponible = 0` y recibe un rechazo limpio. Ver sección 6.1 para la secuencia detallada.

---

### DA-02 — Procesamiento asíncrono masivo

| Atributo | Valor |
|---|---|
| **Origen documental** | RF-50 (notificaciones de confirmación: volumen proyectado 10,000 correos/evento), RF-30 (certificados PDF: 5,000/evento), CA-04 (rendimiento: generación asíncrona) |
| **Métrica concreta** | Latencia p95 de la API de inscripción NO debe incluir tiempo de generación de email ni PDF. Certificado disponible en <30s desde registro de asistencia (RNF-07). |
| **Restricción de contexto** | La API de inscripción tiene SLA de <2s (DA-01); el envío de email y la generación de PDF son operaciones de >1s cada una |
| **Decisiones que dispara** | ADR-006 (colas de mensajes para desacoplamiento), ADR-008 (Outbox Pattern para consistencia), ADR-019 (DLQ para mensajes fallidos) |
| **Forma de validación** | Test E2E: procesar webhook → outbox vacío en <10s (Awaitility) → mensaje en cola RabbitMQ. Para certificados: test de tiempo end-to-end (pendiente Certificate Service). |

**Por qué Outbox Pattern (ADR-008) y no llamada directa:** Si `InscripcionService` llama directamente a `NotificationService.enviarCorreo()` de forma síncrona, un fallo del servidor de correo cancela la inscripción. Si lo hace asíncronamente sin Outbox, el mensaje puede perderse en un crash post-COMMIT. El Outbox garantiza que el evento existe en la BD antes de ser publicado al broker.

---

### DA-03 — Integraciones externas con resiliencia

| Atributo | Valor |
|---|---|
| **Origen documental** | RF-01 (SSO Google), RF-22 (pasarela de pago MercadoPago), RNF-02 (disponibilidad sistema ≥99.5% aunque los externos fallen), RN-13 (idempotencia webhooks) |
| **Métrica concreta** | Sistema responde HTTP 503 informativo en <500ms cuando la pasarela no está disponible (no cuelga ni da timeout al usuario). Webhook duplicado no genera doble cargo. |
| **Restricción de contexto** | El sistema no controla la disponibilidad de Google, MercadoPago ni Zoom. Deben asumirse caídas temporales de cualquiera de ellos. |
| **Decisiones que dispara** | ADR-009 (Circuit Breaker Resilience4j), ADR-013 (Factory Method pasarela — testeable sin HTTP real), ADR-008 (Outbox + idempotencia por referencia_externa) |
| **Forma de validación** | `PagoFlowEndToEndIT.webhookDuplicado_esIdempotente()`: mismo webhook enviado 2 veces → exactamente 1 evento en outbox. Circuit Breaker: test con mock que simula 5 fallos consecutivos → estado OPEN → fallback 503. |

---

### DA-04 — Cumplimiento legal y auditoría

| Atributo | Valor |
|---|---|
| **Origen documental** | RNF-04 (Ley 1581 — datos personales encriptados en reposo), RF-030 (trazabilidad de pagos), RF-029 (derecho al olvido) |
| **Métrica concreta** | Dump de BD no puede exponer `numero_documento` en claro. Log de auditoría incluye cada webhook con timestamp, estado y referencia_externa. |
| **Restricción de contexto** | Ley 1581 (Colombia) exige consentimiento, acceso, rectificación y supresión de datos personales. No cumplirla es responsabilidad legal. |
| **Decisiones que dispara** | ADR-010 (cifrado AES-256 via pgcrypto), ADR-007 (OIDC con Google — evita almacenar credenciales), ADR-008 (Outbox preserva rastro de eventos de dominio) |
| **Forma de validación** | Test de auditoría: verificar que `metadatos_pasarela` en PAGO contiene el payload completo del webhook. Verificar que `numero_documento` sale como texto cifrado en consulta directa a BD. |

---

## 3. Tabla Maestra de Trazabilidad

> Una fila por RF aprobado del SRS. Estado: ✅ Implementado / 🔄 En progreso / ⏳ Pendiente.

### 3.1 Módulo de Autenticación y Autorización

| Necesidad de negocio | RF | RN | Driver | Decisión (ADR) | Componente que materializa | Patrón GoF/SOLID | Test que valida | Métrica esperada | Estado |
|---|---|---|---|---|---|---|---|---|---|
| Acceso seguro con identidad institucional | RF-01 | RN-05 (solo @javeriana.edu.co vía OIDC) | DA-04 | ADR-007 (OIDC), ADR-004 (AES-256) | auth-service (en diseño) | Strategy (AuthProvider) | Pendiente | Login <1s; 0 credenciales almacenadas | ⏳ Pendiente |
| Acceso de usuarios externos (no Javeriana) | RF-01b | RN-05 (encriptación Ley 1581) | DA-04 | ADR-004 (AES-256), ADR-010 | auth-service | Facade | Pendiente | Registro <2s; datos encriptados en reposo | ⏳ Pendiente |
| Control de acceso por rol y por evento | RF-02 | RN-10 (scope de rol por evento) | DA-04 | ADR-007 (OIDC + RBAC) | auth-service | Policy (SOLID OCP) | Pendiente | Organizador Evento-A no accede a Evento-B | ⏳ Pendiente |

### 3.2 Módulo de Gestión de Eventos

| Necesidad de negocio | RF | RN | Driver | Decisión (ADR) | Componente que materializa | Patrón GoF/SOLID | Test que valida | Métrica esperada | Estado |
|---|---|---|---|---|---|---|---|---|---|
| Ciclo de vida completo de un evento | RF-10 | RN-08 (cupo ≥ 0) | — | ADR-001 (microservicio), ADR-005 (hexagonal) | `CrearEventoService`, `PublicarEventoService` | State (EstadoEvento), Factory | Pendiente IT | Creación evento <500ms | ✅ Implementado |
| Validación anti-solapamiento de sesiones | RF-11 | RN-09 (sin solapamiento mismo espacio-horario) | — | ADR-001 | `Evento.agregarSesion()` (dominio) | State, Template Method | Pendiente IT | Validación en capa dominio, O(n) | ✅ Implementado |
| Disponibilidad informativa de espacios | RF-12 | RN-09 | — | ADR-001 (solo disponibilidad, no gestión) | `EspacioFisico` (value object) | Value Object (DDD) | Pendiente | Consulta disponibilidad <200ms | ✅ Implementado |
| Catálogo de eventos con filtros (<300ms) | RF-13 | — | DA-01 | ADR-003 (Redis), ADR-005 (CQRS lectura) | `ConsultarCatalogoService`, `RedisEventoCacheAdapter` | Adapter (redis), CQRS | Pendiente IT | p95 <300ms bajo 500 RPS concurrentes | ✅ Implementado |

### 3.3 Módulo de Inscripción (driver principal: DA-01)

| Necesidad de negocio | RF | RN | Driver | Decisión (ADR) | Componente que materializa | Patrón GoF/SOLID | Test que valida | Métrica esperada | Estado |
|---|---|---|---|---|---|---|---|---|---|
| Inscripción sin condición de carrera en cupos | RF-20 | RN-01 (SELECT FOR UPDATE), RN-02 (timeout 15min) | DA-01 | ADR-012 (bloqueo pesimista), ADR-008 (Inscription Service) | `CrearInscripcionService`, `JpaInscripcionRepository.guardarConReservaDeCupo()` | Strategy (UseCase), Repository | `InscripcionConcurrencyIT` (⏳ pendiente) | p95 <2s bajo 200 concurrentes; error <1% | ✅ Implementado |
| Evitar inscripción duplicada por retry de red | RF-20b | RN-01 + idempotency_key | DA-01 | ADR-009 (idempotency keys) | `CrearInscripcionService.buscarPorIdempotencyKey()` | Command + Idempotent Receiver | Pendiente | Segunda llamada con misma key → HTTP 200 sin duplicado | ✅ Implementado |
| Liberación de cupo al cancelar | RF-21 | RN-02 (cupo se libera si EXPIRADA/CANCELADA) | DA-01 | ADR-012 | `InscripcionRepository.guardar()` (estado CANCELADA atomico) | State (EstadoInscripcion) | Pendiente | Cupo liberado atómicamente con cambio de estado | ✅ Implementado |
| Expiración automática si no paga en 15min | RF-22b | RN-02 | DA-01 | ADR-008 (job scheduler) | `ExpirarInscripcionesService`, `InscripcionExpirationJob` (@Scheduled) | Observer + Command | Pendiente IT | Cupos liberados en <1min tras expiración | ✅ Implementado |

### 3.4 Módulo de Pagos (driver principal: DA-03)

| Necesidad de negocio | RF | RN | Driver | Decisión (ADR) | Componente que materializa | Patrón GoF/SOLID | Test que valida | Métrica esperada | Estado |
|---|---|---|---|---|---|---|---|---|---|
| Integración con pasarela externa sin coupling | RF-22 | RN-03 (idempotencia referencia_externa), RN-04 (no almacenar tarjeta) | DA-03 | ADR-009 (Circuit Breaker), ADR-013 (Factory Method pasarela), ADR-008 (Outbox) | `CrearPreferenciaService` (factory), `ProcesarWebhookService` (outbox) | Factory Method (PasarelaPagoFactory), Observer (DomainEvent) | `PagoFlowEndToEndIT.webhookConfirmado_publicaEnRabbitMQ()` | Webhook procesado <500ms; duplicado ignorado en <100ms | ✅ Implementado |
| Proteger al usuario de doble cobro | RF-22 (idempotencia) | RN-13 | DA-03 | ADR-009 (ref_ext UNIQUE) | `ProcesarWebhookService.procesar()` verificación idempotencia | Guard Clause | `PagoFlowEndToEndIT.webhookDuplicado_esIdempotente()` | 2 webhooks idénticos → 1 outbox event | ✅ Implementado |
| Reembolsar pago tardío post-expiración | RF-22c | RN-10, RN-PAGO-05 (propuesta) | DA-03 | ADR-013, ADR-008 (Outbox) | `ProcesarWebhookService.procesarPagoTardio()`, `Pago.reembolsarPorExpiracion()` | State (Pago), Template Method | `PagoTest.reembolsarPorExpiracion_desdeIniciado_*` (5 tests) | INSCRIPCION_EXPIRADA retornado; reembolso emitido | ✅ Implementado |
| Tarifas diferenciadas por perfil de asistente | RF-23 | Invariante: 1 tarifa activa por (evento, categoría) | — | ADR-001 (Evento Service) | `Tarifa` (value object en Evento) | Value Object | Pendiente IT | Tarifa correcta aplicada por categoría | ✅ Implementado |

### 3.5 Módulo de Certificados

| Necesidad de negocio | RF | RN | Driver | Decisión (ADR) | Componente que materializa | Patrón GoF/SOLID | Test que valida | Métrica esperada | Estado |
|---|---|---|---|---|---|---|---|---|---|
| Generar PDF sin impactar latencia de la API | RF-30 | RN-06 (asistencia ≥ 80%) | DA-02 | ADR-006 (cola async), ADR-004 (S3) | certificate-service (diseño) | Builder (PDF) + Observer | Pendiente | Disponible <30s tras asistencia (RNF-07) | ⏳ Pendiente |
| Verificación pública por QR sin datos sensibles | RF-31 | RN-06 + codigo_verificacion único | DA-04 | ADR-004 (S3), ADR-005 (endpoint público) | certificate-service (verificación) | Proxy (acceso público sin auth) | Pendiente | Verificación <500ms sin datos PII expuestos | ⏳ Pendiente |

### 3.6 Módulo de Notificaciones

| Necesidad de negocio | RF | RN | Driver | Decisión (ADR) | Componente que materializa | Patrón GoF/SOLID | Test que valida | Métrica esperada | Estado |
|---|---|---|---|---|---|---|---|---|---|
| Notificaciones sin afectar request-path | RF-50 | — | DA-02 | ADR-006 (cola), ADR-008 (Outbox), ADR-019 (DLQ) | notification-service (diseño), `OutboxRelayService` | Observer + Outbox | `PagoFlowEndToEndIT` (verifica mensaje en cola) | Email enviado en <10s; sin impacto en latencia inscripción | 🔄 En progreso |
| Reenvío manual de notificaciones fallidas | RF-51 | — | DA-02 | ADR-019 (DLQ) | eventos.dlq + admin endpoint | Chain of Responsibility | Pendiente | Mensaje en DLQ disponible para reenvío manual | ⏳ Pendiente |

### 3.7 RNFs Críticos

| RNF | Descripción | Driver | Decisión (ADR) | Componente | Forma de validación | Estado |
|---|---|---|---|---|---|---|
| RNF-01 | Catálogo <300ms p95 | DA-01 | ADR-003, ADR-005 | `ConsultarCatalogoService` + Redis | k6: 500 RPS, medir p95 | 🔄 En progreso |
| RNF-02 | Disponibilidad ≥99.5% | DA-03 | ADR-009 (Circuit Breaker) | `CrearPreferenciaService` fallback | Uptime check cada 30s; testar fallback CB | 🔄 En progreso |
| RNF-03 | Inscripción <2s p95 bajo 200 concurrentes | DA-01 | ADR-012, ADR-008 | `CrearInscripcionService` | k6: 200 VUs concurrentes × 5min | 🔄 En progreso |
| RNF-04 | Datos personales encriptados (Ley 1581) | DA-04 | ADR-004 (AES-256), ADR-007 (OIDC) | auth-service + PostgreSQL pgcrypto | Auditoría: dump BD sin PII en claro | ⏳ Pendiente |
| RNF-06 | API <500ms p99 | DA-01 | ADR-009 (Circuit Breaker timeout 5s) | API Gateway | APM (OpenTelemetry) | ⏳ Pendiente |

---

## 4. Justificación de cada microservicio

Esta sección responde a la crítica del profesor: *"la sobreingeniería persiste y es difícil de defender versus alternativas más simples"*.

| Servicio | Bounded Context | Driver que justifica separación | Alternativa monolítica considerada | Razón de descartar |
|---|---|---|---|---|
| `event-service` | Ciclo de vida de eventos, sesiones, espacios | DA-01: catálogo con alta lectura (RF-13, RNF-01) escala independiente de write. DA-02: publicación de eventos no debe bloquear inscripciones | Módulo en monolito con Redis interno | No permite escalar solo el path de lectura bajo 500 RPS sin escalar también escritura y pagos |
| `inscription-service` | Inscripciones, cupos, expiración | DA-01: bloqueo pesimista de cupos exige transacciones cortas; mezclar con PDF/notif alargaría locks | Módulo en monolito | Un fallo en `certificate-service` haría rollback de inscripciones si comparten transacción |
| `payment-service` | Webhooks, idempotencia, Circuit Breaker pasarela | DA-03: integración con MercadoPago requiere resiliencia (CB, retry) que aísla el fallo de la pasarela | Clase en `inscription-service` | El Circuit Breaker afectaría el servicio completo si la pasarela falla; separado, solo payment-service lo siente |
| `notification-service` | Emails transaccionales | DA-02: 10,000 correos/evento = carga de CPU independiente del path de inscripción | Cola en inscription-service | Comparte CPU con el servicio de alta concurrencia (DA-01); email lento degradaría inscripciones |
| `certificate-service` | PDFs, S3, verificación QR | DA-02: generación PDF es CPU-intensiva (<30s, RNF-07); no puede estar en request-path | Worker thread en inscription-service | Consume CPU y bloquea threads del servidor web; viola RNF-01 y RNF-03 |
| `auth-service` | OIDC, RBAC, JWT | DA-04: SSO institucional es requisito de seguridad transversal; RBAC requiere store centralizado | JWT stateless en cada servicio | Sin store centralizado no es posible revocar tokens ni gestionar roles por evento (RN-10) |

**Veredicto:** Todos los microservicios tienen un driver real que justifica la separación. El único candidato a revisión es `auth-service` si se considera OIDC nativo vía Spring Security sin servicio propio (ADR pendiente de revisión).

---

## 5. Decisiones Conscientemente Simplificadas

| Complejidad | Decisión tomada | Justificación |
|---|---|---|
| **Service Mesh (Istio/Linkerd)** | Postergado. Se usa Spring Cloud Gateway como único proxy | No justificado: el equipo de 1 persona no tiene capacidad operativa para Istio. Docker Compose cubre el scope del proyecto. |
| **Event Sourcing** | Descartado. Se usa CRUD con eventos de dominio via Outbox | Event Sourcing requiere infraestructura adicional (Event Store) y complejidad de reconstrucción de estado. CRUD + Outbox cubre los requerimientos de auditoría declarados. |
| **Saga Pattern (orquestación)** | Parcialmente. Se usa coreografía vía eventos de dominio, no orquestación | La orquestación (Saga Orchestrator) añadiría un componente de estado central. La coreografía con Outbox y consumidores idempotentes cubre el caso de uso con menos complejidad. Deuda documentada: sin compensación automática (ver `payment-refund-async.md`). |
| **CQRS completo** | Solo en `event-service` (catálogo). Los demás servicios usan CRUD simple | El catálogo de eventos es el único path con RNF de <300ms bajo alta concurrencia de lectura. En los demás servicios CQRS añadiría complejidad sin beneficio demostrable dado el volumen de datos. |
| **Kubernetes** | Postergado. Se usa Docker Compose + EC2 | Costo operativo de K8s (ingeniería de plataforma) no está justificado con restricción de $200/mes y equipo de 1 persona. Docker Compose en EC2 t3.medium cuesta ~$30/mes vs. ~$300+/mes para K8s gestionado. Puede evolucionar cuando el equipo crezca. |
| **Distributed Tracing (Jaeger)** | Configurado vía OpenTelemetry pero sin servidor de trazas | La instrumentación está en el código; el servidor se levanta en demanda para debugging. En producción se añadiría Jaeger o Zipkin. |

---

## 6. Evidencia Operativa de Mecanismos Críticos

### 6.1 Bloqueo Pesimista — Concurrencia en cupos (DA-01, ADR-012)

**Escenario:** 2 usuarios (A y B) intentan inscribirse simultáneamente al mismo evento con exactamente 1 cupo.

```
Thread A                                       Thread B
─────────────────────────────────────────────────────────────────────
BEGIN TRANSACTION;                             BEGIN TRANSACTION;
                                               
SELECT id, cupo_disponible                     SELECT id, cupo_disponible
FROM evento_cupo                               FROM evento_cupo
WHERE evento_id = 'X'                          WHERE evento_id = 'X'
FOR UPDATE;          ← Adquiere lock           FOR UPDATE;  ← BLOQUEADO (espera a A)
                                               
-- cupo_disponible = 1 → OK
INSERT INTO inscripcion (...PENDIENTE_PAGO...);
UPDATE evento_cupo SET cupo_disponible = 0;
COMMIT;              ← Lock liberado           
                                               ← Thread B se desbloquea
                                               -- cupo_disponible = 0 → SinCuposDisponiblesException
                                               ROLLBACK;  → HTTP 409 Conflict
```

**Código real** (`JpaInscripcionRepository.java`):
```java
// inscription-service/.../infrastructure/persistence/JpaInscripcionRepository.java
@Override
@Transactional(propagation = Propagation.MANDATORY)
public Inscripcion guardarConReservaDeCupo(Inscripcion inscripcion) {
    EventoCupoEntity cupo = eventoCupoRepo
        .findByEventoIdWithLock(inscripcion.getEventoId())  // SELECT FOR UPDATE
        .orElseThrow(...);
    
    if (cupo.getCupoDisponible() <= 0) {
        throw new SinCuposDisponiblesException(inscripcion.getEventoId()); // → HTTP 409
    }
    cupo.setCupoDisponible(cupo.getCupoDisponible() - 1);   // atómico
    eventoCupoRepo.save(cupo);
    return toDomain(inscripcionRepo.save(toEntity(inscripcion)));
}
```

**Test pendiente:** `InscripcionConcurrencyIT.testCincuentaUsuariosUnCupo()` — 50 threads simultáneos, solo 1 debe confirmar (a implementar en Commit 6).

**Garantía:** La invariante `cupo_disponible ≥ 0` (RN-01) se cumple matemáticamente aunque 5,000 usuarios intenten el último cupo al mismo tiempo.

---

### 6.2 Outbox Pattern — Consistencia eventual con notificaciones (DA-02, ADR-008, ADR-018)

**Problema resuelto:** ¿Cómo garantizar que la notificación al usuario llega exactamente una vez, aunque el servidor se caiga justo después de confirmar el pago?

```
Sin Outbox (problema):
  COMMIT {pago confirmado} → servidor cae → notificación NUNCA enviada

Con Outbox + ShedLock (solución):
  COMMIT {pago confirmado + outbox_event}   ← transacción única
  → servidor cae
  → al reiniciar: OutboxRelayService lee outbox pendientes → publica en RabbitMQ
  → ShedLock (ADR-018): aunque hay 3 réplicas, solo 1 ejecuta el relay
  → NotificationService procesa y envía email al usuario
```

**Flujo de la transacción (código real):**
```
ProcesarWebhookService.procesarAprobado():
  1. pago.confirmar(referencia, metadatos)      ← registra PagoConfirmadoEvent internamente
  2. pagoRepository.guardar(pago)               ← UPDATE pago SET estado=CONFIRMADO
  3. for event in pago.pullDomainEvents():
       outboxRepository.guardar(crearOutboxEvent(event))  ← INSERT outbox_events
  4. COMMIT  ←  pago + outbox en MISMO commit atómico
```

**OutboxRelayService** (cada 2 segundos, protegido por ShedLock):
```
1. SELECT * FROM outbox_events WHERE published=false LIMIT 50
2. para cada evento:
   a. RabbitMQ.publish(exchange="eventos.topic", routingKey="pago.confirmado")
   b. event.published = true; event.published_at = now()
   c. si falla: attempts++ (retry en siguiente tick)
   d. si attempts >= 5: publicar a DLQ (ADR-019)
```

**Garantías:**
- **At-least-once en broker:** si el relay cae después de publish pero antes del UPDATE, el próximo tick republica.
- **Deduplicación:** `messageId = event_id` (UUID) → consumers idempotentes.
- **Sin duplicación entre réplicas:** ShedLock garantiza exactamente 1 réplica ejecuta el relay en un instante dado.

---

### 6.3 Procesamiento Asíncrono — Escalabilidad de notificaciones y certificados (DA-02)

**Distribución de carga:**

```
InscripcionService         RabbitMQ           NotificationService   CertificateService
      │                      │                       │                     │
      │ pago confirmado       │                       │                     │
      │──OutboxRelay──────►pago.confirmado            │                     │
      │                      │──────────────────────►consume/email         │
      │                      │                       │   (paralelo, N workers)
      │ asistencia registrada │                       │                     │
      │──OutboxRelay──────►asistencia.registrada      │                     │
      │                      │──────────────────────────────────────────►consume/PDF
      │                                                                     │
      │                                                         N workers generan PDF en paralelo
```

**Backpressure:**
- Cada worker consume mensajes de su cola con `prefetch=1` (Spring AMQP default): no acepta el siguiente mensaje hasta confirmar el actual.
- Si todos los workers están ocupados, los mensajes esperan en la cola RabbitMQ (buffer persistente, `x-message-ttl: 86400000ms`).
- El productor (OutboxRelay) no se bloquea: publica y continúa.

**Recuperación de mensajes fallidos (DLQ, ADR-019):**
- Después de 3 reintentos con backoff exponencial (1s, 4s, 16s), el mensaje pasa a `eventos.dlq`.
- El operador puede reprocesar manualmente via `POST /api/v1/admin/dlq/reprocesar`.
- Headers de trazabilidad: `x-failure-reason`, `x-attempt-count`, `x-correlation-id`.
- Alarma: `rabbitmq_queue_messages{queue="eventos.dlq"} > 0` debe notificar al equipo de operaciones.

---

## 7. Resumen ejecutivo — Respuesta al feedback del profesor

| Crítica del profesor | Mecanismo de respuesta | Evidencia en este documento |
|---|---|---|
| "Arquitectura avanza más rápido que especificación" | Tabla Maestra §3: cada componente implementado tiene su RF, RN, Driver y ADR | RF-22c (reembolso tardío) trazado a RN-10, RN-PAGO-05, ADR-013, código y test |
| "Criterios de aceptación no homogéneos" | Columna "Test que valida" y "Métrica esperada" en cada fila de la tabla | `PagoFlowEndToEndIT`, `PagoTest`, tests pendientes con clase y escenario explícitos |
| "Concurrencia y escalabilidad no claras operativamente" | Sección 6: pseudocódigo, diagramas de secuencia y código real | §6.1 bloqueo pesimista con código real; §6.2 Outbox con flujo transaccional; §6.3 backpressure |
| "Sobreingeniería difícil de defender" | Sección 4: tabla de justificación por servicio; sección 5: decisiones descartadas | Cada microservicio tiene un driver con métrica concreta; K8s/EventSourcing/Saga descartados con justificación |
