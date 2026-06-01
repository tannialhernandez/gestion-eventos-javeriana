# Inventario de Rutas Frontend

Fecha de revision: 2026-05-31  
Fuente: `frontend/src/app/App.tsx`

## Rutas implementadas

| Ruta | Protegida | Componente | Estado | Observacion |
|---|---|---|---|---|
| `/login` | No | `LoginPage` | Implementada | Login institucional contra `auth-service-stub`. |
| `/` | Si | `Navigate` | Implementada | Redirige a `/catalogo` dentro de `AppShell`. |
| `/catalogo` | Si | `CatalogPage` | Implementada | Catalogo publico consumido desde `event-service`; requiere sesion en el SPA. |
| `/eventos/:eventoId` | Si | `EventDetailPage` | Implementada | Detalle de evento, tarifas, inscripcion y manejo de cupo/error. |
| `/inscripciones/:inscripcionId/pago` | Si | `PaymentPage` | Implementada | Pago simulado desde snapshot local de la inscripcion. |
| `/confirmacion/:inscripcionId` | Si | `ConfirmationPage` | Implementada | Confirmacion posterior al webhook de pago. |
| `*` | Si | `Navigate` | Implementada | Cualquier ruta desconocida redirige a `/catalogo`; no hay pantalla 404 dedicada. |

## Rutas mencionadas en el prompt pero no existentes

| Ruta solicitada | Estado real | Decision documentada |
|---|---|---|
| `/inscripciones/:id` | No implementada | El SPA no tiene bandeja/listado de inscripciones en Entrega 3; se usa `/inscripciones/:inscripcionId/pago`. |
| `/pago/:id` | No implementada | El pago vive bajo el recurso de inscripcion: `/inscripciones/:inscripcionId/pago`. |
| `/perfil` | No implementada | Perfil completo de participante queda fuera del flujo critico y esta registrado en `TECH_DEBT.md` D-015. |
| `/404` | No implementada | La ruta wildcard `*` redirige a `/catalogo`; no hay vista 404 independiente. |

## Estados visuales cubiertos por evidencia

| Ruta | Estados capturados |
|---|---|
| `/login` | Default, credenciales invalidas |
| `/catalogo` | Success, loading/skeleton, empty, error de servicio |
| `/eventos/:eventoId` | Success, loading/skeleton, cupo agotado, error 409, degradacion 503 |
| `/inscripciones/:inscripcionId/pago` | Success, empty sin snapshot, error de servicio |
| `/confirmacion/:inscripcionId` | Success con snapshot, confirmacion generica sin snapshot |
| `/` | Redirect a catalogo |
| `*` | Redirect a catalogo |

## Breakpoints de captura

| Viewport | Dimensiones |
|---|---:|
| Mobile | 375 x 667 |
| Tablet | 768 x 1024 |
| Desktop | 1440 x 900 |
