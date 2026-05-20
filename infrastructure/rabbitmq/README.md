# Topología RabbitMQ — Plataforma de Gestión de Eventos Académicos

**Archivo de configuración:** `definitions.json`  
**Vhost:** `eventos`  
**Última actualización:** 2026-05-19

---

## Exchanges

| Exchange | Tipo | Propósito |
|---|---|---|
| `eventos.topic` | topic | Exchange principal. Todos los servicios publican aquí con routing keys jerárquicas. |
| `eventos.dlx` | direct | **DLX productivo.** Recibe mensajes rechazados de las colas consumidoras existentes. Cada cola tiene su propia binding hacia una `dlq.*` específica. |
| `eventos.dlq.exchange` | topic | **Catch-all DLQ.** Red de seguridad para mensajes huérfanos o publicaciones directas desde código (ej. `OutboxRelayService` tras 5 intentos fallidos). Binding `#` → cola `eventos.dlq`. |

> **Nota:** El sistema tiene dos Dead Letter Exchanges coexistiendo intencionalmente durante la fase de evolución incremental. Ver plan de unificación en `docs/follow-ups/dlx-unification.md`.

---

## Colas

### Colas productivas

| Cola | DLX configurado | DLQ destino | TTL |
|---|---|---|---|
| `inscripcion.confirmada` | `eventos.dlx` | `dlq.inscripcion.confirmada` | 24h |
| `pago.confirmado` | `eventos.dlx` | `dlq.pago.confirmado` | 24h |
| `pago.reembolsado` | `eventos.dlx` | `dlq.pago.reembolsado` | 24h |
| `certificado.solicitado` | `eventos.dlx` | `dlq.certificado.solicitado` | 24h |

### Colas DLQ per-queue (via `eventos.dlx`)

| Cola | Routing key de entrada | Sin TTL — mensajes conservados hasta intervención manual |
|---|---|---|
| `dlq.inscripcion.confirmada` | `dlq.inscripcion.confirmada` | ✅ |
| `dlq.pago.confirmado` | `dlq.pago.confirmado` | ✅ |
| `dlq.pago.reembolsado` | `dlq.pago.reembolsado` | ✅ |
| `dlq.certificado.solicitado` | `dlq.certificado.solicitado` | ✅ |

### Cola DLQ catch-all (via `eventos.dlq.exchange`)

| Cola | Routing key de entrada | Propósito |
|---|---|---|
| `eventos.dlq` | `#` (cualquier) | Red de seguridad para mensajes que no encajan en las DLQs per-queue; receptor del `OutboxRelayService` tras agotar reintentos (ADR-019). |

---

## Bindings

```
eventos.topic  ──[inscripcion.confirmada]──►  inscripcion.confirmada (cola)
eventos.topic  ──[pago.confirmado]──────────►  pago.confirmado (cola)
eventos.topic  ──[pago.reembolsado]─────────►  pago.reembolsado (cola)
eventos.topic  ──[certificado.solicitado]───►  certificado.solicitado (cola)

eventos.dlx    ──[dlq.inscripcion.confirmada]──►  dlq.inscripcion.confirmada
eventos.dlx    ──[dlq.pago.confirmado]──────────►  dlq.pago.confirmado
eventos.dlx    ──[dlq.pago.reembolsado]─────────►  dlq.pago.reembolsado
eventos.dlx    ──[dlq.certificado.solicitado]───►  dlq.certificado.solicitado

eventos.dlq.exchange  ──[#]──►  eventos.dlq
```

---

## Flujo de mensaje fallido (por cola productiva)

```
Consumer rechaza mensaje (NACK, no requeue)
      ↓
eventos.dlx (routing key: dlq.{nombre-cola})
      ↓
dlq.{nombre-cola}  ← inspección manual / reenvío via admin API
```

## Flujo de mensaje huérfano (OutboxRelayService tras 5 intentos)

```
OutboxRelayService detecta attempts >= 5
      ↓
Publica a eventos.dlq.exchange (routing key: dlq.pago.confirmado)
      ↓
eventos.dlq (catch-all con binding #)
      ↓
Inspección manual / alerta Prometheus
```

---

## Alarma recomendada

```
rabbitmq_queue_messages{queue="eventos.dlq"} > 0
```
→ Notificar al equipo de operaciones. Indica mensajes huérfanos que no pudieron ser procesados por el `OutboxRelayService` tras 5 intentos.

---

## Plan de unificación futura

Ver `docs/follow-ups/dlx-unification.md` para el plan de migrar todas las colas productivas a usar `eventos.dlq.exchange` como único DLX, eliminando la duplicidad actual.
