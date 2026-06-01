# Accesibilidad

El SPA valida accesibilidad sobre WCAG 2.1 A/AA con herramientas de desarrollo, sin dependencias runtime.

## Comandos

```bash
npm run lint
npm run test
npm run test:a11y
npm run test:e2e
```

`npm run lint` ejecuta `eslint-plugin-jsx-a11y`.  
`npm run test` incluye pruebas `vitest-axe` sobre componentes y pantallas.  
`npm run test:a11y` ejecuta axe en Chromium con Playwright sobre login, catalogo, detalle y pago.

## Evidencia Generada

```text
test-results/accessibility/*.json
test-results/evidence/06-a11y-*.png
playwright-report/index.html
coverage/index.html
```

## Politicas de Implementacion

- Todo control interactivo debe tener nombre accesible visible o `aria-label`.
- Los mensajes de error deben usar `role="alert"` cuando requieren atencion inmediata.
- Los mensajes de estado no bloqueantes deben usar `role="status"` y `aria-live`.
- Los iconos decorativos deben tener `aria-hidden` o estar dentro de controles con texto.
- No usar `dangerouslySetInnerHTML`; el linter local lo bloquea.
- No depender solo del color para comunicar estados.

## Checklist Manual Antes de Entrega

1. Recorrer login, catalogo, detalle, pago y confirmacion solo con teclado.
2. Verificar foco visible en todos los controles.
3. Probar zoom 200% sin perdida funcional.
4. Probar ancho movil cercano a 320 px sin scroll horizontal global.
5. Confirmar que errores de cupo, red y Circuit Breaker anuncian texto accionable.
