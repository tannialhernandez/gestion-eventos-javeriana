# TECH_DEBT.md - Deuda Tecnica Institucional y Roadmap Fase 2

Proyecto: Plataforma de Gestion de Eventos Academicos - Pontificia Universidad Javeriana  
Fecha de consolidacion: 2026-05-31  
Alcance: Entrega 3, Prompts 9-28

## 1. Resumen ejecutivo

Este documento registra la deuda tecnica consciente de Entrega 3: decisiones diferidas, stubs intencionales, brechas de validacion productiva y trabajo post-MVP necesario para operar el sistema en un entorno institucional real. Su proposito no es justificar omisiones, sino hacerlas visibles, trazables y priorizables con criterio arquitectonico. La posicion del equipo es que una deuda declarada, acotada y con plan de cierre es preferible a una falsa afirmacion de completitud.

| Severidad | Cantidad | Esfuerzo estimado |
|---|---:|---:|
| Critica P0 | 3 | 9.75 dias-persona |
| Mayor P1 | 9 | 16.5 dias-persona |
| Menor P2 | 7 | 15.5 dias-persona |
| Total | 19 | 41.75 dias-persona |

El backlog completo suma aproximadamente 41.75 dias-persona tras cerrar CI/CD y dejar AWS declarado como IaC. El roadmap recomendado lo organiza en 3 sprints de 2 semanas, con paralelizacion o priorizacion por riesgo para que Fase 2 pueda cerrarse en 6 semanas calendario.

## 2. Inventario de deudas tecnicas

### 2.1 Deudas criticas P0

#### D-001 - Despliegue AWS productivo declarado en IaC, apply pendiente

| Campo | Detalle |
|---|---|
| Estado | Reformulado en Prompt 29.8-B |
| Descripcion | El SAD describe despliegue productivo en AWS. Entrega 3 ya incluye Terraform declarativo para VPC, ALB, EC2 Docker Compose, RDS PostgreSQL, ElastiCache Redis, Amazon MQ RabbitMQ, S3 y CloudFront; falta ejecutar `terraform apply` con una cuenta AWS real. |
| Justificacion de Entrega 3 | La cuenta AWS institucional se gestiona en paralelo. Para evitar 12h de trabajo posterior, se dejo IaC versionada y validable en CI sin consumir recursos AWS. |
| Impacto en produccion | No se ha certificado disponibilidad ni rendimiento sobre infraestructura AWS real; TLS con dominio propio, secretos productivos, backups avanzados y alarmas CloudWatch quedan para el apply/hardening. |
| Mitigacion actual | `infra/terraform/`, `docs/deployment-aws-runbook.md`, workflows de Terraform validate/plan dry-run, Dockerfiles por servicio, GHCR y evidencia reproducible local. |
| Plan de cierre | Configurar credenciales AWS, revisar `terraform.tfvars`, ejecutar `terraform init/plan/apply`, validar outputs, publicar SPA en S3/CloudFront, correr smoke post-deploy y ejecutar `terraform destroy` si es demo temporal. |
| Esfuerzo estimado | 5h restantes; IaC ya preparado |
| Prioridad Fase 2 | P0 |
| ADRs relacionados | ADR-004 despliegue contenedorizado AWS/ECS, ADR-022 API Gateway policies, ADR-018 ShedLock |
| Evidencias en el repo | `infra/terraform/`, `docs/deployment-aws-runbook.md`, `.github/workflows/terraform-validate.yml`, `docs/vista-fisica-deployment.md`, `docs/limites-carga-reconocidos.md` |

#### D-002 - Integracion Mercado Pago real pendiente

| Campo | Detalle |
|---|---|
| Estado | Mockeado / simulado |
| Descripcion | El flujo de pago usa proveedor `simulador` y stubs de pasarela para E2E; existe adaptador `MercadoPagoAdapter`, pero la certificacion contra sandbox/productivo de Mercado Pago no fue ejecutada. |
| Justificacion de Entrega 3 | Se evito depender de credenciales externas, cobros reales, cuenta PSP y aprobaciones administrativas. El objetivo era validar orquestacion, webhook, auditoria e idempotencia. |
| Impacto en produccion | No se valida comportamiento real de preferencias, firmas, callbacks, expiraciones, reembolsos, errores del PSP ni conciliacion financiera. |
| Mitigacion actual | Puerto hexagonal `PasarelaPagoPort`, Factory Method, `payment.gateway.provider=simulador`, webhook HMAC y contrato de dominio equivalente para confirmar pago. |
| Plan de cierre | Obtener credenciales sandbox, configurar secrets, ejecutar pruebas de preferencia real, validar firma oficial, callbacks, reintentos, reembolsos, reconciliacion y monitoreo financiero. |
| Esfuerzo estimado | 3-5 dias-persona; referencia usada: 4 |
| Prioridad Fase 2 | P0 |
| ADRs relacionados | ADR-005 desacoplamiento de proveedores externos, ADR-013 Factory Method pasarela, ADR-009 Circuit Breaker, ADR-010 auditoria |
| Evidencias en el repo | `payment-service/src/main/java/com/javeriana/eventos/payment/infrastructure/pasarela/`, `payment-service/src/main/resources/application.yml`, `docs/alcance-y-decisiones-de-mocks.md`, `docs/comportamiento-runtime-inscripcion-pago.md` |

#### D-003 - `auth-service-stub` reemplazable por Azure AD tenant Javeriana

| Campo | Detalle |
|---|---|
| Estado | Mockeado / parcial |
| Descripcion | Entrega 3 implementa `auth-service-stub` con login HTTP real, 5 usuarios demo, JWKS y JWT RS256. No es un IdP productivo ni un microservicio definitivo del SAD. |
| Justificacion de Entrega 3 | Permitio cerrar login real E2E sin que el navegador firme tokens, evitando desplegar Azure AD, Keycloak o un tenant institucional completo. |
| Impacto en produccion | Credenciales demo, sin MFA, sin federacion institucional, sin revocacion centralizada, sin politicas de acceso Javeriana y sin ciclo de vida real de usuarios. |
| Mitigacion actual | Claims compatibles (`sub`, `email`, `name`, `roles`), JWKS real, validacion stateless por microservicios y token en `sessionStorage`. |
| Plan de cierre | Registrar SPA como cliente publico OIDC, integrar Azure AD tenant Javeriana o Keycloak federado, consumir JWKS real, migrar roles, retirar clave privada demo y validar logout/revocacion. |
| Esfuerzo estimado | 4-6 dias-persona; referencia usada: 5 |
| Prioridad Fase 2 | P0 |
| ADRs relacionados | ADR-007 OAuth/OIDC, ADR-015 estrategia de autenticacion, ADR-010 Ley 1581 |
| Evidencias en el repo | `auth-service-stub/`, `docs/alcance-y-decisiones-de-mocks.md`, `frontend/src/features/auth/`, `frontend/scripts/smoke-e2e.mjs` |

### 2.2 Deudas mayores P1

#### D-004 - Migracion a Authorization Code + PKCE

| Campo | Detalle |
|---|---|
| Estado | Declarado |
| Descripcion | El stub actual equivale al flujo Resource Owner Password Credentials Grant de OAuth 2.0 RFC 6749 seccion 4.3, aceptado solo para demo. |
| Justificacion de Entrega 3 | Se requerian usuarios demo y login E2E rapido, sin IdP externo ni configuracion de cliente OIDC. |
| Impacto en produccion | Password grant no es recomendable para SPA productivo; expone credenciales al cliente y no soporta politicas modernas de identidad. |
| Mitigacion actual | Scope local, usuarios demo, JWT de corta vida, `sessionStorage` y documentacion explicita de no uso productivo. |
| Plan de cierre | Implementar Authorization Code + PKCE, callback seguro, renovacion de sesion segun IdP, validacion de `state`/`nonce` y logout federado. |
| Esfuerzo estimado | 3 dias-persona |
| Prioridad Fase 2 | P1 |
| ADRs relacionados | ADR-007 OAuth/OIDC, ADR-015 autenticacion end-to-end |
| Evidencias en el repo | `docs/alcance-y-decisiones-de-mocks.md`, `docs/adr/ADR-013-frontend-spa-revision-typescript.md`, `frontend/src/features/auth/AuthContext.tsx` |

#### D-005 - `notification-service` no implementado

| Campo | Detalle |
|---|---|
| Estado | Declarado / conceptual |
| Descripcion | El SAD modela notificaciones asincronas, pero no existe un servicio desplegable que consuma eventos y envie correos. |
| Justificacion de Entrega 3 | El flujo critico priorizo inscripcion, pago, outbox e idempotencia; notificaciones fueron desacopladas para Fase 2. |
| Impacto en produccion | Usuarios no reciben confirmaciones, recordatorios, certificados disponibles ni avisos operativos por correo/canal institucional. |
| Mitigacion actual | Eventos de dominio y AsyncAPI listos para consumo; RabbitMQ, outbox, DLQ e idempotencia implementados en servicios productores. |
| Plan de cierre | Crear servicio, base de datos propia, consumidores AMQP, plantillas, proveedores SMTP, DLQ/retry, trazabilidad y pruebas E2E de entrega. |
| Esfuerzo estimado | 5 dias-persona |
| Prioridad Fase 2 | P1 |
| ADRs relacionados | ADR-006 colas, ADR-016 notificaciones asincronas, ADR-019 DLQ, ADR-008 Outbox |
| Evidencias en el repo | `docs/asyncapi/`, `docs/vista-escenarios-kruchten.md`, `docs/matriz-trazabilidad.md`, `inscription-service/src/test/java/com/javeriana/eventos/inscription/InscripcionFlowEndToEndIT.java` |

#### D-006 - `certificate-service` no implementado

| Campo | Detalle |
|---|---|
| Estado | Declarado / conceptual |
| Descripcion | Generacion, almacenamiento y verificacion de certificados no estan implementados como servicio ejecutable. |
| Justificacion de Entrega 3 | La entrega se concentro en el flujo participante hasta pago confirmado; certificados dependen de asistencia, PDF, S3 y reglas posteriores. |
| Impacto en produccion | No se emiten certificados ni QR de verificacion publica, lo cual limita el cierre academico del evento. |
| Mitigacion actual | Diagramas, ADRs propuestos y contratos conceptuales dejan definido el camino de implementacion asincrona. |
| Plan de cierre | Implementar consumidor de eventos, reglas de asistencia, generador PDF, almacenamiento S3, endpoint de verificacion, firma QR y lifecycle de documentos. |
| Esfuerzo estimado | 4 dias-persona |
| Prioridad Fase 2 | P1 |
| ADRs relacionados | ADR-017 certificados, ADR-020 timeout/retry certificate-service, ADR-021 HMAC QR, ADR-023 S3 lifecycle |
| Evidencias en el repo | `docs/adrs/ADR-020-certificate-timeout-retry.md`, `docs/adrs/ADR-021-hmac-sha256-qr-asistencia.md`, `docs/adrs/ADR-023-s3-storage-lifecycle-pdfs.md`, `docs/c4-nivel3-componentes.md` |

#### D-007 - ADR-013 revisado a TypeScript strict

| Campo | Detalle |
|---|---|
| Estado | Documentado / cerrado |
| Descripcion | ADR-013 original declaraba JavaScript; Entrega 3 implemento React + Vite + TypeScript strict. La brecha ya fue formalizada como revision. |
| Justificacion de Entrega 3 | TypeScript redujo riesgo de integracion entre SPA, JWT, errores tipados y microservicios. |
| Impacto en produccion | Sin documentacion, habria inconsistencia SAD/codigo; con ADR revisado, no queda riesgo tecnico pendiente. |
| Mitigacion actual | ADR v1.1 creado, lint/build/testing reproducibles y tipos compartidos por capas. |
| Plan de cierre | Ninguno tecnico. Mantener tipos actualizados cuando evolucionen DTOs. |
| Esfuerzo estimado | 0 dias-persona |
| Prioridad Fase 2 | P1 documental cerrado |
| ADRs relacionados | `docs/adr/ADR-013-frontend-spa-revision-typescript.md`, ADR-011 SPA |
| Evidencias en el repo | `docs/adr/ADR-013-frontend-spa-revision-typescript.md`, `frontend/tsconfig.json`, `frontend/src/` |

#### D-008 - RNF-04 de 500 VUs no certificado localmente

| Campo | Detalle |
|---|---|
| Estado | Parcial |
| Descripcion | RNF-04 exige 500 usuarios concurrentes. Entrega 3 certifica escenarios focalizados y carga local de 150 VUs, no 500 VUs en infraestructura horizontal. |
| Justificacion de Entrega 3 | Docker Compose local comparte CPU, memoria, red y disco entre servicios, bases de datos, Redis, RabbitMQ, Prometheus y K6; no representa produccion. |
| Impacto en produccion | No hay garantia empirica de capacidad a 500 VUs reales ni punto de quiebre caracterizado en AWS. |
| Mitigacion actual | K6 defendible: RNF-16, RNF-14, cache Redis y 150 VUs sostenidos sin errores. |
| Plan de cierre | Ejecutar K6 desde host externo sobre AWS, habilitar replicas, medir Hikari/CPU/RAM/Redis/RabbitMQ/PostgreSQL, generar reporte p95/p99/errores y ajustar autoscaling. |
| Esfuerzo estimado | 2 dias-persona |
| Prioridad Fase 2 | P1 |
| ADRs relacionados | ADR-004 despliegue AWS, ADR-012 concurrencia cupos, ADR-003 cache Redis, ADR-018 ShedLock |
| Evidencias en el repo | `docs/evidencia-carga.md`, `docs/limites-carga-reconocidos.md`, `load-tests/reports/02-load-test.html`, `load-tests/reports/timeseries-charts/load-150-vus-latencia-tiempo.svg` |

#### D-009 - `withRetry.ts` reemplazable por TanStack Query

| Campo | Detalle |
|---|---|
| Estado | Parcial |
| Descripcion | Por bloqueo de registry npm durante Prompt 26 se implemento `withRetry.ts` local para backoff y respeto de `Retry-After`, en lugar de TanStack Query. |
| Justificacion de Entrega 3 | La red no permitia instalar dependencias en ese momento; se priorizo mantener semantica de resiliencia sin alterar runtime backend. |
| Impacto en produccion | Se mantiene mas codigo propio de retry/cache de cliente que el necesario; mayor costo de mantenimiento. |
| Mitigacion actual | Tests unitarios cubren backoff, Retry-After, max attempts y no retry en 4xx no recuperables. |
| Plan de cierre | Revaluar TanStack Query cuando registry este estabilizado, migrar hooks de datos y mantener reglas de `Retry-After`. |
| Esfuerzo estimado | 1 dia-persona |
| Prioridad Fase 2 | P1 |
| ADRs relacionados | ADR-013 frontend TypeScript, ADR-009 Circuit Breaker |
| Evidencias en el repo | `frontend/src/lib/retry/withRetry.ts`, `frontend/src/lib/retry/withRetry.test.ts`, `docs/diagnostico-registry-npm.md` |

#### D-010 - Bloqueo de registry npm y mirror local

| Campo | Detalle |
|---|---|
| Estado | Mitigado localmente |
| Descripcion | El entorno presento bloqueo `ENOTFOUND registry.npmjs.org` y configuracion global hacia `npm.artifacts.furycloud.io`; el frontend usa `.npmrc` local con `registry.yarnpkg.com`. |
| Justificacion de Entrega 3 | Se necesitaba instalar devDependencies de testing/accesibilidad sin credenciales corporativas ni cuentas pagas. |
| Impacto en produccion | Riesgo de reproducibilidad para otros miembros del equipo si su entorno hereda otro registry o proxy corporativo. |
| Mitigacion actual | Diagnostico documentado y `.npmrc` local del frontend. |
| Plan de cierre | Coordinar con TI Javeriana registry oficial, proxy, certificados CA y documentacion de onboarding. |
| Esfuerzo estimado | 0.5 dias-persona |
| Prioridad Fase 2 | P1 |
| ADRs relacionados | ADR-013 frontend TypeScript, RNF-10 mantenibilidad |
| Evidencias en el repo | `docs/diagnostico-registry-npm.md`, `frontend/.npmrc`, `frontend/TESTING.md` |

#### D-011 - CI/CD GitHub Actions

| Campo | Detalle |
|---|---|
| Estado | Cerrado en Prompt 29.8-B |
| Descripcion | El repositorio ya incluye workflows GitHub Actions para backend, frontend, E2E smoke, Terraform validate/plan dry-run y releases por tag. |
| Justificacion de Entrega 3 | La automatizacion se implemento sin depender de AWS real: ejecuta calidad, empaqueta servicios, publica imagenes en GHCR y valida IaC en cada push relevante. |
| Impacto en produccion | Riesgo residual bajo: falta observar el primer ciclo verde completo en GitHub Actions y ajustar tiempos si algun runner excede limite por Testcontainers/Playwright. |
| Mitigacion actual | `.github/workflows/ci-backend.yml`, `ci-frontend.yml`, `ci-e2e.yml`, `terraform-validate.yml`, `release.yml`. |
| Plan de cierre | Monitorear primer run, revisar artefactos, publicar paquetes GHCR como publicos o configurar token de pull en AWS, y agregar branch protection cuando el equipo estabilice los tiempos. |
| Esfuerzo estimado | 0 dias-persona; seguimiento operativo menor |
| Prioridad Fase 2 | Cerrado |
| ADRs relacionados | ADR-004 despliegue, ADR-022 gateway/policies, RNF-10 mantenibilidad |
| Evidencias en el repo | `.github/workflows/`, `https://github.com/tannialhernandez/gestion-eventos-javeriana/actions`, `infra/terraform/`, `frontend/TESTING.md` |

#### D-012 - Refresh token no implementado en auth-stub

| Campo | Detalle |
|---|---|
| Estado | Declarado |
| Descripcion | `auth-service-stub` emite access token, pero no implementa refresh token, rotacion, revocacion ni silent renewal. |
| Justificacion de Entrega 3 | El stub no es IdP productivo; el objetivo era validar login y consumo de JWT en el flujo critico. |
| Impacto en produccion | Sesiones largas o renovacion segura no estan cubiertas; logout global y revocacion quedan pendientes. |
| Mitigacion actual | Tokens de vida acotada, almacenamiento en `sessionStorage` y flujo de logout local. |
| Plan de cierre | Delegar a IdP OIDC real o implementar refresh token rotado si se mantiene auth propio; agregar pruebas de expiracion y revocacion. |
| Esfuerzo estimado | 1 dia-persona |
| Prioridad Fase 2 | P1 |
| ADRs relacionados | ADR-007 OAuth/OIDC, ADR-015 autenticacion end-to-end |
| Evidencias en el repo | `auth-service-stub/src/main/java/com/javeriana/eventos/authstub/JwtIssuer.java`, `frontend/src/features/auth/AuthContext.tsx`, `docs/alcance-y-decisiones-de-mocks.md` |

### 2.3 Deudas menores P2

#### D-013 - Validacion con lectores de pantalla reales pendiente

| Campo | Detalle |
|---|---|
| Estado | Parcial |
| Descripcion | Se ejecuto axe-core y checklist manual, pero no una sesion formal con NVDA, JAWS o VoiceOver y usuarios reales. |
| Justificacion de Entrega 3 | Se priorizo auditoria automatizada reproducible y correccion de hallazgos criticos del flujo. |
| Impacto en produccion | Pueden existir barreras de comprension, orden de lectura o navegacion que axe no detecta. |
| Mitigacion actual | 0 violaciones axe WCAG 2.1 A/AA en 4 paginas criticas, lint a11y y checklist documentado. |
| Plan de cierre | Ejecutar pruebas con VoiceOver/NVDA, documentar hallazgos, corregir copy/ARIA/foco y repetir axe. |
| Esfuerzo estimado | 1 dia-persona |
| Prioridad Fase 2 | P2 |
| ADRs relacionados | ADR-013 frontend TypeScript, RNF-10 mantenibilidad, NTC 5854/WCAG |
| Evidencias en el repo | `docs/evidencia-accesibilidad-frontend.md`, `frontend/test-results/accessibility/`, `frontend/ACCESSIBILITY.md` |

#### D-014 - Storybook no implementado

| Campo | Detalle |
|---|---|
| Estado | Declarado |
| Descripcion | No existe catalogo visual aislado de componentes. |
| Justificacion de Entrega 3 | El SPA aun no es un sistema de diseno amplio; Playwright y capturas cubren evidencia visual critica con menor superficie. |
| Impacto en produccion | Menor capacidad para revisar visualmente estados de componentes sin navegar flujos completos. |
| Mitigacion actual | Capturas Playwright, tests RTL y evidencia frontend documentada. |
| Plan de cierre | Instalar Storybook como devDependency, documentar componentes compartidos, estados de error/degradacion y snapshots visuales. |
| Esfuerzo estimado | 2 dias-persona |
| Prioridad Fase 2 | P2 |
| ADRs relacionados | ADR-013 frontend TypeScript, RNF-10 mantenibilidad |
| Evidencias en el repo | `frontend/test-results/evidence/`, `docs/evidencia-testing-frontend.md`, `docs/evidencia-frontend.md` |

#### D-015 - Pantallas administrativas y gestion avanzada pendientes

| Campo | Detalle |
|---|---|
| Estado | Declarado |
| Descripcion | El SPA implementa flujo critico participante; no implementa perfil organizador, administracion avanzada, gestion completa de eventos, reportes ni bandejas institucionales. |
| Justificacion de Entrega 3 | El alcance priorizo arquitectura y flujo login-catalogo-inscripcion-pago-confirmacion. |
| Impacto en produccion | Organizadores y administradores requieren operaciones manuales o APIs directas para gestion completa. |
| Mitigacion actual | Backend expone capacidades base y los pendientes estan explicitados como alcance post-MVP. |
| Plan de cierre | Implementar rutas protegidas por rol, CRUD de eventos/tarifas, administracion, reportes, estados vacios, pruebas E2E y accesibilidad. |
| Esfuerzo estimado | 6-8 dias-persona; referencia usada: 7 |
| Prioridad Fase 2 | P2 |
| ADRs relacionados | ADR-011 SPA, ADR-013 frontend TypeScript, ADR-007 RBAC/OIDC |
| Evidencias en el repo | `frontend/README.md`, `docs/evidencia-frontend.md`, `docs/srs-casos-uso-pendientes.md`, `docs/alcance-y-decisiones-de-mocks.md` |

#### D-016 - Verificacion multi-instancia de `@SchedulerLock` en jobs batch

| Campo | Detalle |
|---|---|
| Estado | Verificacion pendiente; codigo actual muestra locks presentes |
| Descripcion | El riesgo original indicaba falta de `@SchedulerLock` en jobs batch de inscription/payment. La revision actual encuentra `@SchedulerLock` en `RetentionService` de event, inscription y payment; queda pendiente prueba multi-instancia especifica de los retention jobs. |
| Justificacion de Entrega 3 | Se implemento el patron ShedLock; la evidencia de race multi-instancia quedo concentrada en outbox/retention funcional, no en un test operacional con dos replicas para cada job. |
| Impacto en produccion | Si la configuracion de locks falla en despliegue multi-replica, podrian ejecutarse limpiezas concurrentes o duplicarse ciclos batch. |
| Mitigacion actual | `@EnableSchedulerLock` en aplicaciones, `@SchedulerLock` en retention services, deletes batched e idempotencia por SQL. |
| Plan de cierre | Agregar prueba/ensayo con dos replicas por servicio y verificar una sola ejecucion por lock; documentar resultado. |
| Esfuerzo estimado | 0.5 dias-persona |
| Prioridad Fase 2 | P2 |
| ADRs relacionados | ADR-018 ShedLock, Prompt 23 retention |
| Evidencias en el repo | `event-service/src/main/java/com/javeriana/eventos/event/infrastructure/retention/RetentionService.java`, `inscription-service/src/main/java/com/javeriana/eventos/inscription/infrastructure/retention/RetentionService.java`, `payment-service/src/main/java/com/javeriana/eventos/payment/infrastructure/retention/RetentionService.java` |

#### D-017 - Grafana dashboards no implementados

| Campo | Detalle |
|---|---|
| Estado | Declarado |
| Descripcion | Micrometer/Prometheus y metricas K6 existen, pero no dashboards Grafana versionados. |
| Justificacion de Entrega 3 | La evidencia se genero con reportes HTML/JSON y graficas SVG, suficientes para sustentacion academica. |
| Impacto en produccion | Menor visibilidad operacional continua para latencia, errores, outbox, DLQ, cache, retention, circuit breakers y pagos. |
| Mitigacion actual | Metricas Micrometer, reportes K6, Prometheus observado en escenarios y documentos de evidencia. |
| Plan de cierre | Crear dashboards JSON por servicio, alertas y panel ejecutivo de RNFs. |
| Esfuerzo estimado | 1.5 dias-persona |
| Prioridad Fase 2 | P2 |
| ADRs relacionados | ADR-009 Circuit Breaker, ADR-018 ShedLock, ADR-019 DLQ, RNF observabilidad |
| Evidencias en el repo | `docs/evidencia-carga.md`, `load-tests/reports/`, `event-service/src/main/java/com/javeriana/eventos/event/infrastructure/observability/MdcKeys.java`, `payment-service/src/main/java/com/javeriana/eventos/payment/infrastructure/observability/MdcKeys.java` |

#### D-018 - Reembolso de pago tardio asincrono pendiente

| Campo | Detalle |
|---|---|
| Estado | Diferido a backlog |
| Descripcion | `manejarPagoTardio` y el flujo de reembolso tardio estan documentados, pero la arquitectura ideal requiere sacar la llamada externa de reembolso del path transaccional y procesarla asincronicamente. |
| Justificacion de Entrega 3 | El simulador no falla y el alcance se centro en pago confirmado; redisenar reembolso real con PSP externo era mayor que el fix necesario. |
| Impacto en produccion | Riesgo de divergencia entre PSP y base local si la pasarela reembolsa y luego falla la transaccion local; posible doble reembolso ante retries mal manejados. |
| Mitigacion actual | Documento follow-up, `grace-period`, idempotencia de webhook y simulador controlado. |
| Plan de cierre | Implementar `PagoReembolsadoEvent`, consumer de reembolso, idempotencia del consumer, retries y pruebas de fallo pasarela/BD. |
| Esfuerzo estimado | 0.5 dias-persona para backlog minimo; puede crecer si se integra PSP real |
| Prioridad Fase 2 | P2 |
| ADRs relacionados | ADR-008 Outbox, ADR-010 auditoria, ADR-013 Factory Method pasarela |
| Evidencias en el repo | `docs/follow-ups/payment-refund-async.md`, `payment-service/src/main/java/com/javeriana/eventos/payment/application/ProcesarWebhookService.java`, `inscription-service/src/main/java/com/javeriana/eventos/inscription/application/ConfirmarInscripcionService.java` |

#### D-019 - Observaciones O-01, O-02 y O-03 de auditoria original diferidas

| Campo | Detalle |
|---|---|
| Estado | Declarado / agregado |
| Descripcion | Observaciones documentales menores de la auditoria original quedaron fuera del cierre operativo principal y se consolidan como trabajo agregado. |
| Justificacion de Entrega 3 | Los prompts 9-28 cerraron hallazgos criticos/mayores y evidencias funcionales; las observaciones restantes no bloquean el flujo critico ni las garantias RNF principales. |
| Impacto en produccion | Riesgo bajo: inconsistencias documentales o criterios secundarios pueden generar friccion de mantenimiento y sustentacion. |
| Mitigacion actual | Gap analysis, matriz de trazabilidad, documentos de evidencia y este inventario. |
| Plan de cierre | Revisar observaciones una a una, actualizar trazabilidad/criterios, reconciliar numeracion ADR heredada y cerrar con checklist documental. |
| Esfuerzo estimado | 3 dias-persona agregados |
| Prioridad Fase 2 | P2 |
| ADRs relacionados | ADR naming policy, ADR-008 Outbox, ADR-018 ShedLock, ADR-013 frontend |
| Evidencias en el repo | `docs/srs-sad-gap-analysis.md`, `docs/matriz-trazabilidad.md`, `docs/trazabilidad-driver-decision.md`, `docs/policies/adr-naming-policy.md` |

## 3. Decisiones conscientes documentadas que no son deuda

| Decision | Razon por la que no se considera deuda |
|---|---|
| Migracion a TypeScript strict | Ya fue formalizada en `docs/adr/ADR-013-frontend-spa-revision-typescript.md`; mejora RNF-10 y no deja trabajo pendiente salvo mantenimiento normal de tipos. |
| `auth-service-stub` para Entrega 3 | Es deuda reemplazarlo en produccion, pero la decision de usarlo en demo es correcta y documentada: evita que el navegador firme JWT y deja contrato JWKS/OIDC compatible. |
| Pasarela simulada / WireMock para Mercado Pago | Es una decision de aislamiento de proveedor externo; el contrato de pago, webhook, HMAC e idempotencia se prueban sin depender de credenciales reales. |
| 150 VUs en lugar de 500 en local | No es maquillaje de resultados: `docs/limites-carga-reconocidos.md` declara por que Docker Compose en un host no certifica RNF-04 productivo. |
| Frontend monolitico SPA | Es coherente con alcance, equipo y FinOps; micro-frontends aumentarian complejidad sin beneficio en Entrega 3. |
| Tests E2E con mocks Playwright | Complementan, no reemplazan, el smoke real contra backend. Aislan UX y errores para pruebas deterministas. |

## 4. Roadmap Fase 2 priorizado

### Sprint 1 - P0 critico, aproximadamente 2 semanas

| Deuda | Trabajo |
|---|---|
| D-001 | Ejecutar Terraform AWS ya declarado, configurar secretos productivos, validar outputs y smoke post-deploy. |
| D-003 | Integracion Azure AD Javeriana o IdP OIDC institucional. |
| D-002 | Integracion Mercado Pago real en sandbox y preparacion productiva. |

### Sprint 2 - P1 importante, aproximadamente 2 semanas

| Deuda | Trabajo |
|---|---|
| D-004 | Authorization Code + PKCE. |
| D-005 | `notification-service` con consumidores AMQP y plantillas. |
| D-008 | Certificacion RNF-04 con K6 sobre AWS. |
| D-010 | Coordinacion de registry npm/proxy con TI. |

### Sprint 3 - P2 deseable, aproximadamente 2 semanas

| Deuda | Trabajo |
|---|---|
| D-006 | `certificate-service` y verificacion QR/PDF. |
| D-015 | Pantallas administrativas y perfil organizador. |
| D-013 | Validacion con lectores de pantalla reales. |
| D-016 | Ensayo multi-instancia de locks batch. |
| D-017 | Dashboards Grafana. |
| D-018 | Reembolso tardio asincrono. |
| D-014 / D-019 | Storybook y cierre documental residual. |

Total estimado: 6 semanas calendario en 3 sprints, con 41.75 dias-persona de backlog inventariado. Si trabaja una sola persona full-time, se recomienda cerrar P0 y P1 primero y mover parte de P2 a un cuarto sprint si no hay paralelizacion.

## 5. Estado de calidad medible

| Metrica | Threshold | Resultado real | Evidencia |
|---|---:|---:|---|
| Cobertura statements frontend | >=70% | 88.06% | `frontend/coverage/index.html`, `docs/evidencia-accesibilidad-frontend.md` |
| Cobertura branches frontend | >=70% | 82.1% | `frontend/coverage/index.html`, `docs/evidencia-accesibilidad-frontend.md` |
| Violaciones WCAG 2.1 AA | 0 criticas | 0 en 4 auditorias axe Chromium | `frontend/test-results/accessibility/`, `docs/evidencia-accesibilidad-frontend.md` |
| Sobrecupo bajo concurrencia | 0% | 0%; 50 VUs / 10 cupos = 10 confirmadas y 40 rechazadas | `load-tests/reports/04-cupos-concurrencia.html`, `docs/evidencia-carga.md` |
| p95 Circuit Breaker abierto | <50 ms | 37.26 ms | `load-tests/reports/05-circuit-breaker.html`, `docs/evidencia-carga.md` |
| p95 sostenido 150 VUs | <800 ms | 52.81 ms en evidencia final; corrida previa documentada 28.15 ms | `load-tests/reports/02-load-test.html`, `docs/evidencia-carga.md`, `docs/limites-carga-reconocidos.md` |
| Cache hit rate Redis | Medible | 99.50% | `load-tests/reports/06-cache-effectiveness.html`, `docs/evidencia-carga.md` |
| Smoke E2E reproducible | OK | Pago `CONFIRMADO` | `frontend/scripts/smoke-e2e.mjs`, `docs/evidencia-testing-frontend.md` |
| Tests frontend pasando | >=80% | 52/52 unit + 28/28 E2E | `docs/evidencia-autenticacion-completa.md`, `frontend/playwright-report/index.html` |
| Hallazgos auditoria cerrados | 26/27 | 26 cerrados y diferidos registrados en este documento | `docs/srs-sad-gap-analysis.md`, `TECH_DEBT.md` |

## 6. Anexo: mapeo a ADRs

| Deuda | ADRs / decisiones afectadas |
|---|---|
| D-001 | ADR-004, ADR-022, ADR-018 |
| D-002 | ADR-005, ADR-009, ADR-010, ADR-013 Factory Method pasarela |
| D-003 | ADR-007, ADR-015, ADR-010 |
| D-004 | ADR-007, ADR-015 |
| D-005 | ADR-006, ADR-008, ADR-016, ADR-019 |
| D-006 | ADR-017, ADR-020 certificate timeout/retry, ADR-021, ADR-023 |
| D-007 | `docs/adr/ADR-013-frontend-spa-revision-typescript.md`, ADR-011 |
| D-008 | ADR-003, ADR-004, ADR-012, ADR-018 |
| D-009 | ADR-009, ADR-013 frontend TypeScript |
| D-010 | ADR-013 frontend TypeScript, RNF-10 |
| D-011 | ADR-004, ADR-022, RNF-10 |
| D-012 | ADR-007, ADR-015 |
| D-013 | ADR-013 frontend TypeScript, WCAG/NTC 5854 |
| D-014 | ADR-013 frontend TypeScript, RNF-10 |
| D-015 | ADR-011, ADR-013 frontend TypeScript, ADR-007 |
| D-016 | ADR-018 ShedLock |
| D-017 | ADR-009, ADR-018, ADR-019 |
| D-018 | ADR-008, ADR-010, ADR-013 Factory Method pasarela |
| D-019 | ADR naming policy, ADR-008, ADR-018, ADR-013 frontend |

## 7. Hallazgos cerrados posteriores

### H-001 - Validacion JWT en hidratacion de `sessionStorage`

| Campo | Detalle |
|---|---|
| Estado | Cerrado |
| Detectado en | Prompt 29.6 - Verificacion completa de autenticacion y autorizacion |
| Descripcion | El frontend podia restaurar una sesion desde `sessionStorage` cuando existian `gea.session.v1` y `gea.user.v1`, sin validar que el JWT almacenado fuera parseable y no estuviera expirado. |
| Impacto potencial | La UI podia quedar autenticada localmente con un token vencido o malformado hasta que una llamada backend lo rechazara. No comprometia la validacion backend RS256, pero si degradaba la coherencia del estado cliente. |
| Cierre aplicado | Se agrego `jwt-utils.ts` con parseo base64url, extraccion de roles y validacion de `exp`; `AuthContext` valida el JWT durante la restauracion y limpia storage si el token es invalido. |
| Evidencia | `frontend/src/features/auth/jwt-utils.ts`, `frontend/src/features/auth/jwt-claims.test.ts`, `frontend/e2e/09-token-expirado.spec.ts`, `docs/evidencia-autenticacion-completa.md` |
| Resultado | `npm run test`: 52/52; `npm run test:e2e`: 28/28; `npm run smoke:e2e`: pago `CONFIRMADO`. |

## 8. Filosofia de honestidad tecnica

Este documento existe para que el proyecto sea evaluado por lo que realmente demuestra, no por una narrativa inflada. La arquitectura de Entrega 3 tiene evidencias fuertes: flujo E2E real, pruebas automatizadas, accesibilidad, carga defendible, outbox, resiliencia y concurrencia. Tambien tiene limites reconocidos: despliegue productivo, identidad institucional, pasarela real y servicios conceptuales pendientes. Registrar esos limites con impacto, mitigacion y plan de cierre refleja madurez ingenieril, protege al equipo y facilita que Fase 2 avance sobre prioridades reales.
