# Testing Frontend

Esta guia cubre los tres niveles de pruebas del SPA:

- Unitarias: Vitest + React Testing Library.
- Integracion: Vitest + MSW para clientes HTTP, errores tipados y retry.
- E2E: Playwright Chromium sobre navegador real.

## Instalacion

Las herramientas son `devDependencies`; no agregan peso al runtime productivo.

```bash
npm install
```

Este frontend usa un `.npmrc` local para evitar el bloqueo DNS observado contra `registry.npmjs.org`:

```text
registry=https://registry.yarnpkg.com/
@playwright:registry=https://registry.yarnpkg.com/
```

Si `npm install` vuelve a fallar con `ENOTFOUND registry.npmjs.org`, verifica que `frontend/.npmrc` exista y que `npm config get @playwright:registry` devuelva `https://registry.yarnpkg.com/`.

## Comandos

```bash
npm run test
npm run test:watch
npm run test:coverage
npm run test:a11y
npm run test:e2e
npm run test:e2e:headed
npm run test:e2e:ui
```

`npm run test:coverage` genera:

```text
coverage/index.html
coverage/coverage-summary.json
```

`npm run test:e2e` genera:

```text
playwright-report/index.html
test-results/
test-results/evidence/
```

`npm run test:a11y` ejecuta solo la auditoria axe de Playwright y genera:

```text
test-results/accessibility/*.json
test-results/evidence/06-a11y-*.png
```

## Estructura

```text
src/**/*.test.tsx       # Componentes y paginas React
src/lib/**/*.test.ts    # Integracion de errores, retry y clientes HTTP
src/test/mocks/         # MSW handlers compartidos
src/test/test-utils.tsx # render con AuthProvider + Router
e2e/*.spec.ts           # Flujos Playwright
```

## Politicas de escritura

- Los tests de componentes deben verificar comportamiento visible y accesible, no clases CSS internas.
- Las pruebas de HTTP deben usar MSW y no depender del backend real.
- Los E2E de Playwright interceptan las APIs con `page.route` para validar UX de navegador de forma deterministica.
- El smoke manual `npm run smoke:e2e` se mantiene como evidencia complementaria contra backend real.
- Las auditorias de accesibilidad deben fallar si axe reporta violaciones WCAG A/AA en paginas criticas.

## Cobertura objetivo

- `src/features/`: minimo 70% statements.
- `src/lib/`: minimo 70% branches.
- Logica critica: `AuthContext`, `withRetry`, `parseHttpError` cubierta con pruebas directas.

## Debug

Para depurar E2E:

```bash
npm run test:e2e:headed
npm run test:e2e:ui
```

Para inspeccionar una falla:

```bash
npm run test:e2e -- --trace on
```

El reporte HTML de Playwright queda en `playwright-report/index.html`.
