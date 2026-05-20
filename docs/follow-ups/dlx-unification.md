# Follow-up: Unificación de Dead Letter Exchanges

**Servicio afectado:** Infraestructura RabbitMQ (`infrastructure/rabbitmq/definitions.json`)  
**Creado:** 2026-05-19  
**Estado:** Deuda técnica — no bloqueante para Entrega 3  
**Relacionado con:** ADR-019 (Dead Letter Queue Strategy)

---

## 1. Estado actual — dos DLX coexistiendo

El sistema tiene dos Dead Letter Exchanges en el vhost `eventos`:

| Exchange | Tipo | Quién lo usa |
|---|---|---|
| `eventos.dlx` | direct | Todas las colas productivas existentes (x-dead-letter-exchange) |
| `eventos.dlq.exchange` | topic | `OutboxRelayService` (publicación directa tras 5 intentos) |

**Colas DLQ per-queue** (via `eventos.dlx`):
- `dlq.pago.confirmado`
- `dlq.pago.reembolsado`
- `dlq.inscripcion.confirmada`
- `dlq.certificado.solicitado`

**Cola catch-all** (via `eventos.dlq.exchange`):
- `eventos.dlq`

---

## 2. Razón de la coexistencia

El ADR-019 fue redactado describiendo una topología ideal con un único
`eventos.dlq.exchange`. Al verificar la infraestructura existente, se
descubrió que `eventos.dlx` ya estaba configurado en todas las colas
productivas con bindings per-queue.

Cambiar el `x-dead-letter-exchange` de todas las colas productivas
implica recrearlas (RabbitMQ no permite modificar argumentos de una cola
existente sin eliminarla). Esta operación requiere una ventana de
mantenimiento coordinada con drenaje de mensajes — no apta para aplicar
en medio del trabajo de implementación.

Decisión: dejar ambos DLX coexistiendo y documentar la unificación como
deuda técnica.

---

## 3. Plan de unificación

### Objetivo
Un único DLX: `eventos.dlq.exchange` (topic). Todas las colas productivas
configuran `x-dead-letter-exchange: eventos.dlq.exchange`. Las colas
`dlq.*` per-queue se retienen pero se bindean al nuevo exchange.

### Precondiciones
- [ ] Todas las colas productivas drenadas (0 mensajes pendientes).
- [ ] Ventana de mantenimiento fuera de horario de carga.
- [ ] Backup del `definitions.json` previo.
- [ ] Consumer de `eventos.dlq` activo para no perder mensajes durante migración.

### Pasos

**1. Bindear `dlq.*` queues al nuevo exchange (no destructivo):**
```json
{
  "source": "eventos.dlq.exchange",
  "destination": "dlq.pago.confirmado",
  "routing_key": "dlq.pago.confirmado"
}
```
Repetir para cada cola `dlq.*`. Esto permite que ambos exchanges ruteen
a las mismas DLQ durante la transición.

**2. Eliminar y recrear colas productivas con nuevo DLX:**
```bash
# Para cada cola productiva:
rabbitmqctl delete_queue pago.confirmado
rabbitmqctl declare_queue pago.confirmado \
  x-dead-letter-exchange=eventos.dlq.exchange \
  x-dead-letter-routing-key=dlq.pago.confirmado \
  x-message-ttl=86400000
```

**3. Verificar que todos los bindings funcionan.**

**4. Eliminar `eventos.dlx` y los bindings obsoletos.**

**5. Actualizar `definitions.json` y ADR-019.**

### Riesgos

| Riesgo | Mitigación |
|---|---|
| Mensajes en vuelo durante la recreación de colas | Drenar antes + ventana de mantenimiento |
| Consumer rechaza mensaje después de recrear la cola pero antes de bindear la DLQ al nuevo exchange | Transición secuencial: primero bindear, luego recrear |
| Error humano en el orden de pasos | Script automatizado y probado en entorno de staging primero |

---

## 4. Prioridad

**Baja para la Entrega 3 académica.** Los dos DLX funcionan correctamente:
mensajes de consumers van a `eventos.dlx` → DLQ específicas, y mensajes
del OutboxRelayService van a `eventos.dlq.exchange` → `eventos.dlq`.
No hay pérdida de mensajes, solo complejidad operativa adicional.

**Media para producción real.** Un solo DLX simplifica el monitoreo y
la conciliación.
