# Respuesta a Incidentes — Playbook

**PUJ · Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## Clasificación de incidentes

| Severidad | Descripción | Tiempo de respuesta |
|---|---|---|
| P1 — Crítico | Sistema completamente inaccesible | < 15 minutos |
| P2 — Alto | Funcionalidad principal degradada | < 1 hora |
| P3 — Medio | Función secundaria afectada | < 4 horas |
| P4 — Bajo | Impacto menor, workaround disponible | < 24 horas |

---

## Escenario 1: Frontend inaccesible (P1)

**Síntoma:** `https://d1xvny1kolb55e.cloudfront.net` devuelve error.

```bash
# 1. Verificar que CloudFront responde
curl -I https://d1xvny1kolb55e.cloudfront.net

# 2. Verificar que S3 tiene los archivos
aws s3 ls s3://eventos-javeriana-spa-44aa8767/ | grep index.html

# 3. Si S3 vacío: re-desplegar frontend
cd frontend && npm run build
aws s3 sync dist/ s3://eventos-javeriana-spa-44aa8767/ --delete
aws cloudfront create-invalidation --distribution-id E2WXZQBIXPHFUB --paths "/*"
```

---

## Escenario 2: API inaccesible — 502/503 desde el ALB (P1)

**Síntoma:** `GET /api/v1/eventos` devuelve 502 o 503.

```bash
# 1. Verificar estado de target groups
aws elbv2 describe-target-health \
  --target-group-arn $(aws elbv2 describe-target-groups \
    --names "eventos-javeriana-event-tg" \
    --query 'TargetGroups[0].TargetGroupArn' --output text)

# 2. Verificar que el contenedor está activo
aws ssm start-session --target i-07e0425d504f41872
sudo docker ps

# 3. Si el contenedor está caído, reiniciar
cd /opt/eventos && sudo docker compose up -d event-service

# 4. Ver logs del fallo
sudo docker logs --tail 50 eventos-event-service-1 2>&1 | grep ERROR
```

---

## Escenario 3: Login falla para todos los usuarios (P1)

**Síntoma:** 401 en `POST /api/v1/auth/login` con credenciales correctas.

```bash
# Verificar auth-service
curl http://localhost:8081/actuator/health

# Ver logs
sudo docker logs --tail 50 eventos-auth-service-1

# Reiniciar si necesario
sudo docker compose restart auth-service
```

---

## Escenario 4: Inscripciones no se confirman (P2)

**Síntoma:** Pagos procesados pero inscripción queda en PENDIENTE_PAGO.

```bash
# 1. Verificar RabbitMQ procesa mensajes
sudo docker logs --tail 50 eventos-payment-service-1 2>&1 | grep "pago.confirmado"

# 2. Verificar inscription-service recibe eventos
sudo docker logs --tail 50 eventos-inscription-service-1 2>&1 | grep "inscripcion"

# 3. Verificar tablas outbox pendientes en BD
# (conexión a RDS requerida — ver credenciales en AWS Secrets Manager)
```

---

## Escenario 5: Base de datos inaccesible (P1)

```bash
# Test de conectividad
pg_isready -h eventos-javeriana-db.co9qgmemsrf7.us-east-1.rds.amazonaws.com \
           -U eventos_admin -d eventos

# Si falla: verificar Security Groups en AWS Console
# El grupo de seguridad del EC2 debe tener acceso al puerto 5432 del RDS
```

---

## Contactos de escalamiento

| Nivel | Contacto |
|---|---|
| Operativo (L1) | soporte.eventos@javeriana.edu.co |
| Técnico (L2) | equipo.ti@javeriana.edu.co |
| Infraestructura (L3) | infraestructura.aws@javeriana.edu.co |
