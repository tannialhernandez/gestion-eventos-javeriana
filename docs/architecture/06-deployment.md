# Despliegue en Producción — Topología AWS

**PUJ · Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## Recursos de producción

| Recurso | Identificador / URL | Descripción |
|---|---|---|
| SPA (Frontend) | https://d1xvny1kolb55e.cloudfront.net | Aplicación web |
| S3 Bucket | `eventos-javeriana-spa-44aa8767` | Archivos estáticos del frontend |
| CloudFront | `E2WXZQBIXPHFUB` | CDN y terminación HTTPS |
| ALB | `eventos-javeriana-alb-...us-east-1` | Balanceador de carga |
| EC2 | `i-07e0425d504f41872` | Instancia con Docker Compose |
| RDS PostgreSQL | `eventos-javeriana-db.co9qgmemsrf7.us-east-1.rds.amazonaws.com` | Base de datos |
| Redis | ElastiCache — VPC privada | Caché del catálogo |
| RabbitMQ | Amazon MQ — VPC privada | Bus de mensajes |

## Topología de red

```
Internet
    │ HTTPS
    ▼
CloudFront (CDN)
    │
    ├──► S3 (SPA estática)
    │
    └──► ALB (Application Load Balancer)
              │
              │ HTTP (VPC privada)
              ▼
           EC2 (Docker Compose)
           ├── auth-service      :8081
           ├── event-service     :8082
           ├── inscription-service :8083
           └── payment-service   :8084
                    │
              VPC Privada
              ├── RDS PostgreSQL
              ├── ElastiCache Redis
              └── Amazon MQ RabbitMQ
```

## Reglas del ALB (path-based routing)

| Prioridad | Patrón de ruta | Target Group | Puerto |
|---|---|---|---|
| 100 | `/auth/*`, `/api/v1/auth*` | auth-tg | 8081 |
| 200 | `/events/*`, `/api/v1/eventos*`, `/api/v1/tarifas*` | event-tg | 8082 |
| 300 | `/inscriptions/*`, `/api/v1/inscripciones*` | insc-tg | 8083 |
| 400 | `/payments/*`, `/api/v1/pagos*`, `/api/v1/webhooks/pagos*` | pay-tg | 8084 |

## Healthchecks

Todos los target groups verifican `GET /actuator/health` → `{"status":"UP"}` cada 30 segundos.

## Estado verificado (2026-06-03)

| Servicio | Target Group | Estado |
|---|---|---|
| auth-service | auth-tg | ✅ healthy |
| event-service | event-tg | ✅ healthy |
| inscription-service | insc-tg | ✅ healthy |
| payment-service | pay-tg | ✅ healthy |

## Infraestructura como código

La infraestructura completa está declarada en Terraform:

```
infra/terraform/
├── main.tf          # Proveedor y módulos
├── vpc.tf           # Red y subnets
├── ec2.tf           # Instancia backend
├── loadbalancer.tf  # ALB y target groups
├── rds.tf           # Base de datos
├── redis.tf         # Caché
├── mq.tf            # Mensajería
├── s3.tf            # Almacenamiento SPA
└── cloudfront.tf    # CDN
```

Para re-crear la infraestructura desde cero: ver [`../operations/despliegue.md`](../operations/despliegue.md).
