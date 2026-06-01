# Runbook de Despliegue AWS Productivo

## Prerrequisitos

- Cuenta AWS con creditos disponibles y permisos para VPC, EC2, ALB, RDS, ElastiCache, Amazon MQ, S3, CloudFront, IAM y Secrets Manager.
- AWS CLI v2: `aws --version`.
- Terraform 1.6+: `terraform --version`.
- Credenciales configuradas: `aws configure`.
- Imagenes Docker publicadas en GitHub Container Registry.
- Paquetes GHCR publicos o token GHCR disponible en Secrets Manager para login desde EC2.

## 1. Validar entorno

```bash
aws sts get-caller-identity
cd infra/terraform
terraform init
terraform validate
```

## 2. Revisar plan

```bash
cp terraform.tfvars.example terraform.tfvars
terraform plan -out=tfplan
```

Revisar que el costo estimado siga por debajo de USD 200/mes y que `payment_webhook_secret` haya sido reemplazado.

## 3. Aplicar infraestructura

```bash
terraform apply tfplan
```

RDS y Amazon MQ suelen tardar 15-20 minutos. No interrumpir el apply salvo error explicito.

## 4. Validar outputs

```bash
terraform output -json | jq
terraform output deployment_urls
```

Capturar:

- `alb_dns_name`
- `cloudfront_domain`
- `cloudfront_distribution_id`
- `s3_spa_bucket`

## 5. Build y deploy del frontend

```bash
cd ../../frontend
npm install --legacy-peer-deps
npm run build
aws s3 sync dist/ s3://$(terraform -chdir=../infra/terraform output -raw s3_spa_bucket) --delete
aws cloudfront create-invalidation \
  --distribution-id $(terraform -chdir=../infra/terraform output -raw cloudfront_distribution_id) \
  --paths "/*"
```

## 6. Verificar smoke E2E

```bash
ALB=$(terraform -chdir=infra/terraform output -raw alb_dns_name)
curl "http://$ALB/api/v1/eventos?pagina=0&tamano=10"
curl "http://$ALB/actuator/health"
```

Para smoke frontend, configurar `SPA_BASE_URL` contra CloudFront si se publica la SPA con proxy/API gateway equivalente.

## 7. Rollback / cleanup

```bash
cd infra/terraform
terraform destroy
```

Confirmar con `yes`.

## Costos esperados

| Recurso | Estimado mensual |
|---|---:|
| EC2 t3.small | USD 15 |
| RDS db.t4g.micro | USD 13 |
| ElastiCache cache.t3.micro | USD 13 |
| Amazon MQ mq.t3.micro | USD 15 |
| Application Load Balancer | USD 16 |
| NAT Gateway + trafico bajo | USD 32-40 |
| CloudFront + S3 | USD 2 |
| Total | USD 106-114 |

## Limpieza obligatoria

Siempre ejecutar `terraform destroy` despues de la sustentacion o demo si la cuenta AWS no tiene presupuesto permanente. RDS, NAT Gateway, ALB y Amazon MQ generan costo aunque el sistema no reciba trafico.
