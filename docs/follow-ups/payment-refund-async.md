# Follow-up: Reembolso de Pago Tardío — Deuda Técnica y Solución Propuesta

**Servicio afectado:** `payment-service`  
**Archivo con deuda:** `ProcesarWebhookService.procesarPagoTardio()`  
**Creado:** 2026-05-18  
**Estado:** Pendiente de implementar  
**Relacionado con:** RN-10, ADR-11 (Outbox Pattern), docs/comportamiento-runtime-inscripcion-pago.md §4

---

## 1. El problema actual

En `ProcesarWebhookService.procesarPagoTardio()` existe la siguiente secuencia:

```java
// 1. Llamada HTTP a la pasarela (operación externa, no transaccional)
pasarela.reembolsar(payload.referenciaExterna(), pago.getMonto());

// 2. Actualización de BD (operación transaccional)
pago.reembolsarPorExpiracion();
pagoRepository.guardar(pago);
outboxRepository.guardar(evento);  // COMMIT aquí
```

### Escenario de fallo

```
T=0   pasarela.reembolsar() → ÉXITO en MercadoPago (dinero devuelto al usuario)
T=1   BD falla (OOM, crash, timeout de conexión)
T=2   COMMIT nunca ocurre
T=3   Estado BD: pago en PROCESANDO (no REEMBOLSADO)
T=4   OutboxRelayService: no publica nada (no hay evento pendiente)
T=5   inscription-service: nunca recibe notificación de reembolso
T=6   Estado divergente: pasarela reembolsó, sistema no lo sabe
```

### Consecuencias

- El usuario recibió el reembolso (correcto), pero el sistema no lo registró.
- Si el webhook llega de nuevo (retry de MercadoPago), pasa la guarda de idempotencia porque `referencia_externa` nunca se guardó → **doble reembolso posible**.
- inscription-service no libera el cupo ni notifica al usuario.

---

## 2. Decisión consciente de aceptar la deuda

Este comportamiento se acepta **temporalmente** en el fix `fix(payment-service): handle late payment refund flow` por dos razones:

1. **Baja probabilidad en el entorno actual:** el Simulador nunca falla, y los webhooks de prueba son enviados manualmente. El riesgo operativo es cero en desarrollo.
2. **Scope del fix:** el objetivo era corregir el bug de inyección del Factory Method, no rediseñar el flujo de reembolso.

La deuda técnica está marcada con `TODO[DEUDA-TÉCNICA]` en el código.

---

## 3. Solución propuesta (alineada con ADR-11 — Outbox Pattern)

El principio correcto es: **ninguna llamada a sistemas externos debe ocurrir antes del COMMIT de la transacción local**.

### Arquitectura correcta

```
T=0  Webhook llega con estado "approved" post-expiración
T=1  ProcesarWebhookService detecta pago tardío
T=2  pago.reembolsarPorExpiracion()              ← registra PagoReembolsadoEvent
T=3  pagoRepository.guardar(pago)
T=4  outboxRepository.guardar(PagoReembolsadoEvent)
T=5  COMMIT (pago + outbox en misma transacción)   ← NO hay llamada a pasarela aquí

T=6  OutboxRelayService publica PagoReembolsadoEvent a eventos.topic/pago.reembolsado
T=7  ReembolsoConsumer (nuevo, en payment-service) recibe el evento
T=8  ReembolsoConsumer llama pasarela.reembolsar(referenciaExterna, monto)
T=9  Si falla: mensaje vuelve al consumidor (at-least-once) → reintento automático
T=10 Si éxito: ACK, evento procesado
```

### Componentes nuevos necesarios

| Componente | Responsabilidad |
|---|---|
| `ReembolsoConsumer` | Consumer RabbitMQ que escucha `pago.reembolsado` y llama a `pasarela.reembolsar()` |
| `ReembolsoCommand` (opcional) | Comando separado para el reembolso, con idempotency key |

### Garantías de la solución propuesta

- **Atomicidad:** si la BD falla, el evento no existe → no hay reembolso spurious.
- **At-least-once:** si la pasarela falla, el consumer reintenta (RabbitMQ garantiza entrega).
- **Idempotencia:** `ReembolsoConsumer` debe verificar que el pago ya no esté `REEMBOLSADO` antes de llamar a la pasarela.
- **Sin doble reembolso:** la verificación de idempotencia en el consumer previene el escenario de retry duplicado.

---

## 4. Grace period de 60 segundos

En `application.yml`:
```yaml
payment.expiration.grace-period-seconds: 60
```

### Justificación

- RN-10 establece timeout de 15 minutos (900s) para el pago.
- El job de expiración de `inscription-service` corre cada **60 segundos** (`@Scheduled(fixedRate = 60_000)`).
- Race condition posible:
  - T=900s: job corre, marca inscripción como EXPIRADA
  - T=901s: webhook de MercadoPago llega (el pago ya fue procesado por la pasarela antes del job)
  - Sin grace period: el webhook pasaría la guarda de expiración (900s < 901s), se confirmaría el pago de una inscripción expirada
  - Con grace period de 60s: el webhook a T=901s NO activa el path de expiración (901 < 960), se confirma normalmente → correcto

Esta decisión favorece confirmar el pago cuando hay ambigüedad, delegando la verificación definitiva del estado de la inscripción a `inscription-service`.

---

## 5. Prioridad de implementación

**Baja** para el contexto académico actual (demo funciona con Simulador).  
**Alta** para producción real con MercadoPago.

Referencia: `git log --all --grep="RN-PAGO-05"` para commits relacionados.
