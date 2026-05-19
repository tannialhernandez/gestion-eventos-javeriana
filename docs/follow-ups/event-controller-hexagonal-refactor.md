# Refactor hexagonal de EventoController + endpoints admin

## Estado
PENDIENTE — bloqueado para el Prompt 2 del plan E3.

## Origen
Stash `stash@{1}: "payment-outbox wip"` rescatado y respaldado en
`docs/follow-ups/event-controller-admin-endpoints.patch`.

## Problema
`EventoController` inyecta directamente `EventoRepository` (puerto de salida),
violando ADR-01 (Arquitectura Hexagonal pura). El stash añade 4 endpoints
admin que mantienen y profundizan esta violación.

## Endpoints involucrados

| Endpoint | Funcionalidad | RF asociado |
|---|---|---|
| `GET /admin/todos` | Lista eventos en BORRADOR | RF-001, RF-010 |
| `GET /admin/pendientes` | Lista eventos en PENDIENTE_PUBLICACION | RF-001 |
| `POST /{id}/enviar-revision` | Transición BORRADOR → PENDIENTE_PUBLICACION | RF-001 |
| `DELETE /{id}` | Eliminación de evento (BORRADOR únicamente) | RF-001 |

## Acción correctiva planificada

Antes de aplicar el patch del stash, crear los siguientes casos de uso en
`event-service`:

### 1. `ListarEventosPorEstadoUseCase` + `ListarEventosPorEstadoService`
- Recibe `EstadoEvento` como parámetro.
- Delega al puerto out `EventoRepository`.

### 2. `EnviarEventoARevisionUseCase` + `EnviarEventoARevisionService`
- Carga agregado, invoca `evento.enviarARevision()`, persiste, publica
  evento de dominio `EventoEnviadoARevisionEvent` vía Outbox.

### 3. `EliminarEventoUseCase` + `EliminarEventoService`
- Valida que el estado sea `BORRADOR` (regla de dominio en el agregado).
- Soft delete (cambiar estado a `ELIMINADO`, no DELETE físico).
- Publica `EventoEliminadoEvent` vía Outbox.

Una vez creados los casos de uso, refactorizar `EventoController`:
- Inyectar los UseCases, **NO** `EventoRepository`.
- Mapear DTOs de entrada/salida en `infrastructure/web` (no exponer entidades).
- Aplicar el patch del stash con los ajustes correspondientes.

## Criterios de aceptación

- [ ] Ningún `@RestController` del proyecto inyecta repositorios o entidades JPA.
- [ ] Los 4 endpoints admin operativos.
- [ ] Tests unitarios de los 3 nuevos casos de uso (cobertura >85%).
- [ ] Tests de integración de los endpoints admin (con MockMvc).
- [ ] Commit final descarta el stash y elimina el patch del disco (ya integrado).

## Trazabilidad

- ADR-01: Arquitectura Hexagonal pura.
- ADR-013: Patrón State en agregado `Evento`.
- RF-001: Crear y configurar evento académico.
- RF-010: Catálogo de eventos.
