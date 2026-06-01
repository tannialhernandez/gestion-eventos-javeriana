# Evidencia de Testing Frontend - Prompt 27

Proyecto: Plataforma de Gestion de Eventos Academicos, Pontificia Universidad Javeriana.

Fecha de implementacion local: 2026-05-31, zona `America/Bogota`.

## Resumen Ejecutivo

Se implemento una suite automatizada de testing para el SPA React + Vite + TypeScript, alineada con RNF-10 del SRS v1.5:

| Nivel | Herramienta | Alcance implementado |
|---|---|---|
| Unitario | Vitest + React Testing Library | AuthContext, LoginPage, CatalogPage, EventDetailPage, PaymentPage, ConfirmationPage, DegradedServiceBanner |
| Integracion | Vitest + MSW | HTTP client, errores tipados, Retry-After, withRetry, parseHttpError |
| E2E | Playwright Chromium | Login, flujo critico, sesion expirada, cupo agotado, Circuit Breaker UI |

## Estado de Ejecucion

El bloqueo `ENOTFOUND registry.npmjs.org` fue resuelto con un `.npmrc` local en `frontend/`, usando `https://registry.yarnpkg.com/` como mirror publico tambien para `@playwright`.

Verificaciones ejecutadas despues de agregar la suite:

| Comando | Resultado |
|---|---|
| `npm run lint` | OK, 68 archivos TypeScript parseados |
| `npm run build` | OK, Vite build productivo generado |
| `npm run smoke:e2e` | OK, login real -> catalogo -> inscripcion -> pago confirmado |
| `npm run test` | OK, 10 archivos, 43 tests |
| `npm run test:coverage` | OK, umbrales globales cumplidos |
| `npm run test:e2e` | OK, 7 tests Playwright Chromium |

Ultima salida del smoke real:

```json
{
  "login": "diego.participante@javeriana.edu.co",
  "catalogo": "00000000-0000-0000-0000-000000000026",
  "tarifa": "00000000-0000-0000-0001-000000000026",
  "inscripcion": "1c8b0e37-c973-424c-8fb0-4adb3a69849b",
  "pago": {
    "resultado": "CONFIRMADO"
  }
}
```

## Comandos Reproducibles

Desde `frontend/`:

```bash
npm install
npm run test
npm run test:coverage
npm run test:e2e
npm run lint
npm run build
npm run smoke:e2e
```

## Artefactos Esperados

| Artefacto | Ruta |
|---|---|
| Reporte HTML de cobertura | `frontend/coverage/index.html` |
| Resumen JSON de cobertura | `frontend/coverage/coverage-summary.json` |
| Reporte HTML Playwright | `frontend/playwright-report/index.html` |
| Capturas E2E Playwright | `frontend/test-results/evidence/*.png` |

## Tabla de Cobertura

| Modulo | Statements | Branches | Functions | Lines | Estado |
|---|---:|---:|---:|---:|---|
| Global | 87.76% | 80.35% | 73.33% | 87.76% | Cumple |
| `src/features/auth` | 93.46% | 86.11% | 70.00% | 93.46% | Cumple |
| `src/features/catalog` | 98.47% | 91.66% | 28.57% | 98.47% | Statements/branches cumplen |
| `src/features/checkout` | 96.82% | 80.00% | 71.42% | 96.82% | Cumple |
| `src/features/events` | 94.76% | 71.42% | 33.33% | 94.76% | Statements/branches cumplen |
| `src/lib/errors` | 98.15% | 86.00% | 100.00% | 98.15% | Cumple |
| `src/lib/retry` | 100.00% | 100.00% | 100.00% | 100.00% | Cumple |
| `src/lib/api` | 100.00% | 80.00% | 100.00% | 100.00% | Cumple |

## Tests Implementados

| Archivo | Proposito |
|---|---|
| `frontend/src/features/auth/AuthContext.test.tsx` | Sesion inicial, login, restauracion, logout y roles |
| `frontend/src/features/auth/LoginPage.test.tsx` | Branding Javeriana, validaciones, login exitoso y credenciales invalidas |
| `frontend/src/features/catalog/CatalogPage.test.tsx` | Skeleton, lista, estado vacio, navegacion y cupos |
| `frontend/src/features/events/EventDetailPage.test.tsx` | Carga de evento, inscripcion, estado procesando y 409 sin cupos |
| `frontend/src/features/checkout/PaymentPage.test.tsx` | Resumen, confirmacion de pago y error contextual |
| `frontend/src/features/checkout/ConfirmationPage.test.tsx` | Confirmacion con y sin snapshot local |
| `frontend/src/components/degradation/DegradedServiceBanner.test.tsx` | Estado nominal, degradado y cuenta regresiva |
| `frontend/src/lib/api/api-client.test.ts` | Headers Authorization/correlation y transformacion de errores |
| `frontend/src/lib/errors/parseHttpError.test.ts` | Clasificacion 503, 401, 409, network y Retry-After |
| `frontend/src/lib/retry/withRetry.test.ts` | Backoff, Retry-After, max attempts y no retry en 4xx |

## Specs Playwright

| Spec | Flujo |
|---|---|
| `frontend/e2e/01-login-flow.spec.ts` | Login valido, login invalido y logout |
| `frontend/e2e/02-catalogo-inscripcion-pago.spec.ts` | Catalogo -> detalle -> inscripcion -> pago -> confirmacion |
| `frontend/e2e/03-sesion-expirada.spec.ts` | Token expirado en `sessionStorage` -> redirect a login |
| `frontend/e2e/04-cupo-agotado.spec.ts` | HTTP 409 -> mensaje contextual de cupo agotado |
| `frontend/e2e/05-circuit-breaker-ui.spec.ts` | HTTP 503 + `Retry-After` -> banner degradado + cuenta regresiva |

## Interpretacion

La suite cubre los puntos criticos de mantenibilidad y regresion del frontend sin introducir dependencias runtime. El smoke E2E manual se conserva para validar backend real; los tests automatizados usan mocks controlados para aislar comportamiento de UI, errores tipados y navegacion.

Reportes generados:

- Cobertura: `frontend/coverage/index.html`.
- Playwright: `frontend/playwright-report/index.html`.
- Capturas E2E: `frontend/test-results/evidence/*.png`.

Nota: `npm install` reporto 8 vulnerabilidades transitivas. No bloquean la evidencia funcional, pero se recomienda revisarlas con `npm audit` antes de cierre final.
