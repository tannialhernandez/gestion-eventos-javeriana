# Diagramas Arquitectónicos

Esta carpeta contiene los diagramas del proyecto en formato PlantUML (generados via Structurizr MCP).

## Estado — Entrega 3

| Archivo | Tipo | Vista Kruchten | Estado |
|---|---|---|---|
| `c4-context.puml` | PlantUML C4 | Módulo (N1 Contexto) | ⏳ pendiente |
| `c4-container.puml` | PlantUML C4 | Módulo (N2 Contenedores) | ⏳ pendiente |
| `c4-component-inscription-service.puml` | PlantUML C4-Component | Módulo (N3 Componentes) | ✅ listo |
| `c4-code-inscription-service.puml` | PlantUML UML Class | Módulo (N4 Código) | ✅ listo |
| `vista-logica-kruchten.puml` | PlantUML Package Diagram | Vista Lógica (Kruchten) | ✅ listo |
| `despliegue-aws.puml` | PlantUML C4-Deployment | Física (Kruchten) / C4 N4 | ✅ listo |
| `escenario-a-inscripcion-outbox.puml` | PlantUML Sequence | Proceso | ✅ listo |
| `escenario-b-confirmacion-pago.puml` | PlantUML Sequence | Proceso | ✅ listo |
| `escenario-c-expiracion-job.puml` | PlantUML Sequence | Proceso | ✅ listo |
| `vp-escenario-a-fanout.puml` | PlantUML Sequence | Proceso (vista procesos) | ✅ listo |
| `vp-escenario-b-pago.puml` | PlantUML Sequence | Proceso (vista procesos) | ✅ listo |
| `vp-escenario-c-expiracion.puml` | PlantUML Sequence | Proceso (vista procesos) | ✅ listo |

## Archivos fuente Structurizr

| Archivo | Descripción |
|---|---|
| `inscription-service.dsl` | Workspace Structurizr DSL para Inscription Service (C4 N1-N3). Generado via `mcp__structurizr__parse` y `mcp__structurizr__export-c4plantuml` |
| `deployment-aws.dsl` | Workspace Structurizr DSL para Vista Física AWS (C4 N4 Deployment). Validado y exportado via `mcp__structurizr__validate` + `mcp__structurizr__export-c4plantuml` + `mcp__structurizr__export-mermaid` |

## Descripción de diagramas completados

### `c4-component-inscription-service.puml` — C4 Nivel 3
Vista de componentes del Inscription Service exportada desde el DSL Structurizr.
Muestra los 15 componentes (driving adapters, application services, domain services, ports, driven adapters)
y las 19 relaciones entre ellos.

### `despliegue-aws.puml` — Vista Física / C4 Nivel 4 Deployment

Diagrama de despliegue AWS exportado desde el DSL Structurizr (`deployment-aws.dsl`).  
Muestra la topología completa: VPC, subnets públicas/privadas, EC2+Docker Compose, RDS, ElastiCache, Amazon MQ, ALB, CloudFront, S3, ECR, Secrets Manager y CloudWatch.

Documentación completa (13 secciones): [`../vista-fisica-deployment.md`](../vista-fisica-deployment.md)

Incluye:
- Inventario de nodos (21 recursos AWS)
- Estimación de costos FinOps (3 variantes dentro de 100 USD)
- Topología de red VPC con CIDRs y Route Tables
- Matriz de Security Groups (principio de mínimo privilegio)
- Diagrama interno Docker Compose
- Pipeline CI/CD (backend + frontend)
- Estrategia de observabilidad, backup y recuperación
- Escenarios de fallo y mitigación
- Mapeo a RNFs y ADRs relacionados (ADR-004, ADR-008, ADR-012, ADR-018, ADR-019 + 4 ADRs sugeridos)

### `c4-code-inscription-service.puml` — C4 Nivel 4
Diagrama UML de clases con arquitectura hexagonal completa.
Incluye los 5 paquetes Java, 22 clases/interfaces/enums, y todas las relaciones.

Materializa explícitamente:
- **ADR-012** — `EventoJpaRepository.findByIdForUpdate()` con `@Lock(PESSIMISTIC_WRITE)`
- **ADR-008** — `OutboxEvent` + `OutboxRelayService` como relay del Transactional Outbox
- **ADR-018** — `@SchedulerLock` en `ExpirarInscripcionesPendientesService` y `OutboxRelayService`

## Herramientas

- PlantUML: https://plantuml.com/
- Extensión VS Code: "PlantUML" (jebbs.plantuml) — renderiza con Alt+D
- Structurizr DSL: https://structurizr.com/dsl
