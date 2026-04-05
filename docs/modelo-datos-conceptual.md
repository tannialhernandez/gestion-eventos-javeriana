# Modelo de Datos Conceptual
## Plataforma de Gestión de Eventos Académicos — Pontificia Universidad Javeriana

**Versión:** 1.0  
**Fecha:** 2026-04-04  
**Autoras:** Tannia Hernández Rojas  
**Curso:** Diseño de Software Basado en Patrones

---

## 1. Introducción

Este documento define el modelo conceptual de datos de la plataforma, identificando las entidades del dominio, sus relaciones, cardinalidades y los estados de negocio críticos. Complementa el SRS v1.0 respondiendo a la necesidad de formalizar la estructura de información antes de comprometer diseño físico.

---

## 2. Diagrama de Entidades y Relaciones (Notación textual — ERD conceptual)

```
USUARIO ||--o{ ROL_USUARIO : "tiene asignado"
ROL ||--o{ ROL_USUARIO : "asignado a"
USUARIO ||--o{ PROPUESTA_PONENCIA : "presenta"
USUARIO ||--o{ INSCRIPCION : "realiza"
USUARIO ||--o{ EVALUACION_RUBRICA : "evalúa"

EVENTO ||--o{ SESION : "contiene"
EVENTO ||--o{ TARIFA : "define"
EVENTO }o--|| ORGANIZADOR : "gestionado por"
EVENTO ||--o{ INSCRIPCION : "recibe"

SESION }o--|| ESPACIO_FISICO : "se realiza en"
SESION ||--o{ PONENTE_SESION : "cuenta con"
PONENTE ||--o{ PONENTE_SESION : "participa en"
PONENTE ||--o{ PROPUESTA_PONENCIA : "presenta"

PROPUESTA_PONENCIA }o--o| SESION : "aprobada en"
PROPUESTA_PONENCIA ||--o{ EVALUACION_RUBRICA : "evaluada por"

INSCRIPCION ||--|| PAGO : "genera"
INSCRIPCION }o--|| TARIFA : "aplica"
INSCRIPCION ||--o| CERTIFICADO : "origina"
INSCRIPCION ||--o{ ASISTENCIA : "registra"

ASISTENCIA }o--|| SESION : "a"
```

---

## 3. Descripción de Entidades

### 3.1 USUARIO

Representa a cualquier persona que interactúa con la plataforma, autenticada vía OAuth 2.0/OIDC con Google Workspace (@javeriana.edu.co) o registro manual para externos.

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK, NOT NULL | Identificador único |
| email | VARCHAR(255) | UNIQUE, NOT NULL | Correo institucional o externo |
| nombre_completo | VARCHAR(200) | NOT NULL | Nombre para certificados y comunicaciones |
| tipo_documento | ENUM | NOT NULL | CC, CE, PA, TI |
| numero_documento | VARCHAR(30) | UNIQUE, NOT NULL | Número de identificación |
| telefono | VARCHAR(20) | NULL | Teléfono de contacto |
| afiliacion_institucional | VARCHAR(200) | NULL | Facultad, departamento o empresa |
| es_externo | BOOLEAN | NOT NULL, DEFAULT false | Diferencia usuarios externos de la comunidad Javeriana |
| estado | ENUM | NOT NULL | ACTIVO, INACTIVO, BLOQUEADO |
| fecha_creacion | TIMESTAMP | NOT NULL | Registro de creación |
| datos_encriptados | BOOLEAN | NOT NULL, DEFAULT true | Indica si datos sensibles están bajo AES-256 (Ley 1581) |

**Invariantes de negocio:**
- Un usuario con dominio @javeriana.edu.co sólo puede autenticarse vía Google OIDC.
- Los campos `tipo_documento` y `numero_documento` se encriptan en reposo (Ley 1581, Art. 17).
- Un usuario BLOQUEADO no puede realizar inscripciones ni pagos.

---

### 3.2 ROL

Define los permisos funcionales dentro del sistema (RBAC).

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador único |
| nombre | ENUM | UNIQUE, NOT NULL | ASISTENTE, PONENTE, ORGANIZADOR, EVALUADOR, ADMIN_SISTEMA |
| descripcion | TEXT | NULL | Descripción del alcance |

**Roles y alcance:**

| Rol | Capacidades principales |
|---|---|
| ASISTENTE | Inscribirse, pagar, ver certificados, registrar asistencia QR |
| PONENTE | Lo de ASISTENTE + presentar propuestas, ver agenda propia |
| ORGANIZADOR | Crear/editar eventos propios, gestionar sesiones y espacios |
| EVALUADOR | Revisar propuestas de ponencia, emitir evaluaciones con rúbrica |
| ADMIN_SISTEMA | Acceso total: usuarios, eventos de cualquier organizador, reportes |

---

### 3.3 ROL_USUARIO (tabla de asociación)

Permite que un usuario tenga múltiples roles y que un rol sea por-evento (un usuario puede ser ORGANIZADOR del Evento A y ASISTENTE del Evento B).

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador |
| usuario_id | UUID | FK → USUARIO | Usuario |
| rol_id | UUID | FK → ROL | Rol asignado |
| evento_id | UUID | FK → EVENTO, NULL | Scope del rol (NULL = global) |
| fecha_asignacion | TIMESTAMP | NOT NULL | Cuándo se asignó |
| asignado_por | UUID | FK → USUARIO | Quién lo asignó |

---

### 3.4 EVENTO

Entidad central de la plataforma. Representa un congreso, simposio, seminario u otro evento académico.

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador único |
| titulo | VARCHAR(300) | NOT NULL | Nombre oficial del evento |
| descripcion | TEXT | NOT NULL | Descripción extendida |
| tipo | ENUM | NOT NULL | CONGRESO, SIMPOSIO, SEMINARIO, TALLER, OTRO |
| modalidad | ENUM | NOT NULL | PRESENCIAL, VIRTUAL, HIBRIDO |
| fecha_inicio | DATE | NOT NULL | Fecha de apertura |
| fecha_fin | DATE | NOT NULL | Fecha de cierre (≥ fecha_inicio) |
| fecha_limite_inscripcion | TIMESTAMP | NOT NULL | Cierre de inscripciones |
| fecha_limite_propuestas | TIMESTAMP | NULL | Cierre de recepción de ponencias |
| cupo_maximo | INTEGER | NOT NULL, > 0 | Capacidad total del evento |
| cupo_disponible | INTEGER | NOT NULL | Cupos restantes (gestionado con bloqueo pesimista) |
| url_imagen | VARCHAR(500) | NULL | Imagen en Amazon S3 |
| url_evento_virtual | VARCHAR(500) | NULL | Link de transmisión (si aplica) |
| estado | ENUM | NOT NULL | Ver máquina de estados §4.1 |
| organizador_id | UUID | FK → USUARIO | Organizador principal |
| fecha_creacion | TIMESTAMP | NOT NULL | Registro |
| version | INTEGER | NOT NULL | Control de concurrencia optimista |

**Invariantes de negocio:**
- `fecha_fin >= fecha_inicio`.
- `cupo_disponible <= cupo_maximo`.
- Sólo ORGANIZADOR o ADMIN pueden modificar el evento.
- Un evento CANCELADO no puede volver a PUBLICADO.

---

### 3.5 SESION (Track / Charla)

Bloque de tiempo dentro de un evento. Un evento puede tener múltiples sesiones paralelas.

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador |
| evento_id | UUID | FK → EVENTO, NOT NULL | Evento contenedor |
| titulo | VARCHAR(300) | NOT NULL | Nombre de la sesión |
| descripcion | TEXT | NULL | Descripción |
| tipo | ENUM | NOT NULL | KEYNOTE, PANEL, TALLER, POSTER, NETWORKING |
| fecha_hora_inicio | TIMESTAMP | NOT NULL | Inicio |
| fecha_hora_fin | TIMESTAMP | NOT NULL | Fin (> inicio) |
| espacio_id | UUID | FK → ESPACIO_FISICO, NULL | Sala o sala virtual |
| url_transmision | VARCHAR(500) | NULL | URL sesión virtual |
| cupo_sesion | INTEGER | NULL | Límite propio (NULL = ilimitado dentro del evento) |
| estado | ENUM | NOT NULL | PROGRAMADA, EN_CURSO, FINALIZADA, CANCELADA |

**Invariantes:**
- Una sesión no puede solaparse en el mismo `espacio_id` al mismo tiempo (chequeado por regla de negocio en Inscription Service).
- `fecha_hora_fin > fecha_hora_inicio`.

---

### 3.6 ESPACIO_FISICO

Representa una sala, auditorio o laboratorio. **El sistema gestiona disponibilidad pero NO la administración interna del espacio** (reservas de limpieza, mantenimiento, etc. están fuera del alcance — ver SRS §1.3 Exclusiones).

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador |
| nombre | VARCHAR(100) | NOT NULL | Ej: "Auditorio Alfonso Borrero S.J." |
| edificio | VARCHAR(100) | NOT NULL | Edificio en campus Javeriana |
| capacidad_maxima | INTEGER | NOT NULL, > 0 | Aforo máximo permitido |
| equipamiento | TEXT[] | NULL | Lista: ["proyector", "microfono", "videoconferencia"] |
| activo | BOOLEAN | NOT NULL, DEFAULT true | Si está disponible para asignación |

**Regla de negocio clave:** El sistema valida disponibilidad temporal del espacio al programar una sesión, pero no gestiona reservas de servicios generales (fuera del alcance declarado).

---

### 3.7 TARIFA

Define los precios por categoría de participante para un evento.

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador |
| evento_id | UUID | FK → EVENTO, NOT NULL | Evento al que aplica |
| nombre | VARCHAR(100) | NOT NULL | Ej: "Estudiante pregrado Javeriana" |
| precio | DECIMAL(12,2) | NOT NULL, ≥ 0 | Precio en COP (0 = gratuito) |
| moneda | CHAR(3) | NOT NULL, DEFAULT 'COP' | ISO 4217 |
| aplica_a | ENUM | NOT NULL | ESTUDIANTE_JAVERIANA, DOCENTE_JAVERIANA, EXTERNO, PONENTE |
| fecha_inicio_vigencia | DATE | NOT NULL | Inicio de aplicación (precio anticipado) |
| fecha_fin_vigencia | DATE | NOT NULL | Fin de aplicación |
| activa | BOOLEAN | NOT NULL, DEFAULT true | Si está disponible para nuevas inscripciones |

**Invariante:** En un momento dado, sólo puede haber una tarifa activa por `(evento_id, aplica_a)`.

---

### 3.8 INSCRIPCION

Registra la participación de un usuario en un evento. Es la entidad de mayor complejidad de negocio.

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador |
| usuario_id | UUID | FK → USUARIO, NOT NULL | Participante |
| evento_id | UUID | FK → EVENTO, NOT NULL | Evento |
| tarifa_id | UUID | FK → TARIFA, NOT NULL | Tarifa aplicada al momento de inscripción |
| estado | ENUM | NOT NULL | Ver máquina de estados §4.2 |
| fecha_inscripcion | TIMESTAMP | NOT NULL | Momento de creación |
| fecha_expiracion_pago | TIMESTAMP | NULL | Deadline para pagar (= fecha_inscripcion + 15 min) |
| codigo_qr | VARCHAR(500) | NULL | Token QR para acceso y asistencia |
| idempotency_key | UUID | UNIQUE, NOT NULL | Previene inscripciones duplicadas por reintento |
| version | INTEGER | NOT NULL | Control de concurrencia optimista |

**Constraint UNIQUE:** `(usuario_id, evento_id)` — un usuario sólo puede inscribirse una vez por evento.

**Invariantes críticos:**
- Al crear una INSCRIPCION en estado `PENDIENTE_PAGO`, se descuenta 1 de `evento.cupo_disponible` usando bloqueo pesimista (`SELECT FOR UPDATE`).
- Si `fecha_expiracion_pago` pasa sin pago, un job programado mueve la inscripción a `EXPIRADA` y restaura el cupo.
- El `codigo_qr` se genera sólo cuando `estado = CONFIRMADA`.

---

### 3.9 PAGO

Registra la transacción financiera asociada a una inscripción. Hay exactamente un PAGO por INSCRIPCION (relación 1:1).

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador |
| inscripcion_id | UUID | FK → INSCRIPCION, UNIQUE | Inscripción asociada (1:1) |
| monto | DECIMAL(12,2) | NOT NULL | Monto cobrado |
| moneda | CHAR(3) | NOT NULL | ISO 4217 |
| pasarela | ENUM | NOT NULL | MERCADOPAGO, PAYPAL, TRANSFERENCIA_BANCARIA |
| referencia_externa | VARCHAR(200) | UNIQUE, NULL | ID de transacción en la pasarela |
| estado | ENUM | NOT NULL | Ver máquina de estados §4.3 |
| fecha_creacion | TIMESTAMP | NOT NULL | Momento de inicio del pago |
| fecha_confirmacion | TIMESTAMP | NULL | Momento de confirmación (webhook) |
| fecha_reembolso | TIMESTAMP | NULL | Si aplica |
| intentos_cobro | INTEGER | NOT NULL, DEFAULT 0 | Para idempotencia y alertas |
| metadatos_pasarela | JSONB | NULL | Payload original del webhook (auditoría) |

**Invariantes críticos:**
- `referencia_externa` debe ser UNIQUE: evita procesar el mismo webhook dos veces (idempotencia).
- Si `intentos_cobro >= 3` sin éxito → estado `FALLIDO`, se libera cupo.
- `metadatos_pasarela` se almacena cifrado en reposo (datos de tarjeta nunca se guardan — PCI DSS).

---

### 3.10 CERTIFICADO

Documento PDF generado para inscripciones con asistencia registrada.

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador |
| inscripcion_id | UUID | FK → INSCRIPCION, UNIQUE | Inscripción base (1:1) |
| codigo_verificacion | UUID | UNIQUE, NOT NULL | Token público de verificación QR |
| url_s3 | VARCHAR(500) | NULL | URL del PDF en Amazon S3 |
| estado | ENUM | NOT NULL | PENDIENTE_GENERACION, GENERANDO, DISPONIBLE, ERROR |
| fecha_generacion | TIMESTAMP | NULL | Momento de generación exitosa |
| horas_certificadas | DECIMAL(4,1) | NULL | Horas académicas acreditadas |

**Regla:** Sólo se genera si `inscripcion.estado = ASISTENCIA_REGISTRADA` y el porcentaje de asistencia ≥ umbral del evento (configurado por organizador, por defecto 80%).

---

### 3.11 PONENTE

Extiende la entidad USUARIO para participantes con rol de presentación. Un ponente puede tener múltiples propuestas en distintos eventos.

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador |
| usuario_id | UUID | FK → USUARIO, UNIQUE | Usuario base |
| bio | TEXT | NULL | Biografía para el programa del evento |
| url_foto | VARCHAR(500) | NULL | Foto en Amazon S3 |
| afiliacion | VARCHAR(200) | NULL | Institución actual |
| orcid | VARCHAR(30) | NULL | Identificador ORCID (investigadores) |

---

### 3.12 PROPUESTA_PONENCIA

Solicitud de presentación enviada por un ponente para un evento.

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador |
| ponente_id | UUID | FK → PONENTE, NOT NULL | Proponente |
| evento_id | UUID | FK → EVENTO, NOT NULL | Evento destino |
| titulo | VARCHAR(300) | NOT NULL | Título de la ponencia |
| resumen | TEXT | NOT NULL | Abstract (máx. 500 palabras) |
| palabras_clave | TEXT[] | NOT NULL | Mínimo 3 |
| tipo_presentacion | ENUM | NOT NULL | ORAL, POSTER, TALLER |
| estado | ENUM | NOT NULL | Ver máquina de estados §4.4 |
| sesion_asignada_id | UUID | FK → SESION, NULL | Sesión asignada tras aprobación |
| fecha_envio | TIMESTAMP | NOT NULL | Momento de envío |
| comentarios_evaluacion | TEXT | NULL | Retroalimentación consolidada |

---

### 3.13 EVALUACION_RUBRICA

Evaluación individual realizada por un evaluador sobre una propuesta de ponencia.

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador |
| propuesta_id | UUID | FK → PROPUESTA_PONENCIA, NOT NULL | Propuesta evaluada |
| evaluador_id | UUID | FK → USUARIO, NOT NULL | Evaluador asignado |
| puntaje_relevancia | INTEGER | NOT NULL, 1-5 | Relevancia temática |
| puntaje_originalidad | INTEGER | NOT NULL, 1-5 | Originalidad |
| puntaje_metodologia | INTEGER | NOT NULL, 1-5 | Rigor metodológico |
| puntaje_presentacion | INTEGER | NOT NULL, 1-5 | Claridad de presentación |
| puntaje_total | DECIMAL(4,2) | NOT NULL | Promedio ponderado calculado |
| recomendacion | ENUM | NOT NULL | ACEPTAR, ACEPTAR_CON_CAMBIOS, RECHAZAR |
| comentarios | TEXT | NULL | Retroalimentación cualitativa |
| fecha_evaluacion | TIMESTAMP | NOT NULL | Momento de envío |
| es_ciego | BOOLEAN | NOT NULL, DEFAULT true | Revisión doble ciego |

**Regla:** Un evaluador no puede evaluar su propia propuesta (verificado en capa de dominio).

---

### 3.14 ASISTENCIA

Registro de presencia de un inscrito en una sesión específica.

| Atributo | Tipo | Restricción | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador |
| inscripcion_id | UUID | FK → INSCRIPCION, NOT NULL | Inscripción del asistente |
| sesion_id | UUID | FK → SESION, NOT NULL | Sesión a la que asistió |
| fecha_hora_registro | TIMESTAMP | NOT NULL | Momento del scan QR |
| metodo_registro | ENUM | NOT NULL | QR_SCAN, MANUAL_ORGANIZADOR |
| registrado_por | UUID | FK → USUARIO, NULL | Si fue manual |

**Constraint UNIQUE:** `(inscripcion_id, sesion_id)` — no se registra el mismo asistente dos veces en la misma sesión.

---

## 4. Máquinas de Estados de Negocio

### 4.1 Estados de EVENTO

```
                    ┌─────────────────────────────────────────────┐
                    │                                             │
         [crear]    ▼      [enviar a revisión]                   │
   ──────► BORRADOR ──────────────────────► PENDIENTE_PUBLICACION │
                    │                              │              │
                    │ [publicar directamente]      │ [aprobar]    │
                    │ (solo ADMIN)                 ▼              │
                    └──────────────────────► PUBLICADO            │
                                                  │               │
                                    [fecha_fin    │ [cancelar]    │
                                     alcanzada]   │               │
                                         │        ▼               │
                                         │    CANCELADO ──────────┘
                                         │    (terminal)
                                         ▼
                                     FINALIZADO
                                     (terminal)
```

| Estado | Descripción | Transiciones permitidas |
|---|---|---|
| BORRADOR | En creación, no visible al público | → PENDIENTE_PUBLICACION, → CANCELADO |
| PENDIENTE_PUBLICACION | Esperando aprobación ADMIN | → PUBLICADO, → BORRADOR (con cambios), → CANCELADO |
| PUBLICADO | Visible, acepta inscripciones | → FINALIZADO (automático), → CANCELADO |
| FINALIZADO | Evento concluido, cierre de inscripciones | Terminal |
| CANCELADO | Evento cancelado, no acepta nuevas inscripciones | Terminal |

---

### 4.2 Estados de INSCRIPCION

```
                [iniciar inscripcion + reserva de cupo]
   ─────────────────────────► PENDIENTE_PAGO
                                    │         │
                     [pago exitoso] │         │ [timeout 15 min]
                     (webhook)      │         │ [o cancelación usuario]
                                    ▼         ▼
                               CONFIRMADA   EXPIRADA ──► [cupo liberado]
                                    │
                          [asistencia ≥ umbral]
                                    │
                                    ▼
                          ASISTENCIA_REGISTRADA
                                    │
                         [proceso asíncrono]
                                    │
                                    ▼
                           CERTIFICADO_EMITIDO
```

| Estado | Descripción | Cupo | Certificado |
|---|---|---|---|
| PENDIENTE_PAGO | Reserva activa, esperando pago | Descontado | No |
| CONFIRMADA | Pago verificado, acceso garantizado | Descontado | No (QR generado) |
| ASISTENCIA_REGISTRADA | Asistió al evento (≥ umbral) | Descontado | En proceso |
| CERTIFICADO_EMITIDO | Certificado disponible para descarga | Descontado | Sí |
| EXPIRADA | Timeout de pago alcanzado | **Liberado** | No |

**Flujo crítico de timeout:**
1. T=0: Se crea INSCRIPCION en `PENDIENTE_PAGO`, `fecha_expiracion_pago = NOW() + 15min`.
2. T=15min: Job programado (cada 1 minuto) detecta inscripciones expiradas.
3. Transacción atómica: `estado → EXPIRADA` + `evento.cupo_disponible += 1`.
4. Si el pago llega después de expiración: webhook rechazado, se emite reembolso automático.

---

### 4.3 Estados de PAGO

```
   ─────► INICIADO ──────────────────────► PROCESANDO
                │                               │
                │ [timeout pasarela]            │ [confirmación webhook]
                │                               │
                ▼                               ▼
            FALLIDO ◄────────────────────── CONFIRMADO
                                               │
                                     [solicitud reembolso]
                                               │
                                               ▼
                                          REEMBOLSADO
```

| Estado | Descripción |
|---|---|
| INICIADO | Sesión de pago creada en la pasarela |
| PROCESANDO | Pago en tránsito (esperando respuesta pasarela) |
| CONFIRMADO | Pago exitoso, webhook recibido y validado |
| FALLIDO | Error o timeout; cupo liberado si aplica |
| REEMBOLSADO | Reembolso procesado (cancelación posterior a pago) |

**Idempotencia:** Antes de procesar un webhook, se verifica `pago.referencia_externa` en la BD. Si ya existe en estado `CONFIRMADO`, se responde HTTP 200 sin reprocessing.

---

### 4.4 Estados de PROPUESTA_PONENCIA

```
   ─────► BORRADOR ──► ENVIADA ──────────────────────► EN_REVISION
                                                            │
                              ┌─────────────────────────────┤
                              │                             │
                              ▼                             ▼
                      ACEPTADA_CON_CAMBIOS              ACEPTADA ──► PROGRAMADA
                              │                             
                              │ [ponente reenvía]           
                              ▼                             
                          EN_REVISION                   RECHAZADA
                                                        (terminal)
```

---

## 5. Cardinalidades Resumidas

| Relación | Cardinalidad | Notas |
|---|---|---|
| USUARIO — ROL | M:N (via ROL_USUARIO) | Un usuario puede tener múltiples roles |
| USUARIO — EVENTO | 1:N (como organizador) | Un usuario puede organizar múltiples eventos |
| EVENTO — SESION | 1:N | Un evento tiene múltiples sesiones |
| SESION — ESPACIO_FISICO | N:1 | Múltiples sesiones pueden ocurrir en el mismo espacio (en momentos distintos) |
| EVENTO — TARIFA | 1:N | Un evento tiene múltiples tarifas por categoría |
| USUARIO — INSCRIPCION | 1:N | Un usuario puede inscribirse a múltiples eventos |
| EVENTO — INSCRIPCION | 1:N | Un evento recibe múltiples inscripciones |
| INSCRIPCION — PAGO | 1:1 | Cada inscripción genera exactamente un pago |
| INSCRIPCION — CERTIFICADO | 1:0..1 | Sólo inscripciones con asistencia generan certificado |
| INSCRIPCION — ASISTENCIA | 1:N | Una inscripción puede tener asistencia a múltiples sesiones |
| PONENTE — PROPUESTA_PONENCIA | 1:N | Un ponente puede presentar múltiples propuestas |
| PROPUESTA_PONENCIA — EVALUACION_RUBRICA | 1:N | Evaluación doble ciego: múltiples evaluadores |
| PROPUESTA_PONENCIA — SESION | 0..1:1 | Una propuesta aprobada se asigna a una sesión |

---

## 6. Reglas de Negocio Críticas (resumen)

| ID | Entidad | Regla |
|---|---|---|
| RN-01 | INSCRIPCION | Al crear, reservar cupo con `SELECT FOR UPDATE` en EVENTO |
| RN-02 | INSCRIPCION | Timeout de 15 minutos para completar el pago |
| RN-03 | PAGO | Idempotencia por `referencia_externa` en webhooks |
| RN-04 | PAGO | Nunca almacenar datos de tarjeta; sólo token y referencia externa |
| RN-05 | USUARIO | Datos de identificación encriptados AES-256 (Ley 1581) |
| RN-06 | CERTIFICADO | Generación sólo con asistencia ≥ umbral (default 80%) |
| RN-07 | EVALUACION_RUBRICA | Un evaluador no puede evaluar su propia propuesta |
| RN-08 | EVENTO | `cupo_disponible` no puede ser negativo |
| RN-09 | SESION | No puede haber solapamiento de sesiones en el mismo espacio y horario |
| RN-10 | ROL_USUARIO | Los roles pueden tener scope de evento; ADMIN_SISTEMA es siempre global |

---

## 7. Trazabilidad hacia el SRS

| Entidad | Requisito(s) relacionado(s) |
|---|---|
| USUARIO, ROL | RF-01 (autenticación), RF-02 (RBAC), RN-05 (Ley 1581) |
| EVENTO, SESION | RF-10 (creación evento), RF-11 (gestión sesiones) |
| ESPACIO_FISICO | RF-12 (gestión espacios — sólo disponibilidad) |
| INSCRIPCION, PAGO | RF-20 (inscripción), RF-21 (pago), RN-01, RN-02, RN-03 |
| CERTIFICADO | RF-30 (certificados), RF-31 (verificación QR) |
| PROPUESTA_PONENCIA, EVALUACION_RUBRICA | RF-40 (call for papers), RF-41 (evaluación doble ciego) |
| TARIFA | RF-22 (tarifas diferenciadas) |
| ASISTENCIA | RF-32 (registro asistencia QR) |
