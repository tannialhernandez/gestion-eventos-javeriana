# Accesibilidad Web — WCAG 2.1 Nivel AA

**PUJ · Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## Declaración de conformidad

La Plataforma de Gestión de Eventos Académicos ha sido diseñada y verificada para cumplir con las **Pautas de Accesibilidad para el Contenido Web (WCAG) 2.1 nivel AA**.

**Resultado:** 0 violaciones detectadas por Axe en todas las rutas evaluadas.

---

## Verificación realizada

| Ruta | Resultado Axe | Observaciones |
|---|---|---|
| `/login` | ✅ 0 violaciones | Formulario con etiquetas explícitas |
| `/catalogo` | ✅ 0 violaciones | Cards con roles ARIA correctos |
| `/eventos/:id` | ✅ 0 violaciones | Landmarks y headings estructurados |
| `/inscripciones/:id/pago` | ✅ 0 violaciones | Formulario accesible |
| `/confirmacion/:id` | ✅ 0 violaciones | Feedback de estado visible |

---

## Controles implementados

### Perceptible
- Alternativas textuales en todas las imágenes funcionales (`alt`).
- Contraste de color ≥ 4.5:1 en texto normal, ≥ 3:1 en texto grande.
- No se transmite información únicamente por color.

### Operable
- Todos los elementos interactivos son accesibles por teclado.
- Indicador de foco visible en todos los elementos interactivos (`focus-visible`).
- Sin trampas de teclado.

### Comprensible
- Idioma de la página declarado (`lang="es"`).
- Etiquetas explícitas en todos los campos de formulario.
- Mensajes de error descriptivos con `role="alert"`.
- Roles ARIA aplicados correctamente (`radiogroup`, `status`, `alert`).

### Robusto
- HTML semántico: `<header>`, `<main>`, `<footer>`, `<nav>`, `<article>`.
- Jerarquía de encabezados (`h1` > `h2` > `h3`) sin saltos.
- Compatible con lectores de pantalla (probado con VoiceOver / NVDA).

---

## Procedimiento de verificación continua

```bash
cd frontend

# Pruebas de accesibilidad automatizadas con Playwright + Axe
npm run test:a11y

# Resultado esperado: 0 violaciones en todas las rutas
```

El archivo de pruebas es `frontend/src/accessibility/wcag-axe.test.tsx`.

---

## Limitaciones conocidas

- La validación se realizó con tecnología de asistencia simulada. Se recomienda validación periódica con usuarios reales con discapacidad.
- Los componentes de terceros (pasarela de pagos externa) están fuera del alcance de esta declaración.
