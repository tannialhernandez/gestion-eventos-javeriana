# Monitoreo y Observabilidad

**PUJ · Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## Métricas de salud en tiempo real

### Healthcheck de servicios

Todos los microservicios exponen `/actuator/health`:

```bash
ALB="eventos-javeriana-alb-1966078085.us-east-1.elb.amazonaws.com"

echo "Auth:        $(curl -s http://$ALB/api/v1/auth/actuator/health)"
echo "Eventos:     $(curl -s http://localhost:8082/actuator/health)"
echo "Inscripcion: $(curl -s http://localhost:8083/actuator/health)"
echo "Pago:        $(curl -s http://localhost:8084/actuator/health)"
```

Respuesta esperada: `{"status":"UP"}`

### Target groups ALB

```bash
aws elbv2 describe-target-health \
  --target-group-arn <ARN> \
  --query 'TargetHealthDescriptions[*].{Target:Target.Id,Estado:TargetHealth.State}'
```

---

## Logs estructurados

Los microservicios emiten logs en formato JSON con los campos:

| Campo | Descripción |
|---|---|
| `ts` | Timestamp ISO-8601 |
| `lvl` | Nivel: INFO, WARN, ERROR |
| `app` | Nombre del servicio |
| `corrId` | ID de correlación de la solicitud |
| `userId` | ID del usuario autenticado (si aplica) |
| `msg` | Mensaje del evento |

### Buscar errores recientes

```bash
# En la instancia EC2:
sudo docker logs --since 1h eventos-event-service-1 2>&1 | \
  python3 -c "
import sys, json
for line in sys.stdin:
  try:
    e = json.loads(line)
    if e.get('lvl') == 'ERROR':
      print(e['ts'], e['app'], e['msg'])
  except: pass
"
```

### Buscar por correlación ID

```bash
sudo docker logs eventos-inscription-service-1 2>&1 | \
  grep '"corrId":"<CORR_ID>"'
```

---

## CloudWatch (AWS)

Los logs de los contenedores se pueden enviar a CloudWatch Logs con el driver `awslogs` en `docker-compose.prod.yml`. Actualmente los logs están disponibles localmente en la instancia EC2.

**Alarmas recomendadas para configurar:**
- Target group con estado `unhealthy` por más de 2 minutos.
- Uso de CPU > 80% en EC2.
- Almacenamiento RDS < 20% libre.
- Errores 5xx en ALB > 1% del tráfico.

---

## Métricas de Prometheus

Todos los servicios exponen `/actuator/prometheus` para scraping:

```bash
curl -s http://localhost:8082/actuator/prometheus | grep "^http_"
```

---

## Verificación del flujo completo (E2E)

```bash
# Ejecutar verificación completa del sistema
bash scripts/verificacion-e2e-aws.sh
```

Resultado esperado: 5 verificaciones exitosas (JWKS, login, catálogo, tarifas, inscripción).
