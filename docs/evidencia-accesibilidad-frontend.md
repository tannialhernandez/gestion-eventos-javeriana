# Evidencia de Accesibilidad Frontend WCAG 2.1 AA

Fecha de ejecucion: 2026-05-31  
Aplicacion: SPA React + Vite + TypeScript de la Plataforma de Gestion de Eventos Academicos

## Resumen Ejecutivo

Se implemento una auditoria de accesibilidad en tres niveles: lint estatico con `eslint-plugin-jsx-a11y`, pruebas unitarias con `vitest-axe` y auditoria E2E en Chromium con `@axe-core/playwright`.

Resultado: las cuatro paginas criticas auditadas por axe en navegador real reportan **0 violaciones WCAG 2.1 A/AA**:

| Pantalla | Evidencia JSON | Captura | Violaciones |
|---|---|---|---:|
| Login institucional | `frontend/test-results/accessibility/login.json` | `frontend/test-results/evidence/06-a11y-login.png` | 0 |
| Catalogo | `frontend/test-results/accessibility/catalogo.json` | `frontend/test-results/evidence/06-a11y-catalogo.png` | 0 |
| Detalle e inscripcion | `frontend/test-results/accessibility/detalle-evento.json` | `frontend/test-results/evidence/06-a11y-detalle-evento.png` | 0 |
| Pago simulado | `frontend/test-results/accessibility/pago.json` | `frontend/test-results/evidence/06-a11y-pago.png` | 0 |

## Automatizacion Implementada

| Nivel | Herramienta | Comando | Cobertura |
|---|---|---|---|
| Linter compile-time | `eslint-plugin-jsx-a11y` | `npm run lint` | Reglas JSX accesibles sobre TS/TSX |
| Componentes | `vitest-axe` | `npm run test` | Login form, catalogo, detalle, pago, degradacion |
| E2E navegador | `@axe-core/playwright` | `npm run test:a11y` | Paginas completas en Chromium |
| Regresion E2E | Playwright | `npm run test:e2e` | 11 specs, incluye auditoria a11y |

## Hallazgos Corregidos

| Hallazgo | Correccion | Archivo |
|---|---|---|
| Error de login no anunciado a tecnologias asistivas | Se agrego `role="alert"` | `frontend/src/features/auth/LoginPage.tsx` |
| Filtros de catalogo dependian de placeholder/texto visible no asociado | Se agregaron `aria-label` a busqueda, tipo y modalidad | `frontend/src/features/catalog/CatalogPage.tsx` |
| Paneles de inscripcion/pago usaban `aside` dentro del landmark principal | Se cambiaron a `section` con nombre accesible | `frontend/src/features/events/EventDetailPage.tsx`, `frontend/src/features/checkout/PaymentPage.tsx` |
| `axe-core` + `happy-dom` tenia incompatibilidad con `Node.prototype.isConnected` | Se agrego shim de test sin impacto runtime | `frontend/vitest.setup.ts` |

## Resultados de Ejecucion

```text
npm run lint
OK - Validated 69 TypeScript source files.

npm run test
OK - 11 files, 48 tests passed.

npm run test:coverage
OK - Statements 88.06%, Branches 82.1%, Functions 73.33%, Lines 88.06%.

npm run test:a11y
OK - 4 Playwright axe tests passed, 0 violations.

npm run test:e2e
OK - 11 Playwright tests passed.

npm run build
OK - Vite production build generated.

npm run smoke:e2e
OK - login, catalogo, inscripcion y pago CONFIRMADO contra backend real.
```

## Checklist Manual WCAG 2.1 AA

| Criterio | Resultado | Evidencia / observacion |
|---|---|---|
| 1.1.1 Contenido no textual | Cumple | Logo institucional con `alt`; imagen decorativa del shell con `alt=""`. |
| 1.3.1 Informacion y relaciones | Cumple | Headings, `main`, `nav`, `section`, `dl`, labels y roles semanticos. |
| 1.3.2 Secuencia con sentido | Cumple | DOM sigue login -> catalogo -> detalle -> pago; navegacion por rutas sin saltos visuales. |
| 1.4.1 Uso del color | Cumple | Estados se comunican con texto, no solo color. |
| 1.4.3 Contraste minimo | Cumple | Axe no reporta contrast violations en Chromium. |
| 1.4.4 Cambio de tamano de texto | Cumple | Layout usa unidades fluidas y no fija alturas criticas en controles de texto. |
| 1.4.10 Reflow | Cumple | Grillas y paneles usan breakpoints responsivos; sin dependencia de layout horizontal fijo. |
| 1.4.11 Contraste no textual | Cumple | Controles tienen bordes/focus visibles; focus amarillo institucional. |
| 2.1.1 Teclado | Cumple | Login, filtros, botones y enlaces son controles nativos navegables por teclado. |
| 2.4.3 Orden de foco | Cumple | Orden DOM coincide con orden visual en pantallas criticas. |
| 2.4.4 Proposito de enlaces | Cumple | Enlaces/botones tienen texto accionable o `aria-label`. |
| 2.4.6 Encabezados y etiquetas | Cumple | Formularios con labels; filtros con nombre accesible. |
| 2.4.7 Foco visible | Cumple | CSS mantiene foco visible en controles interactivos. |
| 3.3.1 Identificacion de errores | Cumple | Errores contextuales usan `role="alert"` y texto especifico. |
| 3.3.2 Etiquetas o instrucciones | Cumple | Email, contrasena y tarifa tienen labels; credenciales demo se explican en texto auxiliar. |
| 4.1.2 Nombre, funcion, valor | Cumple | Radio group de usuarios demo, botones, selects y banners exponen rol/nombre/estado. |
| 4.1.3 Mensajes de estado | Cumple | Banners de red/degradacion usan `role="status"` y `aria-live`. |

## Limitaciones Reconocidas

- Axe automatiza una parte importante de WCAG, pero no reemplaza una prueba formal con usuarios reales ni lector de pantalla en ambiente de laboratorio.
- La validacion manual se hizo sobre el flujo critico de Entrega 3; pantallas futuras de organizador/administrador deben entrar a esta misma matriz cuando se implementen.
- El reporte Playwright queda en `frontend/playwright-report/index.html`; los JSON de axe son evidencia reproducible, no artefactos de produccion.

## Comando Reproducible

```bash
cd frontend
npm install --legacy-peer-deps
npm run lint
npm run test
npm run test:coverage
npm run test:a11y
npm run test:e2e
npm run build
npm run smoke:e2e
```
