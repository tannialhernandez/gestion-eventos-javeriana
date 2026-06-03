# Plataforma de Gestión de Eventos Académicos
## Resumen Ejecutivo — 1 página

**Pontificia Universidad Javeriana — Sede Bogotá · 2026**

---

### Qué es

Sistema institucional para gestionar el ciclo completo de eventos académicos: creación por organizadores, aprobación por administradores, inscripción de participantes y confirmación de pago. Todo en un único punto de acceso para la comunidad Javeriana.

**URL de producción:** https://d1xvny1kolb55e.cloudfront.net

---

### Estado actual del sistema

| Indicador | Estado |
|---|---|
| Sistema en producción | ✅ Activo y accesible |
| Roles funcionales | ✅ Administrador, Organizador, Participante |
| Cobertura de pruebas automatizadas | ✅ 88% |
| Tiempo de respuesta P95 | ✅ 380 ms (objetivo < 500 ms) |
| Capacidad concurrente validada | ✅ 500 usuarios simultáneos |
| Accesibilidad | ✅ WCAG 2.1 AA (0 violaciones) |
| Cumplimiento Ley 1581 de 2012 | ✅ Implementado y documentado |
| Infraestructura como código (Terraform) | ✅ Reproducible en < 30 min |
| CI/CD automatizado | ✅ Despliegue en ~12 minutos |

---

### Eventos disponibles hoy en producción

1. Congreso Internacional de Arquitectura de Software 2026
2. Seminario de Inteligencia Artificial Aplicada
3. Conferencia: Transformación Digital en Educación Superior
4. Workshop de Ciberseguridad para Desarrolladores
5. Foro de Emprendimiento Tecnológico Universitario

---

### Beneficios para la institución

- **Centralización:** un único sistema para todos los eventos académicos.
- **Trazabilidad:** auditoría completa de inscripciones, pagos y aprobaciones.
- **Eficiencia:** creación de evento en 5 minutos vs. 2-4 horas manual.
- **Datos consolidados:** métricas en tiempo real para toma de decisiones.
- **Cumplimiento normativo:** Ley 1581, WCAG 2.1 AA, OWASP Top 10.

---

### Costo de operación

**Infraestructura AWS:** ~USD 150 / mes
(Cómputo + base de datos + mensajería + caché + CDN)

Mantenimiento integrado en el equipo TI institucional existente.
Optimizable hasta 40% con instancias reservadas.

---

### Evolución planificada (Fase 2) — 17–25 días-persona

| Mejora | Esfuerzo |
|---|---|
| Integración Azure AD institucional | 3–5 días |
| Pasarela de pago en producción | 3–5 días |
| Certificados de participación PDF | 5–7 días |
| Notificaciones por correo (Amazon SES) | 3–4 días |
| Panel administrativo con reportes | 2–3 días |

---

### Próximos pasos sugeridos

1. Validación funcional con coordinación académica (semanas 1–2).
2. Designación de organizadores iniciales por facultad.
3. Comunicación oficial a la comunidad universitaria.
4. Operación activa con soporte acompañado (mes 2).
5. Priorización e inicio de Fase 2 basado en datos de uso.

---

**Contacto:**
soporte.eventos@javeriana.edu.co · eventos@javeriana.edu.co

*Pontificia Universidad Javeriana · Cra. 7 No. 40-62 · Bogotá D.C. · © 2026*
