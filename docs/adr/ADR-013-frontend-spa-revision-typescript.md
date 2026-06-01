# ADR-013 v1.1 - Frontend SPA con React, Vite y TypeScript Strict

## Estado

Aceptada.

## Fecha

2026-05-31.

## Contexto

ADR-013 declaraba el frontend del proyecto como SPA en JavaScript. Durante la implementacion de Entrega 3, el cliente web evoluciono a React + Vite + TypeScript en modo estricto para reducir defectos de integracion con los tres microservicios y mejorar la mantenibilidad exigida por RNF-10.

El flujo critico implementado es:

```text
login real minimo -> catalogo -> detalle -> inscripcion -> pago -> confirmacion
```

El SPA consume contratos HTTP con tipos explicitos para eventos, tarifas, inscripciones, pagos, autenticacion y errores de degradacion controlada.

## Decision

Se revisa ADR-013 a version 1.1 y se adopta formalmente TypeScript strict mode para el SPA.

La decision reemplaza la declaracion original "JavaScript" por:

- React + Vite + TypeScript.
- `strict`, `noUnusedLocals` y `noUnusedParameters` activos.
- Tipos compartidos por capa en `src/entities`, `src/features`, `src/services` y `src/shared`.
- Lint reproducible mediante `npm run lint`.
- Build productivo reproducible mediante `npm run build`.

## Justificacion Tecnica

TypeScript se adopto porque el frontend ya no es una maqueta aislada: orquesta JWT, headers de correlacion, errores tipados, Circuit Breaker UX, catalogo, inscripcion y pago. En ese contexto, JavaScript sin tipos aumenta el riesgo de errores silenciosos en payloads y estados de sesion.

Beneficios concretos:

- Contratos mas claros para DTOs de `event-service`, `inscription-service`, `payment-service` y `auth-service-stub`.
- Validacion temprana de cambios en `AuthSession`, `AppError`, `ServiceUnavailableError` y respuestas de pago.
- Menor riesgo de romper el flujo E2E al cambiar formularios o adaptadores HTTP.
- Mejor alineacion con RNF-10 de mantenibilidad.

## Evidencia Empirica

Verificacion local al 2026-05-31:

| Evidencia | Resultado |
|---|---|
| Archivos TypeScript/TSX validados | 55 |
| `npm run lint` | OK, 0 errores |
| `npm run build` | OK |
| `npm run smoke:e2e` | OK, login real -> pago confirmado |
| `npm run screenshots:e2e` | OK, evidencia visual regenerada |

## Consecuencias

Positivas:

- El frontend queda mas defendible como artefacto de Entrega 3.
- Los contratos de integracion quedan expresados en codigo.
- Los errores de auth, red, negocio y degradacion controlada tienen discriminadores tipados.

Costos:

- Se requiere mantener tipos actualizados cuando evolucionen los DTOs backend.
- El equipo debe seguir ejecutando `npm run lint` y `npm run build` antes de entregar.

## Relacion con SAD/SRS

- SAD v3.0: actualiza la tecnologia del SPA sin cambiar el estilo arquitectonico por capas.
- SRS v1.5 RNF-10: fortalece mantenibilidad y verificabilidad.
- SRS v1.5 RNF-08: reduce errores de manejo de token y sesion.
- Prompt 26.5: se mantiene compatible con `auth-service-stub` y `sessionStorage`.

