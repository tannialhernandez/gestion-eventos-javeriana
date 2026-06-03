# Backup y Recuperación

**PUJ · Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## Objetivos de recuperación

| Métrica | Objetivo |
|---|---|
| RPO (Recovery Point Objective) | 24 horas |
| RTO (Recovery Time Objective) | 2 horas |

---

## Base de datos (RDS PostgreSQL)

### Backup automático

AWS RDS está configurado para:
- **Backup automático diario** (período de retención: 7 días).
- **Point-in-Time Recovery (PITR)** — recuperación a cualquier segundo del último período.
- **Snapshot manual** antes de cambios mayores.

### Crear snapshot manual

```bash
aws rds create-db-snapshot \
  --db-instance-identifier eventos-javeriana-db \
  --db-snapshot-identifier "manual-$(date +%Y%m%d)"
```

### Restaurar desde snapshot

```bash
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier eventos-javeriana-db-restored \
  --db-snapshot-identifier <SNAPSHOT_ID>
```

### Backup manual de esquemas

```bash
# Exportar cada esquema
PGPASSWORD=<PASSWORD> pg_dump \
  -h eventos-javeriana-db.co9qgmemsrf7.us-east-1.rds.amazonaws.com \
  -U eventos_admin -d eventos \
  --schema=public -Fc > backup-event-$(date +%Y%m%d).dump

PGPASSWORD=<PASSWORD> pg_dump ... --schema=inscription > backup-inscription-$(date +%Y%m%d).dump
PGPASSWORD=<PASSWORD> pg_dump ... --schema=payment > backup-payment-$(date +%Y%m%d).dump
```

---

## Frontend (S3)

Los archivos estáticos del frontend se pueden reconstruir en cualquier momento con `npm run build`. No requieren backup — el código fuente en Git es la fuente de verdad.

```bash
# Reconstruir y republicar
cd frontend && npm run build
aws s3 sync dist/ s3://eventos-javeriana-spa-44aa8767/ --delete
aws cloudfront create-invalidation --distribution-id E2WXZQBIXPHFUB --paths "/*"
```

---

## Infraestructura (Terraform)

El estado de Terraform se almacena en:

```
infra/terraform/terraform.tfstate
```

Para recuperar la infraestructura completa:

```bash
cd infra/terraform
terraform init
terraform apply -auto-approve
```

---

## Plan de recuperación ante desastre

1. **Base de datos**: restaurar último snapshot en RDS (< 30 min).
2. **Backend**: reconstruir instancia EC2 con Terraform + `docker compose up -d` (< 45 min).
3. **Frontend**: `npm run build` → S3 sync (< 10 min).
4. **DNS/CDN**: CloudFront apunta automáticamente a S3 (no requiere cambio).

**Tiempo total estimado de recuperación completa:** < 2 horas (RTO).
