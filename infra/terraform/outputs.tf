output "alb_dns_name" {
  description = "DNS publico del ALB backend."
  value       = aws_lb.main.dns_name
}

output "cloudfront_domain" {
  description = "Dominio CloudFront para la SPA."
  value       = aws_cloudfront_distribution.spa.domain_name
}

output "cloudfront_distribution_id" {
  description = "ID de distribucion CloudFront para invalidaciones."
  value       = aws_cloudfront_distribution.spa.id
}

output "rds_endpoint" {
  description = "Endpoint RDS PostgreSQL."
  value       = aws_db_instance.postgres.endpoint
  sensitive   = true
}

output "redis_endpoint" {
  description = "Endpoint Redis."
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
  sensitive   = true
}

output "mq_endpoint" {
  description = "Endpoint Amazon MQ RabbitMQ."
  value       = aws_mq_broker.rabbitmq.instances[0].endpoints
  sensitive   = true
}

output "s3_spa_bucket" {
  description = "Nombre del bucket S3 para la SPA."
  value       = aws_s3_bucket.spa.bucket
}

output "deployment_urls" {
  description = "URLs de acceso al sistema."
  value = {
    frontend_spa   = "https://${aws_cloudfront_distribution.spa.domain_name}"
    backend_api    = "http://${aws_lb.main.dns_name}"
    auth_endpoint  = "http://${aws_lb.main.dns_name}/api/v1/auth"
    event_endpoint = "http://${aws_lb.main.dns_name}/api/v1/eventos"
  }
}
