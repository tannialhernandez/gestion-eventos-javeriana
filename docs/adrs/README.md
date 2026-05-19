# Índice de Architecture Decision Records (ADRs)

Registro cronológico de decisiones arquitectónicas del proyecto 
Plataforma de Gestión de Eventos Académicos — Pontificia Universidad 
Javeriana.

## Estado de los ADRs

| ID | Título | Estado | Fecha | Relacionado con |
|---|---|---|---|---|
| ADR-001 | Arquitectura de microservicios | Aceptada | 2026-03-15 | RNF-02, RNF-04 |
| ADR-002 | Base de datos PostgreSQL por servicio | Aceptada | 2026-03-15 | RNF-08 |
| ADR-003 | Caché Redis para catálogo de eventos | Aceptada | 2026-03-20 | RNF-01, RF-010 |
| ADR-004 | Despliegue contenedorizado AWS ECS/Fargate | Aceptada | 2026-04-20 | RNF-04, RNF-06 |
| ADR-005 | Desacoplamiento de proveedores externos (Hexagonal) | Aceptada | 2026-03-15 | RNF-10 |
| ADR-006 | Procesamiento asíncrono con colas | Aceptada | 2026-03-15 | RNF-04, RF-007, RF-008 |
| ADR-007 | Autenticación OAuth 2.0 / OIDC | Aceptada | 2026-03-15 | RNF-05 |
| ADR-008 | Patrón Transactional Outbox | Aceptada | 2026-04-10 | RN-13, RNF-08 |
| ADR-009 | Resiliencia con Circuit Breaker | Aceptada | 2026-04-21 | RNF-14 |
| ADR-010 | Cumplimiento Ley 1581 y auditoría inmutable | Aceptada | 2026-04-21 | RF-029, RF-030 |
| ADR-011 | Frontend SPA React + Vite | Aceptada | 2026-04-21 | RNF-01 |
| ADR-012 | Control de concurrencia con bloqueo pesimista | Aceptada | 2026-04-21 | RNF-16, RF-011, RN-01 |
| ADR-013 | Patrón Factory Method para pasarela de pagos | Aceptada | 2026-05-18 | ADR-005, OCP |
| ADR-014 | Patrón State en agregado Evento | Pendiente | TBD | RF-001 |
| ADR-015 | Estrategia de autenticación y autorización end-to-end | Pendiente | TBD | RNF-05 |
| ADR-016 | Estrategia de notificaciones asíncronas | Pendiente | TBD | RF-007 |
| ADR-017 | Estrategia de generación de certificados | Pendiente | TBD | RF-008 |
| ADR-018 | Distributed Locking para Outbox Relay (ShedLock) | Aceptada | 2026-05-18 | RNF-02, RN-13 |
| ADR-019 | Estrategia de Dead Letter Queue | Aceptada | 2026-05-18 | RNF-04, RNF-08 |
| ADR-020 | Estrategia de timeout y retry en Certificate Service | Propuesta | 2026-05-18 | RF-008, RNF-07, RNF-08 |
| ADR-021 | Firma HMAC-SHA256 para tokens QR de asistencia | Propuesta | 2026-05-18 | RF-032, RNF-05, RNF-08 |
| ADR-022 | API Gateway — rate limiting, routing y políticas transversales | Propuesta | 2026-05-18 | RNF-04, RNF-06, RNF-08 |
| ADR-023 | Política de almacenamiento S3 y ciclo de vida de PDFs | Propuesta | 2026-05-18 | RNF-09, RF-008, RF-031 |

## Convenciones

- Formato MADR (Markdown ADR) estándar.
- Estados: Propuesta, Aceptada, Rechazada, Superseded.
- Numeración secuencial sin reutilización.
- Inmutabilidad: un ADR aceptado no se modifica; si la decisión cambia, 
  se crea un ADR nuevo que supersede al anterior con campo "Supersedes: 
  ADR-XXX".

## Cómo proponer un nuevo ADR

1. Copiar la plantilla `docs/adrs/_TEMPLATE.md`.
2. Asignar el siguiente número secuencial disponible.
3. Documentar Contexto, Decisión, Alternativas, Consecuencias, 
   Trazabilidad.
4. Estado inicial: Propuesta.
5. Tras revisión y aprobación, cambiar a Aceptada con fecha.

## Trazabilidad

Cada ADR debe citar al menos un RF (Requisito Funcional), RNF (Requisito 
No Funcional) o RN (Regla de Negocio) del SRS al que responde.
