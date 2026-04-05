# Matriz de Trazabilidad Extendida
## Plataforma de Gestión de Eventos Académicos — Pontificia Universidad Javeriana

**Versión:** 1.0  
**Fecha:** 2026-04-04  
**Autoras:** Tannia Hernández Rojas  
**Curso:** Diseño de Software Basado en Patrones

---

## 1. Propósito

Esta matriz vincula los Requisitos Funcionales (RF) con las Reglas de Negocio (RN), Casos de Uso (CU), Características Arquitectónicas (CA) y Decisiones de Arquitectura (ADR) que los hacen posibles. El objetivo es demostrar que cada requisito tiene un camino completo desde la necesidad del stakeholder hasta la decisión de diseño que la soporta, y que cada decisión de arquitectura se justifica por al menos un requisito.

---

## 2. Leyenda de identificadores

| Prefijo | Significado |
|---|---|
| RF-XX | Requisito Funcional |
| RNF-XX | Requisito No Funcional |
| RN-XX | Regla de Negocio |
| CU-XX | Caso de Uso (escenario principal) |
| CA-XX | Característica Arquitectónica (quality attribute) |
| ADR-XX | Architecture Decision Record |

---

## 3. Matriz principal RF → RN → CU → CA → ADR

### 3.1 Módulo de Autenticación y Autorización

| RF | Descripción RF | RN | CU | CA | ADR | Justificación de la traza |
|---|---|---|---|---|---|---|
| RF-01 | Autenticación con Google OIDC (@javeriana.edu.co) | RN-05: email debe terminar en @javeriana.edu.co para OIDC | CU-01: Iniciar sesión con Google | CA-01: Seguridad | ADR-01: OAuth 2.0/OIDC con Google Workspace | La restricción institucional (RN-05) obliga a federar identidad con Google; OIDC es el protocolo estándar para ello (ADR-01). |
| RF-01b | Registro manual para usuarios externos | RN-05: datos de identificación encriptados AES-256 (Ley 1581) | CU-02: Registro usuario externo | CA-01: Seguridad, CA-05: Cumplimiento legal | ADR-02: Cifrado AES-256 para datos personales sensibles | El cumplimiento de Ley 1581 (CA-05) requiere protección en reposo, materializándose en ADR-02. |
| RF-02 | Control de acceso basado en roles (RBAC) | RN-10: roles con scope de evento; ADMIN_SISTEMA siempre global | CU-03: Asignar rol a usuario | CA-01: Seguridad | ADR-03: RBAC con roles por evento en Auth Service | Sin RBAC por evento (ADR-03), un organizador del Evento A podría modificar el Evento B; RN-10 exige el scope. |

---

### 3.2 Módulo de Gestión de Eventos

| RF | Descripción RF | RN | CU | CA | ADR | Justificación de la traza |
|---|---|---|---|---|---|---|
| RF-10 | Crear y publicar un evento académico | RN-08: cupo_disponible ≥ 0 | CU-10: Crear evento, CU-11: Publicar evento | CA-02: Disponibilidad, CA-03: Integridad | ADR-04: Evento Service como microservicio independiente | El ciclo de vida del evento (borrador→publicado→finalizado) es lo suficientemente complejo para justificar un servicio propio (ADR-04). |
| RF-11 | Gestionar sesiones y tracks dentro de un evento | RN-09: sin solapamiento de sesiones en mismo espacio-horario | CU-12: Crear sesión, CU-13: Asignar espacio a sesión | CA-03: Integridad | ADR-04: Evento Service | La validación de solapamiento (RN-09) vive en la capa de dominio de Evento Service; no requiere microservicio adicional. |
| RF-12 | Gestionar disponibilidad de espacios físicos | RN-09 (mismo que RF-11) | CU-13: Asignar espacio a sesión | CA-03: Integridad | ADR-04: Evento Service | **Aclaración de frontera:** El sistema sólo gestiona disponibilidad temporal de espacios. La administración interna (mantenimiento, limpieza) está fuera del alcance (SRS §1.3). |
| RF-13 | Consultar catálogo de eventos con filtros | — (sin regla de negocio exclusiva) | CU-14: Buscar eventos | CA-04: Rendimiento (<300ms p95) | ADR-05: Redis cache para catálogo, ADR-06: CQRS para lecturas de catálogo | El RNF de <300ms (CA-04) no es alcanzable con consultas directas a PostgreSQL bajo carga; Redis (ADR-05) + CQRS (ADR-06) lo hacen posible. |

---

### 3.3 Módulo de Inscripción

| RF | Descripción RF | RN | CU | CA | ADR | Justificación de la traza |
|---|---|---|---|---|---|---|
| RF-20 | Inscribirse a un evento | RN-01: reservar cupo con SELECT FOR UPDATE; RN-02: timeout 15 min | CU-20: Inscribirse a evento | CA-02: Disponibilidad, CA-03: Integridad, CA-06: Concurrencia | ADR-07: Bloqueo pesimista para gestión de cupos; ADR-08: Inscription Service como microservicio | Sin bloqueo pesimista (ADR-07), dos usuarios simultáneos podrían obtener el último cupo (condición de carrera). RN-01 lo exige explícitamente. |
| RF-20b | Prevenir inscripciones duplicadas | RN-01 + idempotency_key en INSCRIPCION | CU-20 | CA-03: Integridad | ADR-09: Idempotency keys para operaciones de inscripción | Un usuario podría hacer doble clic o reintentar; la idempotency_key (ADR-09) garantiza exactamente-una-vez semántica. |
| RF-21 | Cancelar inscripción (con política de reembolso) | RN-02: cupo se libera si inscripcion.estado = EXPIRADA o CANCELADA | CU-21: Cancelar inscripción | CA-02: Disponibilidad | ADR-07: Bloqueo pesimista | La liberación de cupo en cancelación debe ser atómica con el cambio de estado para evitar cupos fantasma. |

---

### 3.4 Módulo de Pagos

| RF | Descripción RF | RN | CU | CA | ADR | Justificación de la traza |
|---|---|---|---|---|---|---|
| RF-22 | Procesar pago vía pasarela externa (MercadoPago/PayPal) | RN-03: idempotencia por referencia_externa; RN-04: no almacenar datos de tarjeta | CU-22: Realizar pago; CU-23: Procesar webhook de confirmación | CA-01: Seguridad, CA-03: Integridad | ADR-10: Integración con pasarela vía webhook; ADR-11: Outbox Pattern para eventos de dominio | El webhook puede llegar múltiples veces (retries de la pasarela); RN-03 + ADR-10 garantizan procesamiento idempotente. |
| RF-22b | Manejar timeout de pago (15 min) | RN-02: fecha_expiracion_pago | CU-24: Job de expiración de inscripciones | CA-02: Disponibilidad | ADR-12: Job programado para liberación de cupos | Sin el job (ADR-12), los cupos reservados pero no pagados quedarían bloqueados indefinidamente, reduciendo disponibilidad. |
| RF-22c | Emitir reembolso si pago llega tras expiración | RN-03 + estado REEMBOLSADO en PAGO | CU-23 (flujo alternativo) | CA-01: Seguridad | ADR-10 | Protege al usuario de cobros por inscripciones ya expiradas. |
| RF-23 | Definir tarifas diferenciadas por categoría de participante | Invariante TARIFA: una tarifa activa por (evento_id, aplica_a) en un momento dado | CU-25: Configurar tarifas de evento | CA-03: Integridad | ADR-04: Evento Service | Las tarifas son parte del dominio del evento; no justifican un servicio separado. |

---

### 3.5 Módulo de Certificados

| RF | Descripción RF | RN | CU | CA | ADR | Justificación de la traza |
|---|---|---|---|---|---|---|
| RF-30 | Generar certificado de participación en PDF | RN-06: asistencia ≥ umbral (default 80%) | CU-30: Generar certificado | CA-04: Rendimiento (generación asíncrona) | ADR-13: Certificate Service con procesamiento asíncrono; ADR-14: Almacenamiento en Amazon S3 | La generación de PDFs es CPU-intensiva; procesarla en el request-path degradaría la latencia de la API. ADR-13 la mueve a proceso asíncrono vía cola de mensajes. |
| RF-31 | Verificación pública de certificados vía QR | RN-06 + codigo_verificacion único en CERTIFICADO | CU-31: Verificar certificado por URL pública | CA-01: Seguridad, CA-03: Integridad | ADR-14: Amazon S3; ADR-15: Endpoint público de verificación | El código de verificación (UUID) en el QR permite validación sin exponer datos personales del usuario. |

---

### 3.6 Módulo de Ponencias (Call for Papers)

| RF | Descripción RF | RN | CU | CA | ADR | Justificación de la traza |
|---|---|---|---|---|---|---|
| RF-40 | Enviar propuesta de ponencia | — | CU-40: Enviar propuesta | CA-03: Integridad | ADR-04: Evento Service (sub-dominio de ponencias) | Las propuestas están acopladas al evento; viven en Evento Service o en un Review Service si el volumen lo justifica. |
| RF-41 | Evaluación doble ciego de propuestas | RN-07: evaluador no puede evaluar su propia propuesta | CU-41: Evaluar propuesta con rúbrica; CU-42: Ver resultado de evaluación | CA-01: Seguridad | ADR-16: Review Service con anonimización de ponente | El doble ciego (RN-07 + ADR-16) requiere que el evaluador no vea el nombre del ponente durante la revisión. |

---

### 3.7 Módulo de Notificaciones

| RF | Descripción RF | RN | CU | CA | ADR | Justificación de la traza |
|---|---|---|---|---|---|---|
| RF-50 | Enviar notificaciones por correo (inscripción, pago, certificado) | — | CU-50: Notificar confirmación de inscripción; CU-51: Notificar certificado disponible | CA-02: Disponibilidad, CA-04: Rendimiento | ADR-17: Notification Service asíncrono con cola de mensajes | Enviar email en el request-path de inscripción añade latencia y puntos de fallo. La cola (ADR-17) desacopla y garantiza entrega eventual. |
| RF-51 | Reenvío de notificaciones fallidas (DLQ) | — | CU-52: Reenviar notificación fallida (DLQ) | CA-02: Disponibilidad | ADR-17: DLQ (Dead Letter Queue) | Sin DLQ, un fallo de email server silencia permanentemente la notificación al usuario. |

---

## 4. Trazabilidad Requisitos No Funcionales → CA → ADR

| RNF | Descripción | CA | ADR | Métrica verificable |
|---|---|---|---|---|
| RNF-01 | Latencia catálogo < 300ms (p95) | CA-04: Rendimiento | ADR-05: Redis cache; ADR-06: CQRS lecturas | Medido con k6 en ambiente de staging; falla CI si p95 > 300ms |
| RNF-02 | Disponibilidad ≥ 99.5% mensual | CA-02: Disponibilidad | ADR-18: Circuit Breaker con Resilience4j; ADR-19: Docker + orquestación | Monitoreo con uptime check cada 30s; alertas en canal Slack de ops |
| RNF-03 | Tiempo inscripción < 2s (p95) bajo 200 usuarios concurrentes | CA-04: Rendimiento; CA-06: Concurrencia | ADR-07: Bloqueo pesimista; ADR-08: Inscription Service independiente | Carga con k6: 200 VUs simultáneos durante 5 minutos |
| RNF-04 | Datos personales encriptados en reposo (Ley 1581) | CA-05: Cumplimiento legal | ADR-02: AES-256 | Auditoría: dump de BD no debe exponer número_documento en claro |
| RNF-05 | Trazabilidad de operaciones de pago (auditoría) | CA-01: Seguridad | ADR-10: metadatos_pasarela en PAGO; ADR-11: Outbox Pattern | Log de cada webhook con timestamp, estado y referencia externa |
| RNF-06 | API < 500ms para el 99% de requests (excepto generación de PDF) | CA-04: Rendimiento | ADR-20: API Gateway con timeout de 5s por servicio downstream | Medido en APM (OpenTelemetry + Jaeger) |

---

## 5. Trazabilidad ADR → Requisitos que lo justifican

Esta vista inversa permite verificar que ninguna decisión arquitectónica es "especulativa" — cada ADR debe tener al menos un requisito que la motive.

| ADR | Título de la decisión | Motivado por | Alternativas rechazadas |
|---|---|---|---|
| ADR-01 | OAuth 2.0/OIDC con Google Workspace | RF-01, CA-01 | SAML (más complejo sin ganancia), credenciales propias (riesgo seguridad) |
| ADR-02 | Cifrado AES-256 para datos personales | RF-01b, RNF-04, CA-05 | Sin cifrado (viola Ley 1581); cifrado de columnas (PostgreSQL pgcrypto viable pero complejo) |
| ADR-03 | RBAC con scope de evento | RF-02, RN-10 | RBAC global (insuficiente para multi-organizador) |
| ADR-04 | Evento Service como microservicio | RF-10, RF-11, RF-23, RF-40 | Monolito (no escala por equipo ni por carga independiente de catálogo vs. gestión) |
| ADR-05 | Redis cache para catálogo | RNF-01, RF-13 | CDN (no maneja filtros dinámicos); sin cache (viola RNF-01 bajo carga) |
| ADR-06 | CQRS para lecturas de catálogo | RNF-01, RF-13 | Sin CQRS (modelo de lectura acoplado al de escritura, dificulta optimización) |
| ADR-07 | Bloqueo pesimista para cupos | RF-20, RN-01, RNF-03 | Bloqueo optimista (acepta colisiones, requiere retry UI — peor UX bajo alta concurrencia) |
| ADR-08 | Inscription Service independiente | RF-20, RF-21, RNF-03 | En Evento Service (acoplamiento de dominio; la carga de inscripciones no debe afectar el catálogo) |
| ADR-09 | Idempotency keys para inscripciones | RF-20b, RN-01 | Sin idempotencia (doble inscripción posible con retry de red) |
| ADR-10 | Integración con pasarela vía webhook | RF-22, RN-03 | Polling activo (más costoso y con mayor latencia de confirmación) |
| ADR-11 | Outbox Pattern para eventos de dominio | RF-22, RF-50 | Llamada directa a Notification Service (acoplamiento sincrónico; fallo de notif cancela pago) |
| ADR-12 | Job programado liberación de cupos | RF-22b, RN-02 | Sin job (cupos bloqueados indefinidamente; viola disponibilidad) |
| ADR-13 | Certificate Service asíncrono | RF-30, CA-04 | Generación síncrona (inaceptable para latencia de API) |
| ADR-14 | Almacenamiento PDF en Amazon S3 | RF-30, RF-31 | BD relacional para binarios (no escala); servidor de archivos propio (costo operativo) |
| ADR-15 | Endpoint público de verificación | RF-31 | Autenticación requerida para verificar (excluye terceros que verifican credenciales) |
| ADR-16 | Review Service con anonimización | RF-41, RN-07 | En Evento Service (mezcla dominio de evaluación con gestión; complica anonimización) |
| ADR-17 | Notification Service con cola + DLQ | RF-50, RF-51, CA-02 | Email sincrónico (punto de fallo en el request-path) |
| ADR-18 | Circuit Breaker con Resilience4j | RNF-02 | Sin circuit breaker (fallo en cadena si un servicio downstream cae) |
| ADR-19 | Docker + Docker Compose | RNF-02, restricción presupuestal $200/mes | Kubernetes (sobre-engineering para el equipo y presupuesto actuales; puede evolucionar) |
| ADR-20 | API Gateway Spring Cloud Gateway | RF-01, RNF-06 | NGINX (menos integración con Spring ecosystem; sin circuit breaker nativo) |

---

## 6. Cobertura: Requisitos sin traza (gaps identificados)

Los siguientes requisitos del SRS v1.0 aún no tienen todos los artefactos de traza completos y requieren desarrollo en próximas iteraciones:

| RF/RNF | Descripción | Gap | Acción requerida |
|---|---|---|---|
| RF-32 | Registro de asistencia por scan QR | Falta CU-32 detallado | Desarrollar escenario de uso con flujo de scan en app móvil |
| RNF-07 | Tiempo de generación de certificado < 30s | Sin ADR específico para timeout de cola | Añadir ADR para política de timeout y retry en Certificate Service |
| RF-60 | Reportes y analíticas para organizadores | Sin diseño de dominio para queries de analítica | Definir si usa CQRS mismo o requiere data warehouse separado |

---

## 7. Justificación de la Arquitectura de Microservicios

El profesor señaló que la elección de microservicios no está suficientemente justificada frente a alternativas. A continuación, la justificación estructurada:

### Por qué microservicios y no monolito modular

| Criterio | Monolito Modular | Microservicios (elegido) | Decisión |
|---|---|---|---|
| Equipos paralelos | Un solo pipeline de deploy bloquea todos los equipos | Cada servicio tiene deploy independiente | Microservicios |
| Escala diferenciada | Catálogo y procesamiento de pagos escalan igual | Catálogo (alto RPS) escala independiente de Certificate Service (bajo RPS, CPU-intensivo) | Microservicios |
| Fallo aislado | Fallo en generación de PDF puede derribar la API de inscripción | Circuit Breaker aísla el fallo | Microservicios |
| Complejidad operativa | Simpler deploy | Requiere orquestación (Docker Compose → Kubernetes eventualmente) | Monolito preferible si el equipo < 3 personas |
| Presupuesto | Menor infraestructura | $200/mes es alcanzable con Docker Compose en VPS o EC2 t3.medium | Microservicios viables con restricción presupuestal |

**Conclusión:** La escala diferenciada entre el catálogo de eventos (RF-13, RNF-01 <300ms) y los servicios de procesamiento offline (certificados, notificaciones) justifica el aislamiento. El monolito modular fue considerado y rechazado porque no permite escalar el cache de catálogo sin escalar el servicio de pagos.

### Costo estimado de infraestructura (Docker Compose en EC2)

| Componente | Instancia AWS | Costo mensual estimado (USD) |
|---|---|---|
| App Server (API Gateway + 6 microservicios) | EC2 t3.medium (2 vCPU, 4 GB) | ~$30 |
| PostgreSQL RDS | db.t3.micro (prod-small) | ~$15 |
| Redis ElastiCache | cache.t3.micro | ~$13 |
| Amazon S3 (certificados, imágenes) | 10 GB + 1M requests | ~$5 |
| Load Balancer ALB | — | ~$20 |
| Backups + transferencia de datos | — | ~$10 |
| **Total estimado** | | **~$93/mes** (margen hasta $200) |

Este costo está dentro del presupuesto declarado de $200/mes y deja margen para crecer.
