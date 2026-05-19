# SRS — Casos de Uso Pendientes y Especificación Complementaria
## Plataforma de Gestión de Eventos Académicos — Pontificia Universidad Javeriana

**Versión:** 2.0 (continuación de SRS v1.0)  
**Fecha:** 2026-04-19  
**Autoras:** Tannia Hernández Rojas  
**Curso:** Diseño de Software Basado en Patrones

---

## 1. Propósito de este documento

Este documento cierra los gaps identificados en la Matriz de Trazabilidad v1.0 (§6 — Cobertura: Requisitos sin traza):

| Gap (de la matriz) | Sección que lo resuelve |
|---|---|
| RF-32: Registro de asistencia por scan QR — falta CU-32 detallado | §3 de este documento |
| RNF-07: Certificado < 30s — sin ADR de timeout y retry | §4 (complementa ADR-21 del SAD v2.0) |
| RF-60: Reportes para organizadores — sin diseño de dominio | §5 de este documento |
| Flujo de Call for Papers (CU-40 a CU-42) sin secuencias de runtime | §6 de este documento |
| Flujo de generación y verificación de certificados (CU-30/31) | §7 de este documento |

---

## 2. Requisitos pendientes de especificación completa

### 2.1 RF-32: Registro de asistencia por scan QR

**Descripción ampliada:** El sistema permite registrar la presencia de un participante inscrito en una sesión específica mediante el escaneo del código QR único generado al confirmar la inscripción. El registro puede hacerse también de forma manual por un organizador.

**Pre-condiciones:**
- El usuario tiene `inscripcion.estado = CONFIRMADA`.
- La sesión existe y su `estado = PROGRAMADA` o `EN_CURSO`.
- El evento está en curso (fecha_inicio ≤ hoy ≤ fecha_fin).
- El escaneo ocurre dentro del horario de la sesión ± 30 minutos de tolerancia.

**Flujo principal:**
1. El organizador escanea el código QR del participante con la app/web del sistema.
2. El sistema decodifica el token QR, extrae el `inscripcion_id`.
3. Inscription Service valida:
   a. La inscripción existe y está en estado `CONFIRMADA`.
   b. No existe ya un registro de `ASISTENCIA` para `(inscripcion_id, sesion_id)` (constraint UNIQUE).
4. Inscription Service llama `inscripcion.registrarAsistencia()` (método de dominio).
5. Se registra `ASISTENCIA {inscripcion_id, sesion_id, fecha_hora_registro, metodo_registro: QR_SCAN}`.
6. Si el porcentaje de sesiones con asistencia ≥ umbral del evento (default 80%):
   - Se actualiza `inscripcion.estado → ASISTENCIA_REGISTRADA`.
   - Se publica evento de dominio `InscripcionAsistenciaRegistradaEvent`.
7. El sistema retorna confirmación visual en pantalla del organizador.

**Flujo alternativo — Registro manual:**
- El organizador busca al participante por nombre o número de documento.
- Si lo encuentra con inscripción confirmada, registra asistencia con `metodo_registro: MANUAL_ORGANIZADOR`.
- Se requiere que `registrado_por = organizador_id` quede en el registro de auditoría.

**Flujos de excepción:**

| Condición | Respuesta del sistema |
|---|---|
| Código QR inválido o adulterado | HTTP 400 `codigo_qr_invalido` |
| Inscripción no encontrada | HTTP 404 |
| Inscripción en estado `PENDIENTE_PAGO` o `EXPIRADA` | HTTP 422 `inscripcion_no_activa`: "El participante no ha completado el pago." |
| Asistencia ya registrada para esta sesión | HTTP 409 `asistencia_ya_registrada` (idempotente: retorna el registro existente) |
| Sesión fuera del rango de tiempo permitido | HTTP 422 `sesion_fuera_de_horario` con `tolerance_minutes: 30` |

**Regla de cálculo del umbral de asistencia:**

```
sesiones_asistidas = COUNT(ASISTENCIA WHERE inscripcion_id = X)
total_sesiones_evento = COUNT(SESION WHERE evento_id = Y AND estado != CANCELADA)
porcentaje = (sesiones_asistidas / total_sesiones_evento) * 100

si porcentaje >= umbral_evento → inscripcion.registrarAsistencia() → ASISTENCIA_REGISTRADA
sino → estado permanece en CONFIRMADA (puede seguir asistiendo a sesiones)
```

**Nota:** Para eventos de sesión única (ej. seminario de 1 día), el umbral se cumple con 1 registro de asistencia. El organizador configura el umbral al crear el evento (valor por defecto: 80%).

---

### 2.2 RF-60: Reportes y analíticas para organizadores

**Descripción ampliada:** El organizador de un evento tiene acceso a un módulo de reportes en tiempo real que consolida métricas de inscripción, asistencia y recaudación.

**Requisitos de reporte:**

| Reporte | Datos | Frecuencia de actualización |
|---|---|---|
| R-01: Inscripciones por estado | PENDIENTE_PAGO, CONFIRMADA, EXPIRADA, CANCELADA — con totales y porcentajes | Tiempo real (query directa) |
| R-02: Ocupación del evento | Cupo total, cupos vendidos, cupos disponibles, % ocupación | Tiempo real |
| R-03: Recaudación | Total cobrado por tarifa, total por pasarela, total reembolsado | Tiempo real |
| R-04: Asistencia por sesión | Para cada sesión: inscritos esperados vs. presentes | Post-evento (tras fecha_fin) |
| R-05: Perfil de participantes | Distribución por tipo (estudiante, docente, externo) | Tiempo real |
| R-06: Exportación | Descarga en CSV o PDF del reporte completo | Bajo demanda |

**Decisión de diseño para RF-60 (cierre del gap en la matriz):**

Los reportes usan el **mismo modelo de datos relacional** con queries optimizadas. No se requiere un data warehouse separado en la fase actual por las siguientes razones:

1. El volumen esperado es ≤ 5,000 inscripciones por evento.
2. Las queries de reporte se ejecutan sobre `inscriptions_db` con JOINs indexados.
3. Los reportes del organizador no compiten con el path crítico de inscripción (ejecutan en réplica de lectura si el volumen lo justifica).

**Query representativa (R-03 recaudación):**

```sql
SELECT
    t.nombre                              AS tarifa,
    t.aplica_a                            AS categoria,
    COUNT(i.id)                           AS inscripciones_confirmadas,
    SUM(p.monto)                          AS total_cobrado,
    SUM(CASE WHEN p.estado = 'REEMBOLSADO' THEN p.monto ELSE 0 END) AS total_reembolsado,
    SUM(p.monto) - SUM(CASE WHEN p.estado = 'REEMBOLSADO' THEN p.monto ELSE 0 END) AS neto
FROM inscripcion i
    JOIN tarifa t ON t.id = i.tarifa_id
    JOIN pago p ON p.inscripcion_id = i.id
WHERE i.evento_id = :eventoId
    AND i.estado IN ('CONFIRMADA', 'ASISTENCIA_REGISTRADA', 'CERTIFICADO_EMITIDO')
    AND p.estado = 'CONFIRMADO'
GROUP BY t.nombre, t.aplica_a
ORDER BY total_cobrado DESC;
```

**API de reportes (Event Service, sub-módulo de analítica):**

| Método | Path | Descripción |
|---|---|---|
| `GET` | `/api/v1/eventos/{id}/reportes/inscripciones` | R-01 y R-02 |
| `GET` | `/api/v1/eventos/{id}/reportes/recaudacion` | R-03 |
| `GET` | `/api/v1/eventos/{id}/reportes/asistencia` | R-04 |
| `GET` | `/api/v1/eventos/{id}/reportes/participantes` | R-05 |
| `GET` | `/api/v1/eventos/{id}/reportes/exportar?formato=csv` | R-06 |

**Control de acceso:** Solo ORGANIZADOR del evento y ADMIN_SISTEMA pueden acceder a los reportes. Los datos financieros (R-03) solo son visibles para ADMIN_SISTEMA.

---

## 3. CU-32 — Registrar Asistencia por Código QR

**Nombre:** Registrar Asistencia QR  
**Actor principal:** Organizador / Auxiliar de registro  
**Actor secundario:** Sistema (valida QR y calcula umbral)  
**Precondición:** Organizador autenticado con rol ORGANIZADOR para el evento en cuestión.  
**Postcondición éxito:** Registro de ASISTENCIA creado; si umbral cumplido, inscripción → ASISTENCIA_REGISTRADA y evento `InscripcionAsistenciaRegistradaEvent` publicado.

### 3.1 Flujo principal de éxito

| Paso | Actor | Acción |
|---|---|---|
| 1 | Organizador | Abre la app de registro y selecciona la sesión en curso. |
| 2 | Organizador | Activa la cámara y enfoca el código QR del participante. |
| 3 | Sistema | Decodifica el QR: extrae `inscripcion_id` y `hmac_firma`. |
| 4 | Sistema | Valida la firma HMAC del QR contra la clave del evento (previene QR falsificados). |
| 5 | Sistema | Consulta la inscripción: verifica `estado = CONFIRMADA`. |
| 6 | Sistema | Verifica que no existe ASISTENCIA previa para `(inscripcion_id, sesion_id)`. |
| 7 | Sistema | Verifica que el horario actual esté dentro del rango permitido para la sesión. |
| 8 | Sistema | Crea registro ASISTENCIA con `metodo_registro = QR_SCAN`. |
| 9 | Sistema | Calcula porcentaje de asistencia; si ≥ umbral → actualiza inscripción. |
| 10 | Sistema | Retorna confirmación con nombre del participante y foto de perfil (si disponible). |
| 11 | Organizador | Ve en pantalla: "✓ Andrés Torres — Asistencia registrada" con marca de tiempo. |

### 3.2 Flujos alternativos

**3.2.A — QR ya escaneado (idempotencia):**

| Paso | Acción |
|---|---|
| 6a | El sistema detecta que ya existe ASISTENCIA para `(inscripcion_id, sesion_id)`. |
| 6b | Retorna HTTP 200 con el registro existente (no crea duplicado). |
| 6c | Muestra en pantalla: "ℹ Andrés Torres — Asistencia ya registrada (10:35)". |

**3.2.B — QR de inscripción no pagada:**

| Paso | Acción |
|---|---|
| 5a | El sistema detecta `inscripcion.estado = PENDIENTE_PAGO` o `EXPIRADA`. |
| 5b | Retorna HTTP 422. |
| 5c | Muestra en pantalla: "✗ Participante sin pago confirmado. No puede acceder." |

**3.2.C — Registro manual:**

| Paso | Acción |
|---|---|
| 2a | El organizador elige "Registro manual" e ingresa el número de documento del participante. |
| 3a | El sistema busca al participante por `numero_documento` en `usuarios` JOIN `inscripciones WHERE evento_id = :id`. |
| 4a-11a | Flujo idéntico al principal desde el paso 5, con `metodo_registro = MANUAL_ORGANIZADOR`. |

### 3.3 Estructura del código QR

El código QR codifica un JSON firmado con HMAC-SHA256:

```json
{
  "inscripcionId": "uuid",
  "usuarioId": "uuid",
  "eventoId": "uuid",
  "emitidoEn": "2026-05-10T09:00:00Z",
  "firma": "hmac-sha256-hex"
}
```

La clave de firma es `SECRET_QR_KEY` configurada por evento (variable de entorno del Inscription Service). Esto garantiza que un QR de un evento no es válido en otro evento.

### 3.4 Diagrama de secuencia — Registro QR exitoso

```
Organizador App    API Gateway    Inscription Svc    PostgreSQL     Message Queue
     │                  │                │                │               │
     │── POST /inscripciones/{id}/asistencia             │               │
     │   ?sesionId=uuid, body:{qrToken}  │                │               │
     │                  │── validar JWT, rol ORGANIZADOR  │               │
     │                  │── enrutar ────────►             │               │
     │                  │                │                │               │
     │                  │                │── validar firma HMAC del QR    │
     │                  │                │── SELECT inscripcion WHERE id AND estado=CONFIRMADA ──►
     │                  │                │                │◄── {inscripcion, estado: CONFIRMADA}
     │                  │                │── CHECK UNIQUE(inscripcion_id, sesion_id) ──────────►
     │                  │                │                │◄── no existe
     │                  │                │── BEGIN TRANSACTION            │
     │                  │                │── INSERT INTO asistencia ───────►
     │                  │                │── calcular_porcentaje_asistencia ─►
     │                  │                │               │◄── porcentaje: 85%
     │                  │                │── [umbral=80%] UPDATE inscripcion SET estado=ASISTENCIA_REGISTRADA ──►
     │                  │                │── INSERT INTO outbox_events {INSCRIPCION_ASISTENCIA_REGISTRADA}──►
     │                  │                │── COMMIT ───────────────────────►
     │                  │                │                │               │
     │◄── HTTP 200 ─────────────────────│                │               │
     │  {participante: "Andrés Torres",  │                │               │
     │   sesion: "Keynote Opening",      │                │               │
     │   registradoEn: "10:35:02"}      │                │               │
     │                  │                │                │               │
     │                  │                │── [Outbox Relay publica] ──────►
     │                  │                │                │◄── [Certificate Svc consumirá
     │                  │                │                │     ASISTENCIA_REGISTRADA]
```

---

## 4. CU-30 — Generar Certificado de Participación

**Nombre:** Generar Certificado PDF  
**Actor principal:** Sistema (proceso asíncrono)  
**Actor que desencadena:** Certificate Service al consumir `InscripcionAsistenciaRegistradaEvent`  
**Precondición:** `inscripcion.estado = ASISTENCIA_REGISTRADA` (umbral cumplido).  
**Postcondición éxito:** PDF generado, subido a S3, `certificado.estado = DISPONIBLE`, evento `CertificadoDisponible` publicado.

### 4.1 Flujo de generación asíncrona

```
Certificate Service consume INSCRIPCION_ASISTENCIA_REGISTRADA
    │
    ├── 1. Buscar o crear registro CERTIFICADO {estado: PENDIENTE_GENERACION}
    │
    ├── 2. Obtener datos del evento y participante (llamada HTTP a event-service y auth-service)
    │
    ├── 3. Actualizar estado → GENERANDO
    │
    ├── 4. Generar PDF con iText 8:
    │      • Encabezado institucional (logo Javeriana)
    │      • "La Pontificia Universidad Javeriana certifica que [NOMBRE COMPLETO]"
    │      • "con documento [TIPO] [NUMERO] participó en [TITULO EVENTO]"
    │      • "celebrado del [FECHA_INICIO] al [FECHA_FIN] con una intensidad de [HORAS]h"
    │      • Código QR de verificación (URL: /public/verificar/{codigo_verificacion})
    │      • Firma digital del organizador
    │      • Fecha de emisión
    │
    ├── 5. Subir PDF a Amazon S3:
    │      bucket: eventos-certificados-javeriana
    │      key: {evento_id}/{usuario_id}/{certificado_id}.pdf
    │      ACL: private (acceso vía URL pre-firmada de 1 hora)
    │
    ├── 6. Actualizar CERTIFICADO:
    │      estado → DISPONIBLE
    │      url_s3 = S3 key
    │      fecha_generacion = NOW()
    │      horas_certificadas = calculadas según duración del evento
    │
    └── 7. Publicar evento CertificadoDisponibleEvent → Notification Service envía email
```

**Manejo de errores (ADR-21):**

```
Si cualquier paso falla:
→ Reintentar con backoff exponencial: 5s, 15s, 45s (máx. 3 reintentos)
→ Si falla en los 3 intentos: certificado.estado = FALLO_DEFINITIVO
→ Alerta al ADMIN_SISTEMA (email o canal de monitoreo)
→ Operador puede reintentar manualmente: POST /api/v1/admin/certificados/{id}/reintentar
```

---

## 5. CU-31 — Verificar Certificado por QR

**Nombre:** Verificar Certificado  
**Actor principal:** Tercero (empleador, institución que valida el certificado)  
**Precondición:** El tercero tiene el código QR del certificado o la URL de verificación.  
**Postcondición:** El tercero ve información pública del certificado (sin datos sensibles del participante).

### 5.1 Flujo principal

| Paso | Actor | Acción |
|---|---|---|
| 1 | Tercero | Escanea el QR del certificado físico o hace clic en el enlace del PDF. |
| 2 | Browser | Abre: `https://eventos.javeriana.edu.co/public/verificar/{codigo_verificacion}` |
| 3 | Sistema | Busca el CERTIFICADO por `codigo_verificacion` (UUID público). |
| 4 | Sistema | Si existe y `estado = DISPONIBLE`: retorna página HTML con datos públicos. |
| 5 | Tercero | Ve: Nombre del evento, fechas, institución, nombre del participante, horas certificadas. |

**Datos visibles en la verificación pública:**

```
CERTIFICADO VERIFICADO ✓

Nombre del evento: XVI Congreso de Ingeniería de Sistemas
Institución emisora: Pontificia Universidad Javeriana
Participante: Andrés Torres (nombre completo — no número de documento)
Categoría: Asistente
Fechas: 10 al 12 de mayo de 2026
Intensidad: 24 horas académicas
Fecha de emisión: 15 de mayo de 2026
Código de verificación: [uuid]
```

**Seguridad:** El `codigo_verificacion` es un UUID v4 generado aleatoriamente. No expone el `usuario_id` ni el `inscripcion_id`. La URL no requiere autenticación (acceso público para terceros verificadores).

---

## 6. CU-40 a CU-42 — Call for Papers

### 6.1 CU-40 — Enviar Propuesta de Ponencia

**Actor:** Ponente (usuario con rol PONENTE)  
**Precondición:** El evento está en estado `PUBLICADO` y la `fecha_limite_propuestas` no ha pasado.

**Flujo principal:**

| Paso | Actor | Acción |
|---|---|---|
| 1 | Ponente | Accede al evento y elige "Enviar propuesta". |
| 2 | Ponente | Completa el formulario: título, resumen (máx. 500 palabras), palabras clave (mín. 3), tipo de presentación. |
| 3 | Sistema | Valida campos obligatorios y límites de longitud. |
| 4 | Sistema | Crea `PROPUESTA_PONENCIA {estado: ENVIADA, ponente_id, evento_id}`. |
| 5 | Sistema | Notifica al organizador del evento sobre la nueva propuesta. |
| 6 | Ponente | Recibe confirmación con el ID de la propuesta y número de radicado. |

**Flujo alternativo — Revisión antes de enviar:**
- El ponente puede guardar como `BORRADOR` y editar antes de enviar.
- Solo puede haber una propuesta por `(ponente_id, evento_id)`.

### 6.2 CU-41 — Evaluar Propuesta con Rúbrica (Doble Ciego)

**Actor:** Evaluador (usuario con rol EVALUADOR asignado al evento)  
**Precondición:** La propuesta está en estado `EN_REVISION`. El evaluador tiene acceso pero NO ve el nombre del ponente (ADR-22).

**Flujo principal:**

| Paso | Actor | Acción |
|---|---|---|
| 1 | Evaluador | Accede a la lista de propuestas asignadas (sistema le muestra sólo propuestas para revisar). |
| 2 | Evaluador | Selecciona una propuesta — ve título, resumen y palabras clave SIN datos del ponente. |
| 3 | Evaluador | Completa la rúbrica: relevancia (1-5), originalidad (1-5), metodología (1-5), presentación (1-5). |
| 4 | Evaluador | Elige recomendación: ACEPTAR / ACEPTAR_CON_CAMBIOS / RECHAZAR. |
| 5 | Evaluador | Opcionalmente agrega comentarios cualitativos al ponente. |
| 6 | Sistema | Guarda `EVALUACION_RUBRICA`, calcula `puntaje_total = promedio ponderado`. |
| 7 | Sistema | Si todas las evaluaciones asignadas están completas → cambia propuesta a estado `ACEPTADA` o `RECHAZADA` según consenso. |

**Regla de consenso:**
- Si todas las recomendaciones son `ACEPTAR`: propuesta → `ACEPTADA`.
- Si alguna es `RECHAZAR` y ninguna es `ACEPTAR`: propuesta → `RECHAZADA`.
- Si hay `ACEPTAR_CON_CAMBIOS`: propuesta → `ACEPTADA_CON_CAMBIOS`, se notifica al ponente.

**Protección contra conflicto de interés (RN-07):**

```java
// En la capa de aplicación, antes de asignar evaluador:
public void asignarEvaluador(UUID propuestaId, UUID evaluadorId) {
    Propuesta propuesta = propuestaRepository.buscarPorId(propuestaId);

    if (propuesta.getPonenteId().equals(evaluadorId)) {
        throw new BusinessRuleViolationException(
            "RN-07",
            "El evaluador no puede revisar su propia propuesta"
        );
    }
    // ... proceder con la asignación
}
```

### 6.3 CU-42 — Ver Resultado de Evaluación

**Actor:** Ponente  
**Precondición:** La propuesta ha completado el proceso de evaluación.

**Flujo principal:**

| Paso | Actor | Acción |
|---|---|---|
| 1 | Ponente | Accede a "Mis propuestas". |
| 2 | Sistema | Muestra lista de propuestas con su estado actual. |
| 3 | Ponente | Selecciona una propuesta en estado `ACEPTADA`, `ACEPTADA_CON_CAMBIOS` o `RECHAZADA`. |
| 4 | Sistema | Muestra: estado, comentarios consolidados de evaluación, puntaje promedio (sin revelar evaluadores individuales). |
| 5 | Si `ACEPTADA_CON_CAMBIOS` | Ponente actualiza la propuesta y reenvía para segunda revisión. |

**Datos visibles por el ponente (resumen anónimo):**

```
Estado: ACEPTADA CON CAMBIOS

Puntaje promedio: 3.8 / 5.0
Número de evaluaciones: 2

Comentarios consolidados:
"El trabajo presenta un enfoque novedoso pero requiere mayor 
 rigor en la sección de metodología. Se recomienda ampliar la 
 revisión de literatura de los últimos 5 años."

Próximo paso: Puede actualizar y reenviar hasta 2026-06-01.
```

---

## 7. Diagrama de Secuencia — Flujo de Certificados y Asistencia

```
Organizador     Inscription Svc    PostgreSQL     Message Queue    Certificate Svc    S3        Notification Svc
     │                │                │               │                │              │               │
     │── POST /asistencia              │               │                │              │               │
     │   {sesionId, qrToken}           │               │                │              │               │
     │                │── validar QR   │               │                │              │               │
     │                │── BEGIN TX     │               │                │              │               │
     │                │── INSERT asistencia ──────────►                 │              │               │
     │                │── UPDATE inscripcion → ASISTENCIA_REGISTRADA ──►               │               │
     │                │── INSERT outbox {INSCRIPCION_ASISTENCIA_REGISTRADA} ──────────►               │
     │                │── COMMIT ───────────────────────►               │              │               │
     │◄── HTTP 200 ───│                │               │                │              │               │
     │                │                │               │                │              │               │
     │                │  [OutboxRelay publica evento] ─────────────────►│              │               │
     │                │                │               │◄── consume INSCRIPCION_ASISTENCIA_REGISTRADA
     │                │                │               │                │              │               │
     │                │                │               │                │── obtener datos evento/usuario (HTTP)
     │                │                │               │                │── generar PDF (iText) ─────────────►
     │                │                │               │                │◄── (proceso local, ~5-20s)        │
     │                │                │               │                │── PUT PDF ──────────────────────────►
     │                │                │               │                │◄── S3 URL                          │
     │                │                │               │                │── UPDATE certificado.estado=DISPONIBLE
     │                │                │               │                │── INSERT outbox {CERTIFICADO_DISPONIBLE}
     │                │                │               │                │              │               │
     │                │                │  [OutboxRelay publica] ────────────────────────────────────────►
     │                │                │               │                │              │◄── consume CERTIFICADO_DISPONIBLE
     │                │                │               │                │              │── enviar email con URL descarga
```

### 7.1 Polling de estado de certificado (UX)

El frontend puede consultar el estado del certificado mientras se genera:

```
GET /api/v1/certificados/{inscripcionId}/estado

Response durante generación:
{
  "estado": "GENERANDO",
  "mensaje": "Tu certificado está siendo generado. Estará listo en unos momentos.",
  "porcentaje": null
}

Response cuando está listo:
{
  "estado": "DISPONIBLE",
  "urlDescarga": "https://eventos.javeriana.edu.co/api/v1/certificados/{id}/descargar",
  "horasCertificadas": 24.0,
  "codigoVerificacion": "uuid-publico"
}
```

---

## 8. Requisitos No Funcionales — Complemento

### 8.1 RNF-07 (cierre del gap)

**Requisito:** El certificado debe estar disponible para descarga en menos de 30 segundos desde que se registra la asistencia.

**Verificación:** Test de integración con Testcontainers que mide el tiempo entre `INSERT asistencia` y `certificado.estado = DISPONIBLE`.

**Criterio de aceptación:**
```
Dado que una inscripción pasa a ASISTENCIA_REGISTRADA
Cuando el Certificate Service consume el evento
Entonces el certificado debe estar en estado DISPONIBLE en ≤ 30 segundos (p95)
```

**Estrategia de cumplimiento:**
- Timeout de 25s por generación de PDF (5s de margen).
- iText genera el PDF en memoria (<2s para un certificado estándar de 1 página).
- La latencia principal es el upload a S3: configurar S3 Transfer Acceleration si supera 5s.
- Objetivo realista: < 10s en condiciones normales.

### 8.2 RNF-08 (nuevo): Seguridad del endpoint de verificación

**Requisito:** El endpoint público de verificación de certificados (`/public/verificar/{codigo}`) debe ser resistente a enumeración. Un atacante no debe poder descubrir certificados válidos iterando UUIDs.

**Solución:** El `codigo_verificacion` es un UUID v4 aleatorio (122 bits de entropía). La probabilidad de adivinar un UUID válido es astronomicamente baja (~1 en 2^122). Adicionalmente, se aplica rate limiting en el API Gateway: máximo 60 consultas por minuto por IP.

### 8.3 RNF-09 (nuevo): Disponibilidad de certificados en S3

**Requisito:** Los certificados almacenados en S3 deben estar disponibles por al menos 5 años desde la fecha del evento.

**Solución:** Política de S3 Lifecycle:
```json
{
  "Rules": [{
    "ID": "CertificadosRetention",
    "Status": "Enabled",
    "Filter": { "Prefix": "certificados/" },
    "Expiration": { "Days": 1825 },
    "Transitions": [{
      "Days": 365,
      "StorageClass": "STANDARD_IA"
    }]
  }]
}
```

---

## 9. Casos de Uso — Resumen de cobertura final

| Módulo | CU | Nombre | Estado de especificación |
|---|---|---|---|
| Autenticación | CU-01 | Iniciar sesión con Google | SRS v1.0 |
| Autenticación | CU-02 | Registro usuario externo | SRS v1.0 |
| Autenticación | CU-03 | Asignar rol a usuario | SRS v1.0 |
| Eventos | CU-10 | Crear evento | SRS v1.0 |
| Eventos | CU-11 | Publicar evento | SRS v1.0 |
| Eventos | CU-12 | Crear sesión | SRS v1.0 |
| Eventos | CU-13 | Asignar espacio a sesión | SRS v1.0 |
| Eventos | CU-14 | Buscar eventos (catálogo) | SRS v1.0 |
| Inscripción | CU-20 | Inscribirse a un evento | SRS v1.0 + comportamiento-runtime v1.0 |
| Inscripción | CU-21 | Cancelar inscripción | SRS v1.0 |
| Pagos | CU-22 | Realizar pago | SRS v1.0 + comportamiento-runtime v1.0 |
| Pagos | CU-23 | Procesar webhook de confirmación | SRS v1.0 + comportamiento-runtime v1.0 |
| Pagos | CU-24 | Job de expiración de inscripciones | SRS v1.0 + comportamiento-runtime v1.0 |
| Pagos | CU-25 | Configurar tarifas de evento | SRS v1.0 |
| Certificados | CU-30 | Generar certificado | **SRS v2.0 (este documento §4)** |
| Certificados | CU-31 | Verificar certificado por URL pública | **SRS v2.0 (este documento §5)** |
| Asistencia | CU-32 | Registrar asistencia por QR | **SRS v2.0 (este documento §3)** |
| Ponencias | CU-40 | Enviar propuesta de ponencia | **SRS v2.0 (este documento §6.1)** |
| Ponencias | CU-41 | Evaluar propuesta con rúbrica | **SRS v2.0 (este documento §6.2)** |
| Ponencias | CU-42 | Ver resultado de evaluación | **SRS v2.0 (este documento §6.3)** |
| Notificaciones | CU-50 | Notificar confirmación de inscripción | SRS v1.0 |
| Notificaciones | CU-51 | Notificar certificado disponible | SRS v1.0 |
| Notificaciones | CU-52 | Reenviar notificación desde DLQ | SRS v1.0 |
| Reportes | CU-60 | Ver reportes de inscripción y recaudación | **SRS v2.0 (este documento §2.2)** |

---

## 10. Trazabilidad adicional — Nuevos requisitos hacia ADRs

| Req. | Descripción | ADR nuevo | Justificación |
|---|---|---|---|
| RNF-07 | Certificado < 30s | ADR-21 (SAD v2.0 §7) | Timeout + retry policy para iText + S3 |
| RF-32 | QR scan de asistencia | ADR-22 (SAD v2.0 §7) | HMAC-SHA256 para firma del token QR; previene falsificaciones |
| RF-60 | Reportes organizadores | Ninguno nuevo | Queries SQL sobre BD existente; no requiere decisión arquitectónica separada |
| RNF-08 | Resistencia a enumeración de verificación | ADR-05 (rate limiting en Gateway) | Complemento del ADR existente de API Gateway |
| RNF-09 | Retención de certificados 5 años | ADR-14 (S3, extensión) | Política de Lifecycle de S3 añadida como extensión de ADR-14 |

---

## 11. Reglas de Negocio Propuestas — Pendiente aprobación del product owner

Las siguientes reglas surgieron durante la implementación del código. No están formalizadas en el SRS v1.0 ni v2.0, pero están respaldadas implícitamente por las reglas y diagramas de estado existentes. Se proponen para aprobación y eventual incorporación al SRS v3.0.

### RN-PAGO-05 — Transición REEMBOLSADO solo desde estados no-finales (pago tardío)

| Atributo | Valor |
|---|---|
| **Código** | RN-PAGO-05 |
| **Estado** | PROPUESTA — pendiente aprobación |
| **Módulo** | Pagos |
| **Servicio** | `payment-service` |
| **Implementado en** | `Pago.reembolsarPorExpiracion()` (commit: fix(payment-service): handle late payment refund flow) |

**Enunciado:**
> Un pago en estado final (`CONFIRMADO`, `FALLIDO`, `REEMBOLSADO`) no puede ser reembolsado por expiración de inscripción. La transición a `REEMBOLSADO` vía pago tardío solo es válida desde estados no-finales (`INICIADO`, `PROCESANDO`).

**Respaldo documental:**
- `srs-seccion9-modelo-datos-seccion10-trazabilidad.md` §9.3.3: la máquina de estados define `REEMBOLSADO` solo como salida de `CONFIRMADO` en el flujo normal; no contempla transición directa desde estados finales.
- `comportamiento-runtime-inscripcion-pago.md` §4.3: *"La expiración y la liberación de cupo son una sola transacción. No puede haber estado inconsistente."*
- `comportamiento-runtime-inscripcion-pago.md` §5 (Resumen de garantías): *"Webhook llega después de que expiró → Pago rechazado + reembolso automático."*

**Escenario cubierto:**
El webhook de confirmación de la pasarela llega después de que el job de `inscription-service` expiró la inscripción (timeout RN-10, 15 min). El pago fue aprobado por la pasarela pero la inscripción ya no existe. El sistema debe reembolsarlo sin intentar confirmar una inscripción expirada.

**Tests:** `PagoTest` — 5 casos cubriendo los 5 estados del enum `EstadoPago`.
