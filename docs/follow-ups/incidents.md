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
