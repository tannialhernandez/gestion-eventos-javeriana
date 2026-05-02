# Spec: Representación arquitectónica completa para Entrega 3

**Fecha:** 2026-05-02
**Autora:** Tannia Hernández Rojas
**Sub-proyecto:** 1 de 3 (Diagramas → Código → PPT/cierre)
**Deadline objetivo:** ~2026-05-23

---

## 1. Motivación

El feedback del profesor sobre la Entrega 2 (calificación 3.3/5.0) señala explícitamente:

> *"El SAD sí incluye C4 en niveles de contexto, contenedores y componentes, pero no llega al último nivel. Además, no aplicaron Kruchten 4+1, que era una expectativa importante. Esto no es un detalle menor: limita la capacidad del documento para presentar la arquitectura de manera formal, completa y defendible."*

Este sub-proyecto cierra ese gap produciendo el modelo C4 completo (C1→C4) y las cinco vistas de Kruchten 4+1, formalizadas con notación estándar y trazabilidad explícita a requisitos y ADRs.

## 2. Alcance

### Dentro del alcance
- 4 diagramas C4: Contexto, Contenedores, Componentes, Código
- 5 diagramas Kruchten 4+1: Lógica, Desarrollo, Procesos, Física/Despliegue, Escenarios
- Trazabilidad inline (cada diagrama referencia RF/RNF/ADR/patrones)
- Exportación a PNG/SVG para uso en la PPT de defensa
- Conversión de los diagramas ASCII existentes en el SAD a Mermaid

### Fuera del alcance
- Diagramas C3 de servicios no implementados (`auth-service`, `notification-service`, `certificate-service`) — solo aparecen como "previstos" en C2
- Diagramas de actividad por cada caso de uso del SRS — solo 3-4 escenarios críticos
- Documentación de cada clase del código — eso queda como responsabilidad del propio código
- Diagramas de despliegue en cloud específico (AWS/Azure) — se menciona conceptualmente, no se diagrama
- C4 nivel 4 (código) para servicios distintos de `inscription-service` — el deep-dive es solo del flujo crítico de inscripción

## 3. Decisiones de diseño

### 3.1 Herramienta: Mermaid + PlantUML como fallback
**Decisión:** Mermaid es el formato primario. PlantUML solo se usa cuando Mermaid no soporta la notación necesaria.

**Razón:**
- Mermaid renderiza nativo en GitHub, GitLab y VS Code → revisión visual sin tooling adicional
- Versionable como código → diff legible en git, regenerable, auditable
- Exportación a PNG/SVG con `mermaid-cli` o plugins de VS Code → flujo directo a la PPT
- Velocidad de iteración mayor que herramientas visuales (draw.io, Lucidchart)

**Alternativas descartadas:**
- **Structurizr DSL:** rigor metodológico mayor pero curva de aprendizaje suma 1-2h en un proyecto con presupuesto ajustado, y solo cubre C4 (Kruchten queda aparte)
- **draw.io / Lucidchart:** estética más pulida pero no versionable cómodamente y multiplica el tiempo de creación

### 3.2 Organización: archivo único consolidado
**Decisión:** Todos los diagramas viven en un solo documento nuevo:
```
docs/sad-vistas-arquitectonicas.md
```

**Razón:**
- Navegación lineal alineada al orden esperado en la PPT
- Una sola fuente de verdad → evita inconsistencias entre diagramas que se actualizan en momentos distintos (el profe explícitamente señaló inconsistencias documentales como problema en Entrega 2)
- Encaja con el patrón ya establecido en `docs/` (otros artefactos también son `.md` consolidados)

**Alternativa descartada:** un archivo por vista. Más maintainable a largo plazo pero introduce overhead de navegación y multiplica el riesgo de inconsistencia entre vistas que deberían referirse a las mismas entidades.

### 3.3 Trazabilidad inline obligatoria
**Decisión:** Cada diagrama lleva al pie una sección "Trazabilidad" con:
- Requisitos funcionales relacionados (RF-XXX)
- Requisitos no funcionales relacionados (RNF-XXX)
- ADRs que materializa
- Patrones de diseño aplicados

**Razón:** El profe identificó como gap explícito que falta el paso *"necesidad → requisito → driver → decisión arquitectónica → validación"*. La trazabilidad inline cierra ese gap por diagrama, no en una tabla aparte que nadie lee.

### 3.4 Flujo elegido para C4 (código): inscripción con bloqueo pesimista
**Decisión:** El nivel C4 (código) se materializa como diagrama de clases del flujo de inscripción de `inscription-service`, mostrando:
- Agregado `Inscripcion` (raíz) + entidades + value objects
- Repositorio JPA con anotación `@Lock(PESSIMISTIC_WRITE)`
- Servicio de aplicación que orquesta el flujo
- `OutboxEvent` y publicador transaccional
- Eventos de dominio publicados (`CupoReservadoEvent`, `InscripcionExpiradaEvent`)
- Adaptadores REST y mensajería

**Razón:** El profe pidió textualmente "mostrar de manera explícita cómo se gestiona el bloqueo (concurrencia)". Este flujo ya está implementado en código (commit `eb26fdb`) y representa el "core declarado" del proyecto. Cualquier otro flujo sería más débil para defender el atributo de calidad de concurrencia.

## 4. Inventario de diagramas

| # | Modelo | Vista | Notación | Contenido | Estimado |
|---|---|---|---|---|---|
| 1 | C4 | C1 · Contexto | Mermaid `C4Context` | Sistema + actores (estudiante, organizador, admin) + sistemas externos (Google SSO, pasarela de pago, Zoom/Teams, S3) | 30 min |
| 2 | C4 | C2 · Contenedores | Mermaid `C4Container` | API Gateway + 6 microservicios + PostgreSQL + Redis + RabbitMQ + S3. Convierte el ASCII de SAD sec 2 a Mermaid. | 45 min |
| 3 | C4 | C3 · Componentes | Mermaid `C4Component` (3 diagramas) | Estructura hexagonal interna de `event-service`, `inscription-service`, `payment-service` | 1.5 h |
| 4 | C4 | C4 · Código | Mermaid `classDiagram` | Diagrama de clases del flujo de inscripción con bloqueo pesimista + Outbox | 1 h |
| 5 | Kruchten | Lógica | Mermaid `classDiagram` o `flowchart` | Paquetes principales por bounded context (`application`, `domain`, `infrastructure`) y sus relaciones | 45 min |
| 6 | Kruchten | Desarrollo | Mermaid `flowchart` | Estructura Maven multi-módulo + pipeline conceptual de pruebas (unit/integration/E2E) | 30 min |
| 7 | Kruchten | Procesos | Mermaid `sequenceDiagram` (3 diagramas) | (a) Inscripción con bloqueo pesimista, (b) Webhook de pago + idempotencia, (c) Expiración de reserva por timeout | 2 h |
| 8 | Kruchten | Física / Despliegue | Mermaid `flowchart` | Topología Docker Compose con puertos, volúmenes, redes. Convierte el ASCII de SAD sec 5.1. | 45 min |
| 9 | Kruchten | Escenarios | Mermaid `flowchart` o casos UML | 3-4 casos de uso clave del SRS: inscripción exitosa, pago fallido con liberación de cupo, certificado emitido | 1 h |

**Total: 9 diagramas, ~9 horas estimadas.**

## 5. Estructura del documento `sad-vistas-arquitectonicas.md`

```markdown
# SAD — Vistas Arquitectónicas Completas
## Plataforma de Gestión de Eventos Académicos — Pontificia Universidad Javeriana

**Versión:** 3.0 (Entrega 3)
**Continuación de:** SAD v2.0 (sad-vista-componentes-patrones.md)

## 1. Propósito y alcance del documento
## 2. Modelo C4
   ### 2.1 C1 — Diagrama de Contexto
       - Diagrama Mermaid
       - Actores y sistemas externos descritos
       - Trazabilidad: RF/RNF/ADR
   ### 2.2 C2 — Diagrama de Contenedores
   ### 2.3 C3 — Diagrama de Componentes
       (3 sub-secciones, una por microservicio implementado)
   ### 2.4 C4 — Diagrama de Código (flujo de inscripción)
## 3. Modelo Kruchten 4+1
   ### 3.1 Vista Lógica
   ### 3.2 Vista de Desarrollo
   ### 3.3 Vista de Procesos
       (3 sub-secciones, una por proceso crítico)
   ### 3.4 Vista Física / Despliegue
   ### 3.5 Vista de Escenarios (+1)
## 4. Mapa cruzado C4 ↔ Kruchten
   - Tabla que muestra cómo los diagramas se complementan (no se duplican)
## 5. Exportación para la PPT
   - Lista de archivos PNG/SVG generados y a qué slide pertenece cada uno
```

## 6. Criterios de aceptación

El sub-proyecto se considera cerrado cuando:

- [ ] Existe `docs/sad-vistas-arquitectonicas.md` con los 9 diagramas
- [ ] Los 9 diagramas renderizan correctamente en Mermaid Live Editor o VS Code
- [ ] Cada diagrama tiene su sección de trazabilidad (RF/RNF/ADR/patrones)
- [ ] Los 9 diagramas están exportados como PNG/SVG en `docs/diagramas/` listos para la PPT
- [ ] El documento referencia explícitamente al SAD v1.0 y v2.0 (no es independiente, es continuación)
- [ ] La sección 4 (Mapa cruzado) demuestra que C4 y Kruchten no se duplican sino que se complementan
- [ ] Los diagramas ASCII previos del SAD v2.0 quedan explícitamente marcados como "superseded by v3.0" o se eliminan tras revisión

## 7. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Mermaid no soporta una notación específica de C4 que se necesita | Fallback a PlantUML para ese diagrama puntual; documentado por qué se usó |
| Tiempo subestimado en C3 (3 servicios × 30 min cada uno es optimista) | Empezar por `inscription-service` (el más crítico); si los otros dos quedan más simples, reusar la plantilla |
| Inconsistencia con SAD v2.0 al convertir ASCII a Mermaid | El nuevo documento incluye una nota explícita de "supersede" sobre las secciones convertidas, y se valida con un diff conceptual |
| Profundidad insuficiente en C4 (código) | Validar con la matriz de trazabilidad: el diagrama debe responder explícitamente "cómo se gestiona el bloqueo" (texto literal del feedback del profe) |

## 8. Dependencias con otros sub-proyectos

- **Entrega de este sub-proyecto** → habilita el siguiente (alinear código con arquitectura)
- **PPT (sub-proyecto 3)** consume directamente los PNG/SVG exportados aquí
- **Cierre de inconsistencias del SAD** se beneficia: este documento es la fuente de verdad consolidada que sustituye los diagramas ASCII inconsistentes

## 9. Lo que NO resuelve este sub-proyecto

- No implementa código nuevo
- No actualiza el SRS
- No genera la PPT (solo deja los insumos visuales listos)
- No cubre los 7 frentes de la Entrega 3 — solo el #1 (representación arquitectónica)
