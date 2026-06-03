# Runbook Operativo — Plataforma de Gestión de Eventos Académicos

**PUJ · Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## Verificación de salud del sistema

### Verificar servicios del backend (EC2)

```bash
# Verificar que los 4 contenedores están activos
ssh ec2-user@<IP_EC2>
sudo docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Estado esperado:
```
eventos-auth-service-1      Up X minutes
eventos-event-service-1          Up X minutes
eventos-inscription-service-1    Up X minutes
eventos-payment-service-1        Up X minutes
```

### Verificar healthchecks a través del ALB

```bash
ALB="eventos-javeriana-alb-1966078085.us-east-1.elb.amazonaws.com"

for endpoint in "auth/login" "eventos" "inscripciones" "pagos"; do
  echo -n "/$endpoint: "
  curl -s -o /dev/null -w "%{http_code}" "http://$ALB/api/v1/$endpoint" || echo "ERROR"
done
```

### Verificar target groups en ALB (AWS CLI)

```bash
for TG in auth-tg event-tg insc-tg pay-tg; do
  ARN=$(aws elbv2 describe-target-groups --names "eventos-javeriana-$TG" \
    --query 'TargetGroups[0].TargetGroupArn' --output text)
  echo "$TG: $(aws elbv2 describe-target-health --target-group-arn $ARN \
    --query 'TargetHealthDescriptions[0].TargetHealth.State' --output text)"
done
```

---

## Reiniciar un servicio

```bash
# Conectar a EC2 vía SSM (sin SSH expuesto)
aws ssm start-session --target i-07e0425d504f41872

# En la instancia:
cd /opt/eventos
sudo docker compose restart event-service        # solo event-service
sudo docker compose restart                      # todos los servicios
```

---

## Ver logs de un servicio

```bash
# Últimas 100 líneas con follow
sudo docker logs --tail 100 -f eventos-event-service-1

# Filtrar errores
sudo docker logs eventos-inscription-service-1 2>&1 | grep '"lvl":"ERROR"'
```

---

## Verificación del frontend

```bash
# Verificar que CloudFront responde
curl -I https://d1xvny1kolb55e.cloudfront.net

# Verificar que el login funciona
curl -s -X POST "http://$ALB/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"<EMAIL>","password":"<PASSWORD>"}' | python3 -m json.tool
```

---

## Escenarios comunes

| Escenario | Síntoma | Acción |
|---|---|---|
| Target group unhealthy | 502/503 en el ALB | Ver logs del servicio, reiniciar si necesario |
| Eventos no aparecen | Catálogo vacío | Verificar Redis y event-service |
| Login falla | 401 desde frontend | Verificar auth-service y JWKS |
| Pago sin confirmar | Inscripción queda PENDIENTE_PAGO | Ver logs de payment-service y RabbitMQ |
| Frontend desactualizado | Versión antigua en producción | Invalidar CloudFront + desplegar |

Ver procedimientos detallados en [`incidentes.md`](incidentes.md).
