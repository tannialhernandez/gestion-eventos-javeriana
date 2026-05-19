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
Implementar una estrategia de Dead Letter Queue genérica a nivel de 
infraestructura RabbitMQ:

1. Exchange Dead Letter: eventos.dlq.exchange (tipo topic, durable).
2. Cola Dead Letter: eventos.dlq (durable), bindeada con routing key # 
   para capturar todos los mensajes fallidos.
3. Configuración DLX en colas productivas: cada cola consumidora declara:
   - x-dead-letter-exchange: eventos.dlq.exchange
   - x-dead-letter-routing-key: <routing-key-original>.dlq
4. Política de reintento: máximo 3 reintentos con backoff exponencial 
   (1s, 4s, 16s) vía Spring AMQP RetryTemplate. Tras agotar reintentos, 
   el mensaje se publica al DLX automáticamente.
5. Header de trazabilidad: cada mensaje incluye headers x-correlation-id, 
   x-original-routing-key, x-failure-reason, x-attempt-count.

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
