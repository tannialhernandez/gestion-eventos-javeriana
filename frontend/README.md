# Frontend SPA - Gestion de Eventos Academicos

SPA React + Vite + TypeScript para el flujo critico de Entrega 3:

1. Login real mínimo contra `auth-service-stub` con JWT RS256.
2. Catalogo publico de eventos desde `event-service`.
3. Detalle de evento y seleccion de tarifa.
4. Creacion de inscripcion en `inscription-service`.
5. Confirmacion de pago via webhook firmado hacia `payment-service`.
6. Pantalla de confirmacion.

## Arquitectura

La estructura sigue capas livianas y barrels para mantener separadas rutas, features, entidades, servicios HTTP y utilidades compartidas:

```text
src/
  app/                 # rutas, layout protegido y shell principal
  components/          # ErrorBoundary y UX de degradacion controlada
  entities/            # tipos de dominio usados por el SPA
  features/            # pantallas y estado por caso de uso
  hooks/               # estado de red y salud de servicios
  lib/                 # errores tipados, interceptores, retry y service health
  services/            # clientes HTTP por microservicio
  shared/api/          # Axios, JWT storage, correlation-id y errores
  shared/config/       # variables de entorno
  shared/lib/          # crypto, formato y sanitizacion
  shared/ui/           # componentes UI reutilizables
```

## Configuracion

Valores por defecto para desarrollo local:

```bash
VITE_EVENT_API_URL=/event-api/api/v1
VITE_AUTH_API_URL=/auth-api/api/v1
VITE_INSCRIPTION_API_URL=/inscription-api/api/v1
VITE_PAYMENT_API_URL=/payment-api/api/v1
VITE_PAYMENT_WEBHOOK_SECRET=change-me-before-production
```

El proxy de Vite enruta:

- `/auth-api` -> `http://localhost:8081`
- `/event-api` -> `http://localhost:8082`
- `/inscription-api` -> `http://localhost:8083`
- `/payment-api` -> `http://localhost:8084`

## Ejecucion

Levantar backend E2E y datos de prueba:

```bash
make -C ../load-tests seed
```

Levantar el SPA:

```bash
npm run dev -- --host 127.0.0.1
```

Abrir:

```text
http://127.0.0.1:3000/
```

## Verificacion

```bash
npm run lint
npm run build
npm run smoke:e2e
npm run screenshots:e2e
```

`npm run smoke:e2e` valida el flujo backend real a traves de Vite/proxies: login en `auth-service-stub`, catalogo, tarifa, inscripcion y webhook de pago firmado.

`npm run screenshots:e2e` abre Chrome headless, recorre el flujo visual y guarda capturas en `evidence/screenshots/`.

La ultima captura valida la degradacion controlada: el script intercepta una inscripcion con `HTTP 503` y `Retry-After`, y verifica que el SPA muestre banner global, cuenta regresiva y error contextual.

## Testing

La suite automatizada del Prompt 27 agrega tres niveles:

- `npm run test`: Vitest + React Testing Library para componentes, paginas y contexto de autenticacion.
- `npm run test:coverage`: reporte V8 con umbral global de 70%.
- `npm run test:e2e`: Playwright Chromium con 5 specs de navegador.

Los mocks HTTP viven en `src/test/mocks/` y usan MSW. Los E2E interceptan APIs con `page.route` para mantener escenarios deterministas: login, flujo catalogo-inscripcion-pago, sesion expirada, cupo agotado y Circuit Breaker UI.

Guia completa: `TESTING.md`.

## Accesibilidad

La auditoria WCAG 2.1 AA del Prompt 28 agrega:

- `eslint-plugin-jsx-a11y` dentro de `npm run lint`.
- `vitest-axe` dentro de `npm run test`.
- `@axe-core/playwright` con `npm run test:a11y` y tambien dentro de `npm run test:e2e`.

Evidencia reproducible:

```text
test-results/accessibility/*.json
test-results/evidence/06-a11y-*.png
../docs/evidencia-accesibilidad-frontend.md
```

Guia completa: `ACCESSIBILITY.md`.

## Seguridad

El SPA agrega `X-Correlation-Id` a cada request, guarda el JWT con expiracion en `sessionStorage` y evita renderizado HTML peligroso. El navegador ya no firma tokens: consume `POST /api/v1/auth/login` del `auth-service-stub`. Ese stub es una decision documentada para Entrega 3 y debe reemplazarse por Azure AD, Keycloak u otro IdP antes de produccion.

## Alcance

Quedan fuera de Entrega 3 las pantallas completas de participante, organizador, administrador, refresh token y auth-service productivo. Estan declaradas en `../TECH_DEBT.md` y en `../docs/alcance-y-decisiones-de-mocks.md`.
