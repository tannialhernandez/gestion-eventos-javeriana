variable "aws_region" {
  description = "Region AWS."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Nombre del proyecto."
  type        = string
  default     = "eventos-javeriana"
}

variable "environment" {
  description = "Ambiente (dev/staging/prod)."
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "CIDR de la VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "ec2_instance_type" {
  description = "Tipo de instancia EC2 para Docker Compose."
  type        = string
  default     = "t3.small"
}

variable "rds_instance_class" {
  description = "Clase de instancia RDS PostgreSQL."
  type        = string
  default     = "db.t4g.micro"
}

variable "elasticache_node_type" {
  description = "Tipo de nodo ElastiCache Redis."
  type        = string
  default     = "cache.t3.micro"
}

variable "mq_instance_type" {
  description = "Tipo de instancia Amazon MQ RabbitMQ."
  type        = string
  default     = "mq.t3.micro"
}

variable "db_name" {
  description = "Nombre de la base de datos compartida para los microservicios."
  type        = string
  default     = "eventos"
}

variable "db_username" {
  description = "Usuario administrador de RDS."
  type        = string
  default     = "eventos_admin"
}

variable "mq_username" {
  description = "Usuario de Amazon MQ RabbitMQ."
  type        = string
  default     = "eventos_mq"
}

variable "jwt_public_key" {
  description = "Clave publica RSA usada por event-service e inscription-service para validar JWT."
  type        = string
  default     = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4C3rkbm3lVOmdTFJH/fGTNz1Fk+ScJIu+SkFXJdu8D9C10X9Bmpj97owkfgX2zGbZ+jDjbvwCIDkIKxW1KzgdeGxEWSnYUBCH5fakqW1f6eA1B08yoWT1WzAB46CI6Dlu9Fd965/zN1tWzzFrpvmrmqZDpm5fVsCxsDSrRrKhO36wMJVpXMFxRNxmYIbQmw/CsKCA4oJdTaC0wKF86s5p3sneqs4tH/eTNhjyrbpufYkS6aDWOxSxtqhX5C1CRqF+65QoLyvRlTocLs69O+XRQoDYxa5teZM77stmLMqAjZCtDVpSnNaYknsaqQnS9AfPiYAKJ3dd9ql/jYF5UtF9QIDAQAB"
}

variable "payment_webhook_secret" {
  description = "Secreto HMAC para webhooks de payment-service. Reemplazar en terraform.tfvars real."
  type        = string
  sensitive   = true
  default     = "change-me-before-production"
}

variable "container_images" {
  description = "Mapa de imagenes Docker en ghcr.io."
  type        = map(string)
  default = {
    event-service       = "ghcr.io/tannialhernandez/event-service:latest"
    inscription-service = "ghcr.io/tannialhernandez/inscription-service:latest"
    payment-service     = "ghcr.io/tannialhernandez/payment-service:latest"
    auth-service-stub   = "ghcr.io/tannialhernandez/auth-service-stub:latest"
  }
}
