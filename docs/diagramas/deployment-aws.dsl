workspace "Gestión de Eventos Académicos - Deployment" "Vista Física AWS - Kruchten 4+1 / C4 Nivel 4" {

  model {
    estudiante   = person "Estudiante"            "Inscribe y gestiona participaciones en eventos"
    coordinador  = person "Coordinador Académico"  "Publica y administra eventos"

    sistema = softwareSystem "Plataforma de Gestión de Eventos Académicos" "Sistema distribuido de microservicios" {
      frontendSpa    = container "React SPA"            "Interfaz web"        "React 18 + Vite"
      eventSvc       = container "event-service"        "Gestión de eventos"  "Spring Boot 3 Java 17"
      inscriptionSvc = container "inscription-service"  "Gestión de inscripciones. Outbox + ShedLock" "Spring Boot 3 Java 17"
      paymentSvc     = container "payment-service"      "Procesamiento de pagos" "Spring Boot 3 Java 17"
      notifSvc       = container "notification-service" "Envío de notificaciones" "Spring Boot 3 Java 17"
      wireMock       = container "WireMock"             "Mock pasarela de pagos" "WireMock 3.x"
      postgresDb     = container "PostgreSQL"           "4 bases: event_db, inscription_db, payment_db, notification_db" "PostgreSQL 15"
      redisCache     = container "Redis"                "Cache distribuido - idempotency keys, sessions" "Redis 7"
      rabbitMq       = container "RabbitMQ"             "Message broker - Outbox relay, eventos de pago" "RabbitMQ 3.x"
    }

    estudiante  -> frontendSpa    "Usa vía navegador" "HTTPS"
    coordinador -> eventSvc       "Gestiona eventos"  "HTTPS REST"

    eventSvc       -> postgresDb "Persiste datos" "JDBC:5432 event_db"
    inscriptionSvc -> postgresDb "Persiste datos" "JDBC:5432 inscription_db"
    paymentSvc     -> postgresDb "Persiste datos" "JDBC:5432 payment_db"
    notifSvc       -> postgresDb "Persiste datos" "JDBC:5432 notification_db"

    inscriptionSvc -> redisCache "Idempotency + rate limiting" "TCP:6379"
    paymentSvc     -> redisCache "Session cache"               "TCP:6379"

    inscriptionSvc -> rabbitMq "Publica InscripcionCreadaEvent (Outbox ADR-008)" "AMQPS:5671"
    paymentSvc     -> rabbitMq "Publica PagoConfirmadoEvent / PagoExpiradoEvent" "AMQPS:5671"
    notifSvc       -> rabbitMq "Consume eventos de notificacion"                 "AMQPS:5671"
    paymentSvc     -> wireMock "Mock pasarela de pagos externos"                 "HTTP:8089"

    aws = deploymentEnvironment "AWS us-east-1 (Produccion)" {

      globalEdge = deploymentNode "AWS Global Services" "Servicios de borde fuera de VPC" "AWS Global" {
        route53    = infrastructureNode "Amazon Route53"    "Hosted zone. A-alias a ALB y CloudFront" "AWS Route53"
        cloudFront = infrastructureNode "Amazon CloudFront" "CDN + TLS termination para SPA. Origin Access Control" "AWS CloudFront"
        s3Bucket   = deploymentNode "Amazon S3 eventos-frontend-prod" "Bucket privado SPA. OAC desde CloudFront" "AWS S3" {
          frontendInst = containerInstance frontendSpa
        }
        ecrRegistry = infrastructureNode "Amazon ECR eventos-registry" "Repos privados Docker: event-service, inscription-service, payment-service, notification-service" "AWS ECR"
      }

      vpc = deploymentNode "VPC eventos-vpc (10.0.0.0/16)" "" "AWS VPC" {

        igw = infrastructureNode "Internet Gateway igw-eventos" "Conecta VPC a Internet para subnets publicas" "AWS IGW"

        pubAz1 = deploymentNode "Public Subnet AZ-1a (10.0.1.0/24)" "" "AWS Subnet" {
          alb   = infrastructureNode "Application Load Balancer eventos-alb" "Listener HTTPS:443, cert ACM. sg-alb. Path-routing a EC2:8081-8084" "AWS ALB"
          natGw = infrastructureNode "NAT Gateway nat-eventos"               "EIP fija. Egress subnet privada a Internet" "AWS NAT Gateway"
        }

        pubAz2 = deploymentNode "Public Subnet AZ-1b (10.0.2.0/24)" "" "AWS Subnet" {
          albAz2 = infrastructureNode "ALB node AZ-1b" "Nodo secundario ALB para HA del balanceador" "AWS ALB"
        }

        privAz1 = deploymentNode "Private Subnet AZ-1a (10.0.11.0/24)" "" "AWS Subnet" {

          ec2 = deploymentNode "EC2 t3.small eventos-app-server" "Amazon Linux 2023. 2 vCPU / 2 GB. sg-ec2. IAM Role: eventos-ec2-role" "AWS EC2" {
            dockerRuntime = deploymentNode "Docker Engine eventos-net bridge" "Docker Compose v2. /opt/eventos/docker-compose.yml" "Docker Compose" {
              eventInst  = containerInstance eventSvc
              inscInst   = containerInstance inscriptionSvc
              payInst    = containerInstance paymentSvc
              notifInst  = containerInstance notifSvc
              mockInst   = containerInstance wireMock
            }
          }

          rds = deploymentNode "RDS db.t4g.micro eventos-rds" "PostgreSQL 15. Multi-AZ OFF. sg-rds. 20GB gp3. Backup 7 dias" "AWS RDS" {
            pgInst = containerInstance postgresDb
          }

          elasticache = deploymentNode "ElastiCache cache.t4g.micro eventos-cache" "Redis 7. Single-node. sg-redis. Puerto 6379" "AWS ElastiCache" {
            redisInst = containerInstance redisCache
          }

          amazonMq = deploymentNode "Amazon MQ mq.t3.micro eventos-broker" "RabbitMQ 3.x. Single-instance. sg-mq. AMQPS:5671" "AWS Amazon MQ" {
            mqInst = containerInstance rabbitMq
          }

          secretsMgr = infrastructureNode "AWS Secrets Manager" "Secretos: rds/master, rabbitmq/creds, jwt-secret, payment-api-key" "AWS Secrets Manager"
          cwLogs     = infrastructureNode "CloudWatch Logs"     "Log groups por servicio. CloudWatch Agent. Retencion 14 dias." "AWS CloudWatch Logs"
          cwMetrics  = infrastructureNode "CloudWatch Metrics y Alarms" "EMF metrics. Alarmas: CPU, 5xx, RDS, MQ. Dashboard: eventos-prod." "AWS CloudWatch"
        }

        privAz2 = deploymentNode "Private Subnet AZ-1b (10.0.12.0/24)" "" "AWS Subnet" {
          rdsStandby = infrastructureNode "RDS Standby deshabilitado" "Multi-AZ OFF por presupuesto. Gap en ADR-004." "AWS RDS"
        }
      }
    }
  }

  views {
    deployment sistema "AWS us-east-1 (Produccion)" "DeploymentAWS" "Vista Fisica C4 Nivel 4 - AWS Deployment (Kruchten Physical View)" {
      include *
      autoLayout tb
    }

    theme default
  }
}
