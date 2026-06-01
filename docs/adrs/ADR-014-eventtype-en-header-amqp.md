# ADR-014: eventType en header AMQP, no en JSON body

## Estado
Aceptado — 2026-05-27

## Contexto
Los eventos de dominio (`PagoConfirmadoEvent`, `PagoFallidoEvent`,
`InscripcionConfirmadaEvent`, etc.) son `record` Java que implementan
la interfaz `DomainEvent`. La interfaz declara el método
`eventType(): String` que cada record implementa con un valor literal
("PAGO_CONFIRMADO", "PAGO_FALLIDO", etc.).

Al serializar con `ObjectMapper.writeValueAsString(event)`, Jackson
serializa los **componentes** del record (campos declarados en el
constructor canónico: `eventId`, `aggregateId`, `monto`, `moneda`, etc.)
pero **NO** los métodos adicionales definidos en el cuerpo del record,
aunque implementen una interfaz.

Por tanto, el payload JSON publicado al outbox contiene los datos del
evento pero **no incluye `eventType`** como campo.

Esto fue descubierto durante la implementación de `PagoFlowEndToEndIT`
(Prompt 6): el helper `verificarPayloadPagoConfirmado` falló con NPE al
intentar `json.get("eventType").asText()`.

## Decisión
Mantenemos esta separación intencionalmente y la formalizamos:

- **Headers AMQP**: contienen metadata de enrutamiento — `eventType`,
  `aggregateType`, `aggregateId`, `messageId`.
- **JSON body**: contiene exclusivamente los datos del evento
  (componentes del record).

El `OutboxRelayService` incluye el header `eventType` en cada
`Message` AMQP (`MessageBuilder.setHeader("eventType", ...)`).
El consumidor AMQP inspecciona el header `eventType` para determinar
el tipo de evento antes de deserializar el body.

## Justificación

1. **Patrón estándar de mensajería empresarial** (EIP: "Message Type
   Identifier"): separar metadata enrutable del payload facilita
   ruteo, filtrado y observabilidad sin parsear el body.

2. **Los routing keys de RabbitMQ ya transmiten el tipo** —
   `pago.confirmado`, `pago.fallido`, `inscripcion.confirmada` →
   incluir `eventType` en el body sería redundante.

3. **Spring AMQP usa headers para deserializar tipos** —
   `AbstractJackson2MessageConverter` opera exactamente sobre
   el header `__TypeId__` para elegir la clase concreta.

4. **Reduce el tamaño del payload** — relevante a escala.

5. **Inmutabilidad de headers tras envío** — el header `eventType`
   no puede ser alterado por consumidores intermedios, garantizando
   integridad del tipo de evento.

## Consecuencias positivas

- Consumidores pueden filtrar por `eventType` header sin parsear JSON
- Routing keys + header `eventType` son la única fuente de verdad del tipo
- Verificado en `PagoFlowEndToEndIT`: `props.getHeader("eventType")`
  devuelve `"PAGO_CONFIRMADO"` correctamente

## Consecuencias negativas

- Consumidores que persistan el body como log auditable **sin headers**
  pierden el tipo de evento en el registro
  - **Mitigación**: incluir el header `eventType` en cualquier
    mecanismo de log/auditoría AMQP

- La aserción en tests debe usar headers AMQP, no el JSON body,
  para verificar el tipo de evento

## Alternativa considerada y descartada

**Anotar `eventType()` con `@JsonProperty("eventType")`** para que
aparezca en el body.

Descartada porque:
- Duplica información que ya está en el header → riesgo de divergencia
  si se cambia el método pero no la anotación
- Acopla la implementación del record de dominio a un detalle de
  transporte (serialización JSON)
- El record sería responsable de dos cosas: modelar el evento Y
  formatear su serialización de transporte (SRP violado)

## Referencias

- Hallazgo descubierto en `PagoFlowEndToEndIT.verificarPayloadPagoConfirmado`
- Spring AMQP `MessageBuilder.setHeader("eventType", ...)` en `OutboxRelayService`
- Enterprise Integration Patterns (Hohpe/Woolf): "Message Type Indicator"
- Spring AMQP `__TypeId__` header convention para deserialización
