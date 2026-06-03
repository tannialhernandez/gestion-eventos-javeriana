# Gestión de Credenciales y Secretos

**PUJ · Versión:** 2.0 · **Actualizado:** 2026-06-03  
**Confidencialidad:** Uso interno TI — no publicar

---

## Principios de gestión de secretos

- **Ningún secreto** se almacena en el repositorio de código fuente.
- **Producción:** secretos inyectados como variables de entorno en el contenedor Docker.
- **Acceso mínimo:** cada servicio solo tiene acceso a sus propios secretos.

---

## Recursos de producción

| Recurso | Descripción | Ubicación del secreto |
|---|---|---|
| Base de datos PostgreSQL | Host, usuario, contraseña | Variables de entorno en docker-compose.prod.yml |
| Redis | Host (sin contraseña en VPC privada) | Variable de entorno |
| RabbitMQ (Amazon MQ) | Host, usuario, contraseña | Variables de entorno |
| JWT — clave privada RSA | Firma de tokens JWT | Archivo de configuración en auth-service |
| JWT — clave pública RSA | Validación en microservicios | Variable de entorno `JWT_PUBLIC_KEY` |
| AWS (acceso) | Access Key / Secret Key | Perfil IAM de la instancia EC2 |

---

## Acceso de emergencia a la base de datos

Solo el equipo de TI con acceso a la instancia EC2 puede conectarse a la base de datos, ya que RDS está en una subred privada sin acceso directo desde internet.

```bash
# Vía SSM (sin abrir puerto 22)
aws ssm start-session --target i-07e0425d504f41872

# Desde la instancia:
PGPASSWORD=<SECRETO> psql \
  -h eventos-javeriana-db.co9qgmemsrf7.us-east-1.rds.amazonaws.com \
  -U eventos_admin -d eventos
```

La contraseña se encuentra en `docker-compose.prod.yml` en la instancia EC2 en `/opt/eventos/`.

---

## Rotación de credenciales

| Credencial | Frecuencia de rotación | Procedimiento |
|---|---|---|
| Contraseña de RDS | Anual | Actualizar en `docker-compose.prod.yml` + reiniciar servicios |
| Contraseña RabbitMQ | Anual | Actualizar en Amazon MQ Console + docker-compose + reiniciar |
| Par RSA (JWT) | Anual | Generar nuevo par, actualizar auth-service + `JWT_PUBLIC_KEY` en todos los servicios, reiniciar |

---

## Plan de transición a AWS Secrets Manager

Para Fase 2, las credenciales deben migrarse a AWS Secrets Manager con rotación automática. Los microservicios Spring Boot utilizan `spring-cloud-aws-secrets-manager` para recuperar secretos en tiempo de arranque.
