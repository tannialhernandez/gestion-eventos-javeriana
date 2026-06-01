# Reporte de Enlaces Frontend

Fecha de ejecucion: 2026-06-01T00:26:10.761Z

## Resumen

| Metrica | Resultado |
|---|---:|
| Rutas recorridas | 7 |
| Enlaces encontrados | 8 |
| Enlaces rotos | 0 |

## Criterio de validacion

Un enlace interno es valido si resuelve a una ruta declarada por el SPA:

- `/`
- `/login`
- `/catalogo`
- `/eventos/:eventoId`
- `/inscripciones/:inscripcionId/pago`
- `/confirmacion/:inscripcionId`

Los enlaces externos se documentan como permitidos cuando apuntan fuera del origen del SPA, por ejemplo el checkout simulado.

## Detalle

| Ruta origen | Texto | Href | Tipo | Estado | Observacion |
|---|---|---|---|---|---|
| `/` | Catálogo | `/catalogo` | internal | OK | Matches SPA route inventory. |
| `/catalogo` | Catálogo | `/catalogo` | internal | OK | Matches SPA route inventory. |
| `/eventos/00000000-0000-0000-0000-000000000001` | Catálogo | `/catalogo` | internal | OK | Matches SPA route inventory. |
| `/inscripciones/insc-link-check/pago` | Catálogo | `/catalogo` | internal | OK | Matches SPA route inventory. |
| `/inscripciones/insc-link-check/pago` | Abrir checkout simulado | `https://wiremock.local/checkout` | external | OK | External link documented as allowed. |
| `/confirmacion/insc-link-check` | Catálogo | `/catalogo` | internal | OK | Matches SPA route inventory. |
| `/confirmacion/insc-link-check` | Volver al catálogo | `/catalogo` | internal | OK | Matches SPA route inventory. |
| `/ruta-inexistente` | Catálogo | `/catalogo` | internal | OK | Matches SPA route inventory. |

## Resultado

No se detectaron enlaces internos rotos.
