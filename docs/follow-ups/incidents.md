# Registro de Incidentes Operativos

## Incidente 001 — Pérdida accidental de stash@{1} durante Commit 1

**Fecha:** 2026-05-18  
**Severidad:** Baja (sin pérdida de datos por backup proactivo previo)  
**Branch:** feat/payment-outbox-e2e

**Contexto**  
Durante la ejecución del Commit 1 (deps), Claude Code ejecutó un
`git stash pop` sobre stash@{1} que generó conflicto. La resolución del
conflicto eliminó el stash de la pila.

**Impacto**  
Cero pérdida de información: el contenido del stash había sido
respaldado preventivamente en `docs/follow-ups/event-controller-admin-endpoints.patch`
durante la fase de protección posterior al Commit 4.

**Causa raíz**  
`git stash pop` no es atómico: si el merge falla, el stash queda eliminado
de la pila aun cuando los cambios no se aplican limpiamente. Comportamiento
documentado de Git pero contraintuitivo.

**Acción correctiva inmediata**  
Validación seca del patch (`git apply --check`) confirmó integridad del backup:
```
git apply --check docs/follow-ups/event-controller-admin-endpoints.patch
# → exit code 0, sin errores. Patch aplicable limpiamente.
```

**Acción preventiva**  
Política a partir de este momento: nunca usar `git stash pop`. Usar siempre:
1. `git stash apply stash@{n}`
2. Verificar que aplicó correctamente
3. `git stash drop stash@{n}` (solo si todo OK)

**Lección aprendida**  
La decisión arquitectónica de respaldar stashes a patch en disco antes
de operaciones de riesgo se validó como correcta. Documentar como
evidencia de gestión proactiva de riesgo en la PPT de defensa.

---

## Incidente 002 — Colisión de numeración ADR

**Fecha:** 2026-05-18  
**Severidad:** Alta (riesgo de credibilidad documental ante el profesor; bloqueado por feedback explícito de Entrega 2)  
**Branch:** feat/payment-outbox-e2e

**Contexto**  
Durante el Commit 2 se creó `ADR-018` (ShedLock) bajo el nuevo schema MADR estándar
de 3 dígitos. Documentos pre-existentes (`comportamiento-runtime-inscripcion-pago.md`,
`matriz-trazabilidad.md`) referenciaban ADRs sin padding (`ADR-18 = Circuit Breaker`),
generando colisión semántica directa: mismo número, contenido radicalmente diferente.

**Impacto**  
Cualquier lector que cruce `ADR-18` en el documento de runtime con el índice maestro
obtendrá la descripción incorrecta (ShedLock en lugar de Circuit Breaker).
El profesor calificó la Entrega 2 con 3.3/5.0 citando "persisten problemas de
consistencia documental" — esta colisión agravaría ese feedback si no se corrige.

**Causa raíz**  
Los ADRs en documentos pre-existentes (SRS, SAD v1.0/v2.0) nunca tuvieron numeración
formal centralizada. El SAD original los numeraba `ADR-01..ADR-20` en orden de redacción.
El índice `docs/adrs/README.md` creado en Commit 2 usa el schema MADR estándar (3 dígitos)
con asignaciones de contenido propias, sin reconciliar con el schema previo.

**Acción correctiva — Commit 2.5 (Tier 1)**  
Normalización de todas las referencias con mapeo limpio (6 archivos):
- `comportamiento-runtime-inscripcion-pago.md`: ADR-07→ADR-012, ADR-09→ADR-008,
  ADR-11→ADR-008, ADR-17→ADR-019, ADR-18→ADR-009.
- `build-conventions.md`: ADR-18→ADR-009.
- `follow-ups/payment-refund-async.md`: ADR-11→ADR-008.
- `follow-ups/event-controller-hexagonal-refactor.md`: ADR-01→ADR-005.
- `superpowers/plans/*.md`: ADR-11→ADR-008.
- `superpowers/specs/*.md`: ADR-01→ADR-005.

**Acciones diferidas**  
- Commit 2.6 (Tier 2A): referencias huérfanas en `srs-casos-uso-pendientes.md`
  (ADR-21, ADR-22 sin entry en índice → añadir ADR-020, ADR-021).
- Prompt 8 (Tier 2B): reconciliación completa de `matriz-trazabilidad.md`
  incluyendo conflictos de contenido en ADR-13/ADR-14.

**Acción preventiva**  
Política creada: `docs/policies/adr-naming-policy.md`. Cualquier referencia a ADR
debe usar el ID literal del índice maestro `docs/adrs/README.md`.

---

## Incidente 003 — Discrepancia entre ADR-019 y realidad de DLX en RabbitMQ

**Fecha:** 2026-05-19  
**Severidad:** Baja (detectado y corregido en el mismo commit)  
**Branch:** feat/payment-outbox-e2e

**Contexto**  
ADR-019 fue redactado en el Commit 2 describiendo una topología ideal con un
único exchange Dead Letter (`eventos.dlq.exchange`). Al ejecutar la fase de
investigación previa al Commit 3, se descubrió que `definitions.json` ya tenía
`eventos.dlx` (direct) configurado como DLX en todas las colas productivas
existentes, con colas per-queue (`dlq.pago.confirmado`, etc.) y sus bindings.

**Impacto**  
Si se hubiera aplicado el Commit 3 sin investigación previa, se habría
cambiado `x-dead-letter-exchange` en todas las colas productivas de `eventos.dlx`
a `eventos.dlq.exchange`, rompiendo los bindings per-queue existentes.

**Causa raíz**  
ADR-019 fue redactado sin inspeccionar el estado real de `definitions.json`.
El proceso de redacción de ADRs de infraestructura omitió la fase de
"verificación del estado actual" que aplica el resto del proyecto.

**Acción correctiva**  
- `ADR-019` modificado para reflejar honestamente la coexistencia de dos DLX.
- `definitions.json` actualizado para añadir `eventos.dlq.exchange` y la cola
  `eventos.dlq` como catch-all, SIN modificar los arguments de colas existentes.
- `infrastructure/rabbitmq/README.md` creado documentando la topología completa.
- `docs/follow-ups/dlx-unification.md` creado con el plan de unificación futura.

**Acción preventiva**  
A partir de ahora, todo ADR que describa infraestructura existente debe
estar precedido por una fase de inspección del estado actual — el mismo
principio aplicado al Commit 4 (Pago.reembolsar()), al Commit 3 anterior
(investigación del schema) y en general a cualquier "diseñar sobre la realidad".
