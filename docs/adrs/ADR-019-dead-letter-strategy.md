# ADR-019: Estrategia de Dead Letter Queue para Eventos de Dominio

## Estado
Aceptada

## Fecha
2026-05-18

## Contexto
El sistema utiliza RabbitMQ como broker de eventos de dominio (ADR-Outbox), 
con un único exchange topic eventos.topic y routing keys jerárquicas 
(pago.confirmado, inscripcion.creada, evento.publicado, etc.).

Durante el procesamiento asíncrono de mensajes, los consumidores pueden 
fallar por causas transitorias (error de red, timeout de BD) o 
permanentes (mensaje malformado, violación de regla de negocio en el 
consumer). Sin una estrategia de Dead Lettering:

- Los mensajes con errores transitorios pueden reintentarse infinitamente, 
  saturando el broker (livelock).
- Los mensajes con errores permanentes bloquean la cola si el consumer 
  no los rechaza correctamente.
- Se pierde trazabilidad de mensajes problemáticos, dificultando la 
  conciliación operativa requerida en 
  docs/comportamiento-runtime-inscripcion-pago.md.

## Decisión
Implementar una estrategia de Dead Letter Queue en infraestructura RabbitMQ.

El sistema mantiene **dos Dead Letter Exchanges coexistiendo** durante la fase
de evolución incremental (deuda técnica documentada en
`docs/follow-ups/dlx-unification.md`):

**1. `eventos.dlx` (existente, tipo direct) — DLX per-queue productivo:**
   - Configurado como `x-dead-letter-exchange` en todas las colas consumidoras
     existentes (`pago.confirmado`, `pago.reembolsado`, `inscripcion.confirmada`,
     `certificado.solicitado`).
   - Cada cola tiene `x-dead-letter-routing-key: dlq.{routing-key-original}`.
   - `eventos.dlx` enruta a colas DLQ específicas (`dlq.pago.confirmado`, etc.)
     para aislamiento por tipo de mensaje.
   - Sin TTL en colas DLQ — mensajes conservados hasta intervención manual.

**2. `eventos.dlq.exchange` (nuevo, tipo topic) — red de seguridad catch-all:**
   - Binding `#` → cola `eventos.dlq` (captura cualquier routing key).
   - Usado por `OutboxRelayService` como destino explícito cuando un evento
     supera el umbral de reintentos (attempts >= 5).
   - Facilita el monitoreo central: una sola cola a vigilar para mensajes
     que no pudieron ser procesados por el relay.

**3. Política de reintento en OutboxRelayService:**
   - Cada tick (2 segundos, protegido por ShedLock ADR-018) intenta publicar.
   - Si falla: incrementa `attempts` en `outbox_events`.
   - Si `attempts >= 5`: publica a `eventos.dlq.exchange` con routing key
     `dlq.{tipo-evento}` y marca el evento como `published=true`.

**4. Header de trazabilidad:** cada mensaje incluye `messageId` = event UUID
   (garantía de idempotencia en consumers, ADR-008).

> **Nota:** La unificación de ambos DLX bajo `eventos.dlq.exchange` está agendada
> como trabajo futuro. Ver `docs/follow-ups/dlx-unification.md`.

## Alternativas descartadas
- DLQ por servicio (payment.dlq, inscription.dlq): descartada por 
  fragmentación operativa innecesaria. Una DLQ centralizada simplifica 
  el monitoreo y la conciliación humana.
- Sin DLQ + logging de errores: descartada porque pierde el mensaje 
  original y dificulta el reprocesamiento manual.
- Discard puro (requeue=false sin DLX): descartada porque elimina 
  trazabilidad y viola los principios de auditoría requeridos por el SRS.

## Consecuencias

### Positivas
- (+) Trazabilidad completa de mensajes fallidos para conciliación humana.
- (+) Resiliencia: los errores transitorios se recuperan automáticamente 
  vía reintento exponencial.
- (+) Aislamiento de mensajes problemáticos: no bloquean el flujo principal.
- (+) Consistente con el principio Fail-Closed declarado en el glosario 
  del SRS.

### Negativas
- (-) Complejidad operativa adicional: requiere monitorear eventos.dlq y 
  definir un proceso de reprocesamiento manual.
- (-) Posible duplicación si el operador reprocesa un mensaje cuya 
  ejecución parcial ya tuvo efectos colaterales. Mitigación: idempotencia 
  obligatoria en todos los consumers (RN-13).
- (-) Requiere alarma Prometheus: 
  rabbitmq_queue_messages{queue="eventos.dlq"} > 0 debe disparar 
  notificación al equipo de operaciones.

## Trazabilidad
- RNF-04 Procesamiento asíncrono masivo.
- RNF-08 Disponibilidad y resiliencia.
- RN-13 Idempotencia.
- ADR-Outbox Transactional Outbox Pattern.
- Patrón aplicado: Dead Letter Channel (Enterprise Integration Patterns, 
  Hohpe & Woolf).

## Referencias
- Hohpe, G., & Woolf, B. (2003). Enterprise Integration Patterns, 
  cap. "Dead Letter Channel".
- RabbitMQ documentation: https://www.rabbitmq.com/dlx.html
