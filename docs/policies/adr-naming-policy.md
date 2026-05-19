# Política de Nomenclatura de ADRs

**Versión:** 1.0  
**Fecha:** 2026-05-18  
**Alcance:** Todos los archivos del repositorio `gestion-eventos-javeriana`

---

## Regla única

**Formato obligatorio:** `ADR-NNN` (3 dígitos con padding de ceros a la izquierda).

```
✅ Correcto:  ADR-008, ADR-012, ADR-018, ADR-019
❌ Incorrecto: ADR-8, ADR-08, ADR-11, ADR-18
```

## Fuente de verdad

`docs/adrs/README.md` es el **único índice maestro** de ADRs del proyecto.

- Antes de citar un ADR en cualquier documento, verificar que existe en el índice.
- Si el ADR no existe, crearlo primero (ver convención en `docs/adrs/_TEMPLATE.md`).
- Si el ADR existe bajo un número diferente al que querías usar, usar el número del índice.

## Convención de citación

Citar siempre número Y descripción breve entre paréntesis:

```
✅ ADR-012 (Control de concurrencia con bloqueo pesimista)
✅ ADR-008 (Patrón Transactional Outbox)
❌ ADR-12 (sin descripción)
❌ ADR-08 (Outbox Pattern)  ← número incorrecto
```

## Razón

El proyecto acumuló dos esquemas de numeración paralelos durante las Entregas 1 y 2:
- Schema A (legado): `ADR-NN` sin padding, usado en SAD v1.0/v2.0 y documentos de runtime.
- Schema B (MADR estándar): `ADR-NNN` con padding de 3 dígitos.

La colisión `ADR-18 = Circuit Breaker` (schema A) vs. `ADR-018 = ShedLock` (schema B)
fue identificada como Incidente 002 (ver `docs/follow-ups/incidents.md`).

A partir del Commit 2.5, **solo Schema B es válido**.

## Proceso para proponer un nuevo ADR

1. Consultar `docs/adrs/README.md` para el siguiente número disponible.
2. Copiar `docs/adrs/_TEMPLATE.md` → `docs/adrs/ADR-NNN-slug-descriptivo.md`.
3. Completar todas las secciones (Contexto, Decisión, Alternativas, Consecuencias, Trazabilidad).
4. Añadir la entrada al índice `docs/adrs/README.md`.
5. Estado inicial: `Propuesta`. Cambiar a `Aceptada` tras revisión.

## Política de inmutabilidad

Un ADR aceptado **no se modifica**. Si la decisión cambia:
- Crear un nuevo ADR con `Estado: Aceptada` y `Supersedes: ADR-NNN`.
- Marcar el ADR original con `Estado: Superseded por ADR-MMM`.
