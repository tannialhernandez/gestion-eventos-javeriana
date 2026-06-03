# Terraform AWS - Plataforma de Gestion de Eventos Academicos

Esta carpeta deja declarada la infraestructura AWS necesaria para desplegar la Entrega 3 cuando la cuenta AWS institucional este disponible. El CI ejecuta `terraform fmt`, `terraform init -backend=false` y `terraform validate` sin requerir credenciales reales.

## Componentes

| Archivo | Proposito |
|---|---|
| `versions.tf` | Versiones de Terraform y providers. |
| `variables.tf` | Variables de entrada y defaults FinOps. |
| `network.tf` | VPC, subredes publicas/privadas, Internet Gateway y NAT Gateway. |
| `security.tf` | Security groups de ALB, EC2, RDS, Redis y Amazon MQ. |
| `compute.tf` | EC2 privada con Docker Compose e IAM/SSM. |
| `database.tf` | RDS PostgreSQL 15 y secreto de password. |
| `cache.tf` | ElastiCache Redis 7. |
| `messaging.tf` | Amazon MQ RabbitMQ. |
| `loadbalancer.tf` | ALB, target groups y reglas hacia los 4 servicios. |
| `frontend.tf` | S3 website + CloudFront para la SPA. |
| `outputs.tf` | URLs y endpoints necesarios para smoke/deploy. |
| `user_data.sh` | Bootstrap de Docker Compose en EC2. |

## Validacion local opcional

```bash
cd infra/terraform
terraform fmt -recursive
terraform init -backend=false
terraform validate
```

## Aplicacion cuando exista cuenta AWS

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

RDS y Amazon MQ pueden tardar 15-20 minutos en quedar disponibles.

## Variables principales

| Variable | Default | Comentario |
|---|---|---|
| `aws_region` | `us-east-1` | Region de menor friccion para AWS Academy. |
| `ec2_instance_type` | `t3.small` | Host Docker Compose para los 4 servicios. |
| `rds_instance_class` | `db.t4g.micro` | PostgreSQL 15. |
| `postgres_engine_version` | `15.18` | Version RDS soportada en `us-east-1` al 2026-06-02. |
| `elasticache_node_type` | `cache.t3.micro` | Redis 7. |
| `redis_engine_version` | `7.0` | Version Redis soportada en ElastiCache. |
| `mq_instance_type` | `mq.t3.micro` | Amazon MQ RabbitMQ. |
| `rabbitmq_engine_version` | `3.13` | Version Amazon MQ RabbitMQ soportada en `us-east-1` al 2026-06-02. |
| `container_images` | `ghcr.io/tannialhernandez/*:latest` | Imagenes publicadas por GitHub Actions. |

## Estimacion FinOps mensual

| Recurso | Estimado |
|---|---:|
| EC2 t3.small | USD 15 |
| RDS db.t4g.micro | USD 13 |
| ElastiCache cache.t3.micro | USD 13 |
| Amazon MQ mq.t3.micro | USD 15 |
| Application Load Balancer | USD 16 |
| NAT Gateway + trafico bajo | USD 32-40 |
| S3 + CloudFront | USD 2 |
| Total estimado | USD 106-114 |

La estimacion queda por debajo del limite FinOps declarado de USD 200/mes. Para demos cortas, ejecutar `terraform destroy` inmediatamente despues de la sustentacion.

## Notas operativas

- Si los paquetes GHCR quedan privados, se debe configurar login de GHCR en EC2 via Secrets Manager o volver publicas las imagenes del paquete.
- `auth-service-stub` mantiene su clave demo por alcance MVP; reemplazar por Azure AD/IdP en Fase 2.
- `PAYMENT_GATEWAY_PROVIDER=simulador` se mantiene hasta integrar Mercado Pago real.
