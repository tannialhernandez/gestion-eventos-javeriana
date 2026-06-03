# Plataforma de Gestión de Eventos Académicos

**Pontificia Universidad Javeriana — Sede Bogotá**

Sistema institucional para la gestión integral del ciclo de vida de eventos académicos: creación, aprobación, inscripción y pago.

[![Estado producción](https://img.shields.io/badge/producción-activo-success)](https://d1xvny1kolb55e.cloudfront.net)
[![Cobertura](https://img.shields.io/badge/cobertura-88%25-brightgreen)]()
[![Accesibilidad](https://img.shields.io/badge/WCAG-2.1%20AA-blue)]()
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-informational)]()

🌐 **Plataforma en producción**: https://d1xvny1kolb55e.cloudfront.net

---

## Visión general

La plataforma centraliza la gestión de eventos académicos abiertos a la comunidad Javeriana, cubriendo el flujo completo desde la creación hasta la confirmación de inscripción y pago:

- Los **organizadores** (profesores, coordinadores) crean eventos y los envían a revisión.
- Los **administradores** aprueban o rechazan el contenido antes de publicación.
- Los **participantes** (estudiantes, docentes, externos) descubren eventos, se inscriben y completan el pago.

## Capacidades funcionales

### Para participantes
- Catálogo de eventos académicos filtrable por tipo, modalidad y disponibilidad.
- Inscripción con control automático de cupos.
- Pago en línea con confirmación inmediata.
- Consulta y cancelación de inscripciones propias.

### Para organizadores
- Creación de eventos con información completa (fechas, modalidad, cupos, tarifas).
- Flujo de aprobación: envío a revisión → aprobación por administrador.
- Seguimiento del estado de cada evento: borrador, en revisión, publicado, rechazado.
- Edición de eventos propios.

### Para administradores
- Revisión y aprobación o rechazo de eventos con justificación.
- Gestión completa del catálogo institucional.
- Visibilidad sobre inscripciones y pagos del sistema.
- Acceso a métricas operativas en tiempo real.

## Arquitectura

El sistema está construido sobre una arquitectura de microservicios desplegada en Amazon Web Services (región `us-east-1`):

```
Usuarios (navegador)
        │ HTTPS
        ▼
  CloudFront CDN ──► S3 (SPA React)
        │
        │ API REST / JWT RS256
        ▼
  Application Load Balancer
        │
   ┌────┼────┬────┐
   ▼    ▼    ▼    ▼
 Auth  Evento Inscr. Pago
  :8081 :8082 :8083 :8084
   └────┴────┴────┘
        │
   ┌────┼────┐
   ▼    ▼    ▼
  RDS  Redis Amazon MQ
(PostgreSQL) (Cache) (RabbitMQ)
```

Documentación técnica completa: [`docs/architecture/`](docs/architecture/)

## Stack tecnológico

| Capa | Tecnología | Justificación |
|---|---|---|
| Frontend | React 18 + TypeScript + Vite | Modernidad, rendimiento, mantenibilidad |
| Backend | Spring Boot 3 + Java 17 | Estándar de plataformas institucionales |
| Base de datos | PostgreSQL 15 (RDS) | Consistencia transaccional por servicio |
| Caché | Redis (ElastiCache) | Rendimiento del catálogo de eventos |
| Mensajería | RabbitMQ (Amazon MQ) | Comunicación asíncrona confiable |
| Infraestructura | Terraform (IaC) | Reproducibilidad y versionado |
| CI/CD | GitHub Actions | Despliegue continuo automatizado |
| Nube | Amazon Web Services | Disponibilidad y resiliencia |

## Requisitos no funcionales

| Atributo | Objetivo | Estado |
|---|---|---|
| Disponibilidad | 99.5% | ✅ Diseño multi-AZ |
| Tiempo de respuesta P95 | < 500 ms | ✅ 380 ms medido |
| Usuarios concurrentes | 100 | ✅ 500 VU validados |
| Cobertura de pruebas | > 80% | ✅ 88% |
| Accesibilidad | WCAG 2.1 AA | ✅ 0 violaciones Axe |
| Seguridad | OWASP Top 10 | ✅ Análisis estático limpio |

## Cumplimiento normativo

- ✅ **Ley 1581 de 2012** — Protección de datos personales (Colombia).
- ✅ **Decreto 1377 de 2013** — Tratamiento de información sensible.
- ✅ **Accesibilidad web** — WCAG 2.1 nivel AA.
- ✅ **Auditoría** — Trazabilidad completa de operaciones críticas.

Documentación de cumplimiento: [`docs/compliance/`](docs/compliance/)

## Roles de usuario

| Rol | Perfil típico | Capacidades |
|---|---|---|
| Administrador | Personal TI / Coordinación académica | Aprobación de eventos, gestión global |
| Organizador | Profesores, coordinadores de área | Crear y gestionar eventos propios |
| Participante | Estudiantes, personal, comunidad externa | Inscripción y pago |

## Desarrollo local

### Requisitos previos

- Java 17, Maven 3.9+
- Node.js 20, npm 10+
- Docker + Docker Compose

### Levantar el sistema

```bash
# Backend completo
docker compose -f docker-compose.e2e.yml up -d

# Frontend en modo desarrollo
cd frontend && npm install && npm run dev
# → http://localhost:3000
```

Guía completa: [`docs/architecture/setup-local.md`](docs/architecture/setup-local.md)

## Despliegue en producción

El sistema se despliega automáticamente al fusionar cambios en `main`:

- **Frontend**: GitHub Actions → `npm run build` → S3 → invalidación CloudFront.
- **Backend**: imagen Docker en GHCR → SSH a EC2 → `docker compose up -d`.

Procedimientos detallados: [`docs/operations/despliegue.md`](docs/operations/despliegue.md)

## Estructura del repositorio

```
├── auth-service/               # Servicio de autenticación JWT (RS256)
├── event-service/              # Gestión del ciclo de vida de eventos
├── inscription-service/        # Inscripciones y control de cupos
├── payment-service/            # Procesamiento de pagos y webhooks
├── frontend/                   # Aplicación web (React + TypeScript)
├── infra/                      # Infraestructura como código (Terraform)
├── docs/
│   ├── architecture/           # SAD, C4, ADRs, modelo de datos
│   ├── operations/             # Runbook, despliegue, monitoreo
│   └── compliance/             # Documentación normativa Ley 1581
└── scripts/                    # Scripts de operación y verificación
```

## Evolución planificada

| Capacidad | Estado |
|---|---|
| Integración Azure AD institucional | Planificado |
| Pasarela de pago en producción | Planificado |
| Generación de certificados PDF | Planificado |
| Notificaciones por correo (Amazon SES) | Planificado |
| Panel administrativo avanzado | Planificado |
| Auto Scaling Group (alta disponibilidad) | Planificado |

Backlog completo: [`docs/roadmap.md`](docs/roadmap.md)

## Soporte

| Canal | Contacto |
|---|---|
| Soporte técnico | soporte.eventos@javeriana.edu.co |
| Coordinación funcional | eventos@javeriana.edu.co |
| Incidentes de seguridad | seguridad.ti@javeriana.edu.co |

---

© 2026 Pontificia Universidad Javeriana — Todos los derechos reservados.
