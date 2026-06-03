# Credenciales Demo — Sistema Productivo AWS

## URLs del Sistema

| Componente | URL |
|---|---|
| SPA (Frontend) | https://d1xvny1kolb55e.cloudfront.net |
| API (Backend ALB) | http://eventos-javeriana-alb-1966078085.us-east-1.elb.amazonaws.com |
| JWKS | http://eventos-javeriana-alb-1966078085.us-east-1.elb.amazonaws.com/api/v1/auth/.well-known/jwks.json |

## Usuarios Demo

| Email | Password | Rol | ID |
|---|---|---|---|
| laura.participante@javeriana.edu.co | demo123 | PARTICIPANTE | 11111111-1111-1111-1111-111111111111 |
| diego.participante@javeriana.edu.co | demo123 | PARTICIPANTE | 22222222-2222-2222-2222-222222222222 |
| carlos.organizador@javeriana.edu.co | demo123 | ORGANIZADOR | 33333333-3333-3333-3333-333333333333 |
| ana.admin@javeriana.edu.co | demo123 | ADMIN | 44444444-4444-4444-4444-444444444444 |
| sofia.soporte@javeriana.edu.co | demo123 | PARTICIPANTE | 55555555-5555-5555-5555-555555555555 |

## Evento de Prueba

| Campo | Valor |
|---|---|
| ID | 00000000-0000-0000-0000-000000000001 |
| Título | Congreso Javeriano de Arquitectura de Software 2026 |
| Modalidad | HÍBRIDO |
| Fechas | 3-5 julio 2026 |
| Cupo | 50 |
| Estado | PUBLICADO |

## Tarifa

| Campo | Valor |
|---|---|
| ID | 00000000-0000-0000-0001-000000000001 |
| Monto | 150.000 COP |
| Descripción | Tarifa General AWS |

## Flujo Completo de Prueba

```bash
ALB="eventos-javeriana-alb-1966078085.us-east-1.elb.amazonaws.com"

# 1. Login
TOKEN=$(curl -s -X POST http://$ALB/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"laura.participante@javeriana.edu.co","password":"demo123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

# 2. Ver catálogo
curl -s -H "Authorization: Bearer $TOKEN" http://$ALB/api/v1/eventos

# 3. Ver tarifas
curl -s http://$ALB/api/v1/tarifas?eventoId=00000000-0000-0000-0000-000000000001

# 4. Inscribirse
IKEY=$(python3 -c "import uuid; print(uuid.uuid4())")
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"eventoId\":\"00000000-0000-0000-0000-000000000001\",\"tarifaId\":\"00000000-0000-0000-0001-000000000001\",\"idempotencyKey\":\"$IKEY\"}" \
  http://$ALB/api/v1/inscripciones

# 5. Pagar (usar inscripcionId del paso anterior)
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"inscripcionId":"<ID>","monto":150000,"moneda":"COP"}' \
  http://$ALB/api/v1/pagos/preferencias
```

## Smoke E2E Automatizado

```bash
bash scripts/smoke-e2e-aws.sh
```

Resultado esperado: 5 tests PASS y `✅ SMOKE E2E AWS COMPLETO`.

## Infraestructura AWS

| Recurso | ID/Nombre |
|---|---|
| EC2 | i-07e0425d504f41872 (10.0.10.39) |
| RDS PostgreSQL | eventos-javeriana-db.co9qgmemsrf7.us-east-1.rds.amazonaws.com |
| ALB | eventos-javeriana-alb |
| CloudFront | d1xvny1kolb55e.cloudfront.net |
| ElastiCache Redis | eventos-javeriana-redis |

## Estado de Target Groups ALB (verificado 2026-06-03)

| Target Group | Puerto | Estado |
|---|---|---|
| eventos-javeriana-auth-tg | 8081 | healthy |
| eventos-javeriana-event-tg | 8082 | healthy |
| eventos-javeriana-insc-tg | 8083 | healthy |
| eventos-javeriana-pay-tg | 8084 | healthy |

## Notas Técnicas

- **Flyway**: inscription-service usa schema `inscription`, payment-service usa schema `payment`,
  event-service usa schema `public`. Tablas de historia separadas por servicio.
- **Pasarela de pago**: modo simulador (PAYMENT_GATEWAY_PROVIDER=simulador).
- **JWT**: RSA-256, válidos por 1 hora, firmados por auth-service-stub.
- **uk_usuario_evento**: un usuario no puede inscribirse dos veces al mismo evento.
  Si el smoke test falla con 401 al inscribirse, eliminar inscripciones de prueba:
  `DELETE FROM inscription.inscripcion WHERE evento_id='00000000-0000-0000-0000-000000000001';`
