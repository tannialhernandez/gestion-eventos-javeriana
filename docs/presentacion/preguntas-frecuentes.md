# Preguntas Frecuentes — Material de Apoyo para Presentación

**Plataforma de Gestión de Eventos Académicos · PUJ · 2026**

---

## Sobre el producto

**¿Quién puede usar la plataforma?**
Tres perfiles: administradores institucionales, organizadores académicos (profesores, coordinadores de área) y participantes (estudiantes, personal y comunidad externa). Cada perfil ve y puede hacer cosas distintas.

**¿Reemplaza algún sistema actual?**
Centraliza la gestión de eventos que hoy se realiza en múltiples canales: correo electrónico, formularios de Google, planillas de Excel y llamadas telefónicas. No reemplaza sistemas de gestión académica (SIS, Banner, etc.).

**¿Se integra con otros sistemas de la Javeriana?**
Está diseñada para integrarse con Azure AD institucional (login con cuenta corporativa) y con el portal estudiantil. La integración con Azure AD está planificada para Fase 2.

**¿Funciona desde el celular?**
Sí. La interfaz es completamente responsiva y funciona en cualquier navegador moderno en computador, tablet o celular. No requiere instalar ninguna aplicación.

**¿Qué pasa si un organizador comete un error en el evento publicado?**
Puede editarlo. Los cambios en eventos publicados son visibles inmediatamente. Para cambios mayores (fechas, cupos), el administrador puede rechazar y solicitar corrección antes de la publicación.

---

## Sobre la arquitectura técnica

**¿Por qué se eligió una arquitectura de microservicios?**
Cada capacidad (autenticación, eventos, inscripciones, pagos) evoluciona y escala de forma independiente. Si el servicio de pagos tiene mantenimiento, el catálogo de eventos sigue funcionando normalmente.

**¿Por qué AWS?**
Disponibilidad empresarial comprobada (99.99%), escalabilidad automática, y alineación con la estrategia cloud institucional. El costo es predecible (~USD 150/mes) y optimizable.

**¿Es escalable si hay muchos usuarios simultáneos?**
Validado para 500 usuarios concurrentes bajo carga sostenida. Escalable a más con Auto Scaling Group, planificado en Fase 2. La arquitectura permite escalar cada servicio de forma independiente según la demanda.

**¿Qué pasa si falla uno de los servicios?**
El sistema tiene Circuit Breaker que aísla los fallos. Si el servicio de pagos falla, el catálogo sigue disponible. El usuario ve mensajes claros sobre la disponibilidad temporal reducida, sin errores técnicos.

**¿Por qué no usar una solución SaaS de terceros?**
Las soluciones genéricas no contemplan el flujo de aprobación institucional, la integración con Azure AD Javeriana, ni el cumplimiento específico de la Ley 1581. Tener el código fuente da independencia tecnológica a largo plazo.

---

## Sobre la operación

**¿Quién opera el sistema en producción?**
El equipo TI institucional, con procedimientos completamente documentados en el repositorio. El runbook cubre todos los escenarios de incidente identificados.

**¿Cuánto tiempo tarda en resolverse un incidente?**
Objetivo documentado: RTO (Recovery Time Objective) menor a 2 horas para escenarios mayores. Los incidentes menores (un servicio caído) se resuelven típicamente en menos de 15 minutos siguiendo el runbook.

**¿Cómo se hacen los respaldos de la base de datos?**
RDS realiza snapshots automáticos diarios con retención de 7 días. El RPO (Recovery Point Objective) es menor a 24 horas. Adicionalmente, la infraestructura completa puede recrearse desde cero con Terraform.

**¿Se puede ver quién aprobó qué evento?**
Sí. Todas las operaciones críticas tienen auditoría completa: quién hizo qué y cuándo. Inscripciones, aprobaciones, pagos y cancelaciones están registradas con timestamp y usuario.

**¿Requiere mantenimiento programado?**
Los despliegues se realizan sin interrupción del servicio (rolling updates). No hay ventanas de mantenimiento programadas. El tiempo total de un despliegue es aproximadamente 12 minutos.

---

## Sobre seguridad y privacidad

**¿Cómo se protegen los datos personales de los estudiantes?**
Cumplimiento completo de la Ley 1581: comunicación cifrada (HTTPS), autenticación obligatoria con JWT RS256, control de acceso por rol, auditoría completa, política de retención documentada y procedimiento de supresión disponible.

**¿Qué pasa si un estudiante solicita eliminar sus datos?**
Existe un procedimiento de supresión documentado ejecutable por el equipo TI. El tiempo máximo de respuesta es 15 días hábiles conforme a lo establecido en la Ley 1581.

**¿Dónde están almacenados los datos?**
En Amazon RDS (PostgreSQL) en la región us-east-1 de AWS, en subredes privadas sin acceso directo desde internet. AWS cumple con marcos de seguridad equivalentes a los exigidos por la legislación colombiana.

**¿Los pagos son seguros?**
La versión actual usa un simulador interno para validar el flujo de pago. La integración con Mercado Pago en producción (Fase 2) usa webhooks con autenticación HMAC-SHA256. Los datos de tarjetas nunca se almacenan en el sistema — los procesa la pasarela externa.

**¿Tiene accesibilidad para personas con discapacidad?**
Sí. El sistema cumple con WCAG 2.1 nivel AA, verificado con la herramienta Axe en todas las vistas. Fue probado con tecnología de asistencia (lectores de pantalla). Resultado: 0 violaciones de accesibilidad.

---

## Sobre los roles y gestión

**¿Cómo se asigna el rol de organizador a un profesor?**
En la versión actual, los roles se configuran en el sistema de autenticación. Cuando se integre Azure AD institucional (Fase 2), los roles podrán mapearse desde los grupos de Active Directory.

**¿Un organizador puede ver las inscripciones de su evento?**
Sí. Puede ver la lista de inscritos, el estado de cada inscripción y gestionar el registro de asistencia para generar certificados en el futuro.

**¿Un administrador puede editar o cancelar el evento de otro organizador?**
Sí. El administrador tiene control total sobre el catálogo, incluyendo eventos de otros organizadores. Todas las acciones quedan registradas en la auditoría.

**¿Qué pasa si se llena el cupo de un evento?**
El sistema bloquea automáticamente nuevas inscripciones cuando se agota el cupo. El control es transaccional (evita condiciones de carrera con múltiples inscripciones simultáneas). Los participantes ven el mensaje de cupo agotado.

---

## Sobre la evolución

**¿Qué viene después de esta versión?**
Fase 2 planificada: integración Azure AD, Mercado Pago real, certificados PDF automáticos, notificaciones por correo (Amazon SES), panel administrativo avanzado con reportes y métricas.

**¿Cuánto esfuerzo requiere cada mejora?**
Cada item del roadmap tiene esfuerzo estimado: 17-25 días-persona en total para toda la Fase 2. El orden de prioridad es ajustable según las necesidades institucionales.

**¿Quién decide qué se construye primero?**
La coordinación institucional en conjunto con el equipo TI, basados en datos de uso real y retroalimentación de los usuarios. El backlog técnico está documentado y priorizado.

**¿Pueden otras unidades académicas tener su propio catálogo?**
La arquitectura actual tiene un catálogo institucional único. La segmentación por facultad o unidad académica está en el backlog para una versión futura.

**¿La plataforma puede integrarse con el sistema de certificación académica?**
Está en el roadmap como generación de certificados PDF automáticos (Fase 2). La integración con sistemas de certificación externos (Banner, etc.) requeriría un esfuerzo adicional a evaluar.
