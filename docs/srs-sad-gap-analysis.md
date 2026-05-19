# Análisis de Brechas SRS ↔ SAD
## Plataforma de Gestión de Eventos Académicos — Pontificia Universidad Javeriana

**Versión:** 1.0 (Entrega 3)  
**Fecha:** 2026-05-18  
**Autora:** Tannia Hernández Rojas  
**Confidencialidad:** Documento interno de equipo — NO se entrega al profesor directamente.  
**Propósito:** Tracking de inconsistencias detectadas para guiar la consolidación final del SAD (Prompt 8).

---

## Convenciones

| Estado | Significado |
|---|---|
| 🔴 Crítico | Afecta credibilidad documental; debe resolverse antes de la entrega |
| 🟡 Moderado | Inconsistencia conocida; resolución agendada |
| 🟢 Resuelto | Corrección aplicada en el commit especificado |

---

## Gap 001 — Colisión de numeración ADR 🔴 CRÍTICO

**Inconsistencia detectada:**
- `docs/comportamiento-runtime-inscripcion-pago.md` línea 468: `ADR-18 = Circuit Breaker Resilience4j`
- `docs/adrs/ADR-018-distributed-locking-outbox-relay.md`: `ADR-018 = ShedLock`
- Mismo número, contenido radicalmente diferente.

**Documentos afectados:**
- `docs/comportamiento-runtime-inscripcion-pago.md` (líneas 325, 462, 464, 467, 468, 469)
- `docs/matriz-trazabilidad.md` (secciones 3-5: ADR-01 a ADR-20 con numeración diferente al índice)
- `docs/adrs/README.md` (índice maestro con schema 3 dígitos)

**Causa raíz:**
Los ADRs en documentos pre-existentes (SRS, SAD v1.0/v2.0) nunca tuvieron numeración formal centralizada. El SAD v1.0 los numeraba ADR-01..ADR-12 de forma secuencial según el orden de redacción. Los documentos de comportamiento en runtime los referenciaron con su propio esquema. El índice `docs/adrs/README.md` creado en Commit 2 usa schema MADR estándar (3 dígitos) con asignaciones de contenido diferentes.

**Resolución propuesta:**
Adoptar schema B (ADR-NNN, 3 dígitos) como estándar único del proyecto. Actualizar todos los documentos existentes para usar el ID del índice maestro. Documentado en Commit 2.5. Ver `docs/policies/adr-naming-policy.md` (creado en Commit 2.5).

**Estado:** 🔴 Crítico — Agendado Commit 2.5 (reconciliación inmediata)

---

## Gap 002 — RN-PAGO-05: regla propuesta sin aprobación formal 🟡 Moderado

**Inconsistencia detectada:**
- `Pago.reembolsarPorExpiracion()` en el código cita `RN-PAGO-05`
- `docs/srs-casos-uso-pendientes.md` §11 la lista como "propuesta — pendiente aprobación"
- No aparece en el SRS v1.0 ni v2.0 como regla formal

**Documento afectado:**
- `payment-service/src/.../domain/model/Pago.java` (javadoc del método)
- `docs/srs-casos-uso-pendientes.md` §11

**Causa raíz:**
La regla surgió durante la implementación del fix crítico (Commit 4). No era preexistente; se formalizó como propuesta en paralelo con el código.

**Resolución propuesta:**
Aprobar RN-PAGO-05 como parte del SRS v3.0 antes de la entrega final. La base documental ya existe en `docs/srs-casos-uso-pendientes.md §11` con trazabilidad completa.

**Estado:** 🟡 Moderado — Aprobación pendiente por el equipo antes de la entrega

---

## Gap 003 — Criterios de aceptación heterogéneos en RFs de payos y notificaciones 🟡 Moderado

**Inconsistencia detectada (cita del feedback del profesor):**
> "Los criterios de aceptación no son homogéneos en todos los módulos."

- RF-22 (webhook): criterio fuerte — `PagoFlowEndToEndIT` con Awaitility 10s, aserciones explícitas
- RF-22c (reembolso tardío): criterio fuerte — `PagoTest` con 5 tests unitarios
- RF-50 (notificaciones): criterio débil — "Email enviado en <10s" sin test E2E que lo verifique
- RF-30 (certificados): criterio débil — "Disponible en <30s" sin test implementado

**Documentos afectados:**
- `docs/matriz-trazabilidad.md` §4 (columna de métricas verificables)
- `docs/trazabilidad-driver-decision.md` §3 (columna "Test que valida")

**Causa raíz:**
Los criterios de los módulos implementados (pagos, inscripción) son fuertes porque se escribieron junto con el código. Los módulos pendientes (notificaciones, certificados) tienen criterios conceptuales sin evidencia.

**Resolución propuesta:**
Para la entrega: marcar los tests pendientes con la clase y el escenario exacto (como se hace en `trazabilidad-driver-decision.md`), de modo que queden como "criterio escrito pero pendiente de implementar" en lugar de ausentes.

**Estado:** 🟡 Moderado — Parcialmente resuelto en `trazabilidad-driver-decision.md`

---

## Gap 004 — Inconsistencia en detección de pago tardío: tiempo vs. estado de inscripción 🟡 Moderado

**Inconsistencia detectada:**
- `docs/comportamiento-runtime-inscripcion-pago.md` §4.3: *"El webhook tardío siempre verifica el estado actual de la inscripción antes de confirmar. Si el estado es `EXPIRADA`, el webhook nunca confirma la inscripción."*
- `payment-service/ProcesarWebhookService.procesarPagoTardio()`: detecta expiración por tiempo transcurrido (`fechaCreacion + 960s`), NO consultando el estado de la inscripción en inscription-service.

**Documentos afectados:**
- `docs/comportamiento-runtime-inscripcion-pago.md` §4.3, §10 (tabla de garantías)
- `payment-service/src/.../application/ProcesarWebhookService.java` (comentario TODO)
- `docs/follow-ups/payment-refund-async.md` (deuda técnica documentada)

**Causa raíz:**
La arquitectura documentada asume que `payment-service` consulta `inscription-service` para saber si la inscripción está EXPIRADA. La implementación actual usa una heurística de tiempo porque la comunicación cross-service sería síncrona (y por tanto, frágil). La deuda fue aceptada conscientemente (ver Commit 4 fix).

**Resolución propuesta:**
Dos opciones (pendiente decisión de diseño):
1. `inscription-service` publica `InscripcionExpiradaEvent` cuando expira; `payment-service` lo consume y almacena localmente el estado (event sourcing parcial).
2. Documentar en el SAD que la detección por tiempo es la implementación correcta y actualizar `comportamiento-runtime` para reflejarla.

**Estado:** 🟡 Moderado — Deuda técnica aceptada; documentada en `payment-refund-async.md`

---

## Gap 005 — Documentos SAD v2.0 en rama diferente al código 🟡 Moderado

**Inconsistencia detectada:**
- `docs/sad-vista-componentes-patrones.md` y `docs/flujos-usuario.md` están en `feat/docs-baseline-e3`
- El código de payment-service (y los nuevos ADRs) están en `feat/payment-outbox-e2e`
- Al momento de la entrega, el código y los documentos estarán en ramas diferentes si no se fusionan

**Documentos afectados:**
- Todas las referencias cruzadas entre el SAD v2.0 y el código de payment-service

**Causa raíz:**
Estrategia de branching que separó código (feat/payment-outbox-e2e) de documentación (feat/docs-baseline-e3) para evitar conflictos. Descrita en `README.md`: *"Restricción de ramas: No mergear feat/docs-baseline-e3 a master hasta que feat/payment-outbox-e2e esté lista."*

**Resolución propuesta:**
Merge de `feat/payment-outbox-e2e` a `master` primero, luego `feat/docs-baseline-e3` a `master`, resolviendo conflictos en `pom.xml` y `definitions.json`.

**Estado:** 🟡 Moderado — Agendado como última acción antes de la entrega

---

## Gap 006 — Ausencia de diagrama C4 Nivel 4 (código) en documentos accesibles 🟡 Moderado

**Inconsistencia detectada (cita del feedback del profesor):**
> "El SAD sí incluye C4 en niveles de contexto, contenedores y componentes, pero no llega al último nivel."

- El spec `docs/superpowers/specs/2026-05-02-diagramas-sad-entrega-3-design.md` planifica el C4 Nivel 4 para `inscription-service`
- El archivo de destino `docs/sad-vistas-arquitectonicas.md` NO existe aún en ninguna rama

**Documentos afectados:**
- SAD v2.0 (en `feat/docs-baseline-e3`)
- Specs de diagramas (en `docs/superpowers/specs/`)

**Causa raíz:**
El trabajo de diagramación (Prompt 2 del plan E3) no se ha ejecutado aún. Este es el sub-proyecto más visible para el profesor.

**Resolución propuesta:**
Ejecutar Prompt 2 (diagramas Mermaid C1-C4 + Kruchten 4+1) como próxima prioridad después de los commits 3-8 del plan actual.

**Estado:** 🔴 Crítico — Agendado Prompt 2

---

## Gap 007 — Outbox Pattern referenciado como ADR-11 en matrices, pero ADR-008 en el índice 🟡 Moderado

**Inconsistencia detectada:**
- `docs/matriz-trazabilidad.md` línea 130: `ADR-11: Outbox Pattern para eventos de dominio`
- `docs/adrs/README.md`: `ADR-008: Patrón Transactional Outbox`

**Causa raíz:**
Misma causa que Gap 001 — numeración no centralizada previa al índice maestro.

**Resolución propuesta:**
Parte del trabajo de Commit 2.5 (reconciliación de numeración).

**Estado:** 🔴 Crítico — Agendado Commit 2.5

---

## Resumen de estado

| Gap | Descripción | Severidad | Commit de resolución |
|---|---|---|---|
| 001 | Colisión numeración ADR | 🔴 Crítico | Commit 2.5 |
| 002 | RN-PAGO-05 sin aprobación formal | 🟡 Moderado | Aprobación pendiente |
| 003 | Criterios de aceptación heterogéneos | 🟡 Moderado | Parcialmente resuelto en docs/trazabilidad-driver-decision.md |
| 004 | Detección pago tardío por tiempo vs. estado | 🟡 Moderado | Documentado como deuda técnica |
| 005 | Código y SAD en ramas diferentes | 🟡 Moderado | Merge final antes de entrega |
| 006 | C4 Nivel 4 ausente | 🔴 Crítico | Prompt 2 (diagramas) |
| 007 | Outbox ADR-11 vs ADR-008 | 🔴 Crítico | Commit 2.5 |
