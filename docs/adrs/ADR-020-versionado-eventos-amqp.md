# ADR-020: Estrategia de Versionado de Eventos AMQP

## Estado
Aceptado — 2026-05-27

## Contexto
`inscription-service` publica eventos de dominio al exchange `eventos.topic` de
RabbitMQ. Los consumidores actuales son `notification-service` (conceptual en
Entrega 3) y `payment-service` (via `pago.confirmado`). A medida que el sistema
evolucione, los contratos de los eventos cambiarán (nuevos campos, renombres,
cambios de tipo), lo que puede romper consumidores en producción si no hay una
estrategia explícita de versionado.

### Opciones evaluadas

| Opción | Descripción | Ventaja | Desventaja |
|--------|-------------|---------|------------|
| **A** | Routing key versionada siempre (`inscripcion.confirmada.v1`) | Simple de enrutar | Requiere un nuevo binding por versión |
| **B** | Header `x-schema-version` + routing key estable | Consumidores filtran sin regex | Requiere leer headers en cada mensaje |
| **C** | Campo `schemaVersion` en payload JSON | Funciona incluso sin headers | No permite filtrado antes de deserializar |
| **D (Híbrido)** | Routing key estable + header + campo JSON | Máxima flexibilidad y retro-compatibilidad | Información redundante (documentada) |

## Decisión
Adoptamos la **estrategia híbrida D**:

1. **Routing key estable** para v1: `inscripcion.creada`, `inscripcion.confirmada`,
   `inscripcion.expirada`. Los consumidores se suscriben una sola vez.

2. **Header AMQP `x-schema-version: v1`** en cada mensaje. Permite filtrado eficiente
   a nivel de broker sin deserializar el payload. Con v2 se puede configurar un
   dead-letter o binding alternativo basado en este header.

3. **Campo `schemaVersion: "v1"` en payload JSON** (ya existe en `PayloadEventoDominio`).
   Garantiza que consumidores que persistan solo el body (ej. audit log) tengan
   la versión disponible sin depender de headers.

### Protocolo de evolución hacia v2

Cuando un cambio sea **breaking** (eliminar campo obligatorio, cambiar tipo):

```
v1 activo: routing key = inscripcion.confirmada
v2 lanzado:
  routing key = inscripcion.confirmada.v2
  header x-schema-version: v2
  campo schemaVersion: "v2"

Período de gracia (≥ 1 release):
  v1 Y v2 se publican simultáneamente
  Consumidores migran a v2 gradualmente

Deprecación v1:
  Anunciar con mínimo 30 días de anticipación
  Registrar en CHANGELOG.md del servicio
```

Cambios **no-breaking** (nuevos campos opcionales, `@JsonInclude(NON_NULL)`) NO
requieren nueva versión; solo actualizar el AsyncAPI.

## Justificación

- **Routing key estable** reduce la fricción operacional: un binding por evento basta
  para todo el ciclo de vida v1. Compatibilidad con RabbitMQ sin plugins adicionales.

- **Header `x-schema-version`** sigue la convención de CloudEvents y Apache Kafka
  headers para metadata de evento, facilitando futuras migraciones a Kafka/Pulsar.

- **Campo JSON** cumple el contrato de ADR-014 (eventType en header, datos en body)
  sin romperlo: `schemaVersion` es metadata del mensaje, no del dominio.

## Consecuencias positivas
- Consumidores pueden migrar a v2 de forma independiente (desacoplamiento temporal).
- Audit logs contienen la versión sin depender de headers AMQP (Ley 1581 Art. 17).
- El AsyncAPI 3.0 cataloga formalmente cada versión como canal separado.
- Elimina O-04 de la auditoría Prompt 8 (versionado sin documentar).

## Consecuencias negativas
- Redundancia: la versión aparece en tres lugares (routing key implícita, header, payload).
  Mitigación: documentado explícitamente en este ADR para evitar confusión.

## Eventos en catálogo (v1)

| Evento | Routing key | Estado |
|--------|-------------|--------|
| `INSCRIPCION_CREADA` | `inscripcion.creada` | ✅ Implementado Prompt 15 |
| `INSCRIPCION_CONFIRMADA` | `inscripcion.confirmada` | ✅ Implementado Prompt 9 |
| `INSCRIPCION_EXPIRADA` | `inscripcion.expirada` | ✅ Implementado Prompt 9 |

## Referencias
- O-04 (auditoría Prompt 8): eventos sin versionado formal
- ADR-014: eventType en header AMQP
- AsyncAPI spec: `docs/asyncapi/inscription-service.yml`
- CloudEvents specification (formato de referencia)
