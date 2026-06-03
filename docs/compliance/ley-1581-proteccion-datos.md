# Cumplimiento — Ley 1581 de 2012 (Protección de Datos Personales)

**PUJ · Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## Resumen

La Plataforma de Gestión de Eventos Académicos trata datos personales de usuarios de la comunidad Javeriana (estudiantes, docentes, personal administrativo) y externos. El tratamiento cumple con la Ley 1581 de 2012 y el Decreto Reglamentario 1377 de 2013.

---

## Datos personales tratados

| Categoría | Datos | Finalidad |
|---|---|---|
| Identificación | Nombre completo, correo electrónico | Autenticación y comunicación |
| Operacionales | ID de usuario, historial de inscripciones | Gestión de eventos y trazabilidad |
| Financieros | Referencia de pago, monto pagado | Procesamiento de inscripciones con costo |

**Datos NO tratados:** documentos de identidad, datos de tarjetas de crédito (procesados por la pasarela externa), datos de salud, datos biométricos.

---

## Base legal del tratamiento

- **Relación contractual:** el tratamiento es necesario para ejecutar la inscripción al evento solicitado por el titular.
- **Interés legítimo institucional:** gestión académica y administrativa de la Pontificia Universidad Javeriana.
- **Consentimiento:** al registrarse, el usuario acepta la política de privacidad.

---

## Derechos del titular (Art. 8, Ley 1581)

Los titulares pueden ejercer sus derechos en cualquier momento:

| Derecho | Descripción | Canal |
|---|---|---|
| Conocer | Solicitar qué datos se tratan | soporte.eventos@javeriana.edu.co |
| Actualizar | Corregir datos inexactos | soporte.eventos@javeriana.edu.co |
| Suprimir | Solicitar eliminación de datos | soporte.eventos@javeriana.edu.co |
| Revocar consentimiento | Retirar autorización de tratamiento | soporte.eventos@javeriana.edu.co |
| Acceder | Obtener copia de sus datos | soporte.eventos@javeriana.edu.co |

**Tiempo de respuesta:** máximo 15 días hábiles conforme a la ley.

---

## Medidas de seguridad implementadas

- Contraseñas nunca almacenadas en texto plano.
- Comunicaciones cifradas con TLS/HTTPS.
- Acceso a datos por roles diferenciados (RBAC).
- Logs de auditoría de operaciones críticas.
- Datos de producción accesibles solo desde red VPC privada.

---

## Retención de datos

| Tipo de dato | Período de retención | Justificación |
|---|---|---|
| Inscripciones confirmadas | 5 años | Trazabilidad financiera y académica |
| Pagos procesados | 5 años | Cumplimiento fiscal |
| Inscripciones canceladas/expiradas | 1 año | Referencia operativa |
| Logs del sistema | 90 días | Auditoría de seguridad |

---

## Responsable del tratamiento

**Pontificia Universidad Javeriana — Sede Bogotá**  
Carrera 7 No. 40-62 · Bogotá D.C., Colombia  
Contacto de datos personales: privacidad@javeriana.edu.co
