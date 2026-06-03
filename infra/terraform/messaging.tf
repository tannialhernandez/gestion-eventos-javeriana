resource "random_password" "mq" {
  length  = 24
  special = false
}

resource "aws_mq_broker" "rabbitmq" {
  broker_name         = "${var.project_name}-mq"
  engine_type         = "RabbitMQ"
  engine_version      = var.rabbitmq_engine_version
  host_instance_type  = var.mq_instance_type
  publicly_accessible = false
  subnet_ids          = [aws_subnet.private[0].id]
  security_groups     = [aws_security_group.mq.id]

  user {
    username = var.mq_username
    password = random_password.mq.result
  }

  logs {
    general = true
  }
}

resource "aws_secretsmanager_secret" "mq_password" {
  name = "${var.project_name}-mq-password"
}

resource "aws_secretsmanager_secret_version" "mq_password" {
  secret_id     = aws_secretsmanager_secret.mq_password.id
  secret_string = random_password.mq.result
}
