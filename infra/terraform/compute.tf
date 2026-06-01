data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }
}

locals {
  rds_host = split(":", aws_db_instance.postgres.endpoint)[0]
  rds_port = split(":", aws_db_instance.postgres.endpoint)[1]
  mq_host  = replace(replace(aws_mq_broker.rabbitmq.instances[0].endpoints[0], "amqps://", ""), ":5671", "")
}

resource "aws_iam_role" "ec2" {
  name = "${var.project_name}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "ec2_secrets" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/SecretsManagerReadWrite"
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project_name}-ec2-profile"
  role = aws_iam_role.ec2.name
}

resource "aws_instance" "backend" {
  ami                    = data.aws_ami.amazon_linux.id
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.private[0].id
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name

  user_data = templatefile("${path.module}/user_data.sh", {
    alb_dns_name           = aws_lb.main.dns_name
    db_host                = local.rds_host
    db_port                = local.rds_port
    db_name                = var.db_name
    db_username            = var.db_username
    db_password            = random_password.rds.result
    redis_endpoint         = aws_elasticache_cluster.redis.cache_nodes[0].address
    mq_host                = local.mq_host
    mq_username            = var.mq_username
    mq_password            = random_password.mq.result
    jwt_public_key         = var.jwt_public_key
    payment_webhook_secret = var.payment_webhook_secret
    images                 = var.container_images
  })

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
    encrypted   = true
  }

  tags = {
    Name = "${var.project_name}-backend"
  }
}
