# Configuración del Entorno Local

**PUJ · Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## Requisitos previos

| Herramienta | Versión mínima | Verificación |
|---|---|---|
| Java | 17 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 20 | `node -version` |
| Docker | 24+ | `docker -version` |
| Docker Compose | v2 | `docker compose version` |

## Levantar el sistema completo

### Opción A: Stack completo con Docker Compose

```bash
# Desde la raíz del repositorio
docker compose -f docker-compose.e2e.yml up -d

# Verificar que todos los servicios estén activos
docker compose -f docker-compose.e2e.yml ps
```

Servicios disponibles:
- Auth: http://localhost:8081/actuator/health
- Eventos: http://localhost:8082/actuator/health
- Inscripciones: http://localhost:8083/actuator/health
- Pagos: http://localhost:8084/actuator/health

### Frontend en modo desarrollo

```bash
cd frontend
npm install
npm run dev
# → http://localhost:3000
```

### Ejecutar pruebas

```bash
# Pruebas unitarias frontend
cd frontend && npm test

# Pruebas E2E (Playwright)
cd frontend && npm run test:e2e

# Pruebas backend (por servicio)
mvn test -pl event-service
```

## Variables de entorno para desarrollo

El sistema usa `docker-compose.e2e.yml` con valores de desarrollo definidos. Para producción, los secretos se gestionan por AWS Secrets Manager — ver [`../operations/credenciales.md`](../operations/credenciales.md).

## Usuarios disponibles en desarrollo

Los usuarios de desarrollo están definidos en `auth-service/src/main/resources/application.yml`:

| Email | Contraseña | Rol |
|---|---|---|
| laura.participante@javeriana.edu.co | demo123 | PARTICIPANTE |
| diego.participante@javeriana.edu.co | demo123 | PARTICIPANTE |
| carlos.organizador@javeriana.edu.co | demo123 | ORGANIZADOR |
| ana.admin@javeriana.edu.co | demo123 | ADMIN |
| sofia.soporte@javeriana.edu.co | demo123 | PARTICIPANTE |
