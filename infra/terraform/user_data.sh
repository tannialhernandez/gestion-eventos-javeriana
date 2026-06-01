#!/bin/bash
set -euo pipefail

yum update -y
yum install -y docker
systemctl enable docker
systemctl start docker

curl -L "https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-linux-x86_64" \
  -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

mkdir -p /opt/eventos
cat > /opt/eventos/docker-compose.prod.yml <<'EOF'
version: "3.8"

services:
  auth-service-stub:
    image: ${images["auth-service-stub"]}
    environment:
      SPRING_PROFILES_ACTIVE: prod
    ports:
      - "8081:8081"
    restart: unless-stopped

  event-service:
    image: ${images["event-service"]}
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: ${db_host}
      DB_PORT: "${db_port}"
      DB_NAME: ${db_name}
      DB_USERNAME: ${db_username}
      DB_PASSWORD: ${db_password}
      REDIS_HOST: ${redis_endpoint}
      REDIS_PORT: "6379"
      SPRING_RABBITMQ_HOST: ${mq_host}
      SPRING_RABBITMQ_PORT: "5671"
      SPRING_RABBITMQ_USERNAME: ${mq_username}
      SPRING_RABBITMQ_PASSWORD: ${mq_password}
      SPRING_RABBITMQ_SSL_ENABLED: "true"
      RABBITMQ_HOST: ${mq_host}
      RABBITMQ_PORT: "5671"
      RABBITMQ_USERNAME: ${mq_username}
      RABBITMQ_PASSWORD: ${mq_password}
      RABBITMQ_VHOST: "/"
      JWT_PUBLIC_KEY: ${jwt_public_key}
      RETENTION_ENABLED: "true"
    ports:
      - "8082:8082"
    restart: unless-stopped

  payment-service:
    image: ${images["payment-service"]}
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: ${db_host}
      DB_PORT: "${db_port}"
      DB_NAME: ${db_name}
      DB_USERNAME: ${db_username}
      DB_PASSWORD: ${db_password}
      SPRING_RABBITMQ_HOST: ${mq_host}
      SPRING_RABBITMQ_PORT: "5671"
      SPRING_RABBITMQ_USERNAME: ${mq_username}
      SPRING_RABBITMQ_PASSWORD: ${mq_password}
      SPRING_RABBITMQ_SSL_ENABLED: "true"
      RABBITMQ_HOST: ${mq_host}
      RABBITMQ_PORT: "5671"
      RABBITMQ_USERNAME: ${mq_username}
      RABBITMQ_PASSWORD: ${mq_password}
      RABBITMQ_VHOST: "/"
      PAYMENT_GATEWAY_PROVIDER: simulador
      PAYMENT_WEBHOOK_SECRET: ${payment_webhook_secret}
      MERCADOPAGO_WEBHOOK_URL: http://${alb_dns_name}/api/v1/webhooks/pagos
      RETENTION_ENABLED: "true"
    ports:
      - "8084:8084"
    restart: unless-stopped

  inscription-service:
    image: ${images["inscription-service"]}
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: ${db_host}
      DB_PORT: "${db_port}"
      DB_NAME: ${db_name}
      DB_USERNAME: ${db_username}
      DB_PASSWORD: ${db_password}
      SPRING_RABBITMQ_HOST: ${mq_host}
      SPRING_RABBITMQ_PORT: "5671"
      SPRING_RABBITMQ_USERNAME: ${mq_username}
      SPRING_RABBITMQ_PASSWORD: ${mq_password}
      SPRING_RABBITMQ_SSL_ENABLED: "true"
      RABBITMQ_HOST: ${mq_host}
      RABBITMQ_PORT: "5671"
      RABBITMQ_USERNAME: ${mq_username}
      RABBITMQ_PASSWORD: ${mq_password}
      RABBITMQ_VHOST: "/"
      EVENT_SERVICE_URL: http://event-service:8082
      PAYMENT_SERVICE_URL: http://payment-service:8084
      JWT_PUBLIC_KEY: ${jwt_public_key}
      RETENTION_ENABLED: "true"
    ports:
      - "8083:8083"
    restart: unless-stopped
EOF

cd /opt/eventos
/usr/local/bin/docker-compose -f docker-compose.prod.yml up -d
