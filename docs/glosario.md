# Glosario Institucional — Plataforma de Gestión de Eventos Académicos

**PUJ · Versión:** 1.0 · **Actualizado:** 2026-06-03

---

## Términos del dominio

### Evento académico
Actividad organizada por la Pontificia Universidad Javeriana de carácter formativo, científico o cultural (congresos, talleres, simposios, seminarios). Tiene fechas definidas, capacidad limitada y puede requerir inscripción y pago previos.

### Ciclo de vida del evento
Secuencia de estados por los que pasa un evento: **Borrador → En revisión → Publicado** (o Rechazado). Solo los eventos publicados son visibles para participantes.

### Borrador
Estado inicial de un evento recién creado. Solo visible para el organizador y administradores. No disponible para inscripción.

### En revisión (Pendiente de publicación)
Estado en que se encuentra un evento enviado por el organizador para aprobación por parte de un administrador. No disponible para inscripción.

### Publicado
Estado en que el evento está aprobado y visible en el catálogo para todos los usuarios. Los participantes pueden inscribirse.

### Rechazado
Estado asignado por un administrador cuando un evento no cumple los criterios de publicación. El organizador puede corregirlo y re-enviarlo.

### Inscripción
Registro de un participante en un evento académico. Implica la reserva de un cupo. Puede requerir pago para confirmarse.

### Tarifa
Precio de inscripción a un evento. Un evento puede tener múltiples tarifas (e.g., tarifa para estudiantes Javeriana, tarifa general).

### Cupo
Capacidad disponible de un evento para nuevas inscripciones. Se decrementa automáticamente con cada inscripción confirmada.

### Participante
Usuario que puede inscribirse a eventos académicos. Incluye estudiantes, docentes y personas externas a la Universidad.

### Organizador
Docente o coordinador académico que crea y gestiona eventos de su dependencia. Puede crear eventos y enviarlos a revisión.

### Administrador
Miembro del personal de TI o coordinación que aprueba o rechaza eventos antes de su publicación, y gestiona el catálogo institucional.

### Aprobación
Acción del administrador que cambia un evento de estado "En revisión" a "Publicado", haciéndolo visible en el catálogo.

### Flujo de pago
Proceso por el cual un participante completa el pago de su inscripción a través de la pasarela integrada. El cupo queda reservado hasta que expire el tiempo de pago (15 minutos).

### JWT (Token de acceso)
Credencial digital que el sistema genera al iniciar sesión. Contiene el identificador y rol del usuario, y tiene validez de 1 hora. Permite acceder a las funciones del sistema sin enviar contraseñas en cada solicitud.

### Outbox Pattern
Técnica de ingeniería que garantiza que los eventos importantes (pago confirmado, inscripción registrada) sean procesados de forma confiable incluso si un servicio falla temporalmente.

---

## Acrónimos

| Acrónimo | Significado |
|---|---|
| ALB | Application Load Balancer (balanceador de carga AWS) |
| CDN | Content Delivery Network (red de distribución de contenidos) |
| RBAC | Role-Based Access Control (control de acceso por roles) |
| JWT | JSON Web Token (token de acceso seguro) |
| API | Application Programming Interface (interfaz de programación) |
| SPA | Single Page Application (aplicación web de página única) |
| TLS | Transport Layer Security (cifrado de comunicaciones) |
| RDS | Relational Database Service (base de datos administrada por AWS) |
| IaC | Infrastructure as Code (infraestructura como código) |
