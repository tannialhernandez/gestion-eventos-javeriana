# Roadmap de Evolución del Producto

**PUJ · Versión:** 1.0 · **Actualizado:** 2026-06-03

---

## Capacidades planificadas (Fase 2)

### Autenticación institucional
**Esfuerzo:** 3-5 días  
Integrar Azure Active Directory de la Pontificia Universidad Javeriana mediante protocolo OIDC / OAuth 2.0. Permite que los usuarios inicien sesión con sus credenciales institucionales existentes sin necesidad de una cuenta separada.

### Pasarela de pago en producción
**Esfuerzo:** 3-5 días  
Activar la integración con Mercado Pago para procesamiento real de pagos. El adaptador técnico ya está implementado; requiere credenciales de producción y validación con el equipo financiero.

### Generación de certificados PDF
**Esfuerzo:** 5-7 días  
Emitir certificados de asistencia en PDF firmados digitalmente, almacenados en Amazon S3 y accesibles por correo electrónico.

### Notificaciones por correo electrónico
**Esfuerzo:** 3-4 días  
Enviar confirmaciones automáticas de inscripción, recordatorios y certificados usando Amazon SES, conectado al bus de eventos existente (RabbitMQ).

### Panel administrativo avanzado
**Esfuerzo:** 2-3 días  
Vista de administración con métricas de eventos, inscripciones y pagos en tiempo real. Filtros por período, estado y organizador.

### Alta disponibilidad (Auto Scaling)
**Esfuerzo:** 1-2 días  
Migrar de instancia EC2 única a Auto Scaling Group para tolerancia a fallos automática y escalado horizontal.

### Refresh tokens
**Esfuerzo:** 1-2 días  
Implementar tokens de renovación para sesiones de larga duración sin requerir re-autenticación cada hora.

---

## Deuda técnica conocida

El inventario completo de deuda técnica con clasificación y plan de cierre está en [`TECH_DEBT.md`](../TECH_DEBT.md).

Los elementos de mayor impacto para producción:
1. Integración Azure AD (D-003)
2. Integración Mercado Pago producción (D-001)
3. Certificados de participación PDF (D-005)
4. Notificaciones por correo (D-004)
