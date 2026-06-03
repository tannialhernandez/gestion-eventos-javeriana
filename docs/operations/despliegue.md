# Procedimientos de Despliegue

**PUJ · Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## Despliegue automático (CI/CD)

El sistema se despliega automáticamente mediante GitHub Actions al fusionar cambios en `main`.

### Frontend (SPA)

**Workflow:** `.github/workflows/deploy-frontend.yml`

1. `npm run build` — genera `dist/`
2. `aws s3 sync dist/ s3://eventos-javeriana-spa-44aa8767/ --delete`
3. `aws cloudfront create-invalidation --distribution-id E2WXZQBIXPHFUB --paths "/*"`

**Duración estimada:** 2-3 minutos.

### Backend (microservicios)

Los servicios se despliegan mediante imagen Docker en GHCR:

1. Build de imagen Docker → push a `ghcr.io/tannialhernandez/<servicio>:main`
2. EC2 hace `docker pull` y `docker compose up -d --force-recreate`

---

## Despliegue manual del frontend

```bash
cd frontend
npm run build

# Sincronizar con S3
aws s3 sync dist/ s3://eventos-javeriana-spa-44aa8767/ --delete

# Invalidar caché de CloudFront
aws cloudfront create-invalidation \
  --distribution-id E2WXZQBIXPHFUB \
  --paths "/*"
```

---

## Despliegue manual de un microservicio en EC2

```bash
# 1. Conectar a la instancia vía SSM
aws ssm start-session --target i-07e0425d504f41872

# 2. En la instancia:
cd /opt/eventos

# 3. Actualizar y reiniciar un servicio específico
sudo docker compose pull event-service
sudo docker compose up -d event-service

# 4. Verificar salud
curl -s http://localhost:8082/actuator/health
```

---

## Recrear infraestructura desde cero (Terraform)

```bash
cd infra/terraform

# Planificar cambios
terraform plan -out=tfplan

# Aplicar
terraform apply tfplan
```

**Precaución:** `terraform destroy` elimina toda la infraestructura incluyendo RDS. Requiere aprobación explícita.

---

## Rollback del frontend

```bash
# Desplegar una versión anterior del build
aws s3 sync s3://eventos-javeriana-spa-44aa8767-backup/ \
            s3://eventos-javeriana-spa-44aa8767/ --delete
aws cloudfront create-invalidation \
  --distribution-id E2WXZQBIXPHFUB --paths "/*"
```

---

## Rollback de un microservicio

```bash
# En la instancia EC2:
sudo docker pull ghcr.io/tannialhernandez/event-service:<TAG_ANTERIOR>
# Actualizar imagen en docker-compose.prod.yml
sudo docker compose up -d event-service
```
