resource "aws_lb" "main" {
  name               = "${var.project_name}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id
}

resource "aws_lb_target_group" "auth" {
  name     = "${var.project_name}-auth-tg"
  port     = 8081
  protocol = "HTTP"
  vpc_id   = aws_vpc.main.id

  health_check {
    path    = "/actuator/health"
    matcher = "200"
  }
}

resource "aws_lb_target_group" "event" {
  name     = "${var.project_name}-event-tg"
  port     = 8082
  protocol = "HTTP"
  vpc_id   = aws_vpc.main.id

  health_check {
    path    = "/actuator/health"
    matcher = "200"
  }
}

resource "aws_lb_target_group" "inscription" {
  name     = "${var.project_name}-insc-tg"
  port     = 8083
  protocol = "HTTP"
  vpc_id   = aws_vpc.main.id

  health_check {
    path    = "/actuator/health"
    matcher = "200"
  }
}

resource "aws_lb_target_group" "payment" {
  name     = "${var.project_name}-pay-tg"
  port     = 8084
  protocol = "HTTP"
  vpc_id   = aws_vpc.main.id

  health_check {
    path    = "/actuator/health"
    matcher = "200"
  }
}

resource "aws_lb_target_group_attachment" "auth" {
  target_group_arn = aws_lb_target_group.auth.arn
  target_id        = aws_instance.backend.id
  port             = 8081
}

resource "aws_lb_target_group_attachment" "event" {
  target_group_arn = aws_lb_target_group.event.arn
  target_id        = aws_instance.backend.id
  port             = 8082
}

resource "aws_lb_target_group_attachment" "inscription" {
  target_group_arn = aws_lb_target_group.inscription.arn
  target_id        = aws_instance.backend.id
  port             = 8083
}

resource "aws_lb_target_group_attachment" "payment" {
  target_group_arn = aws_lb_target_group.payment.arn
  target_id        = aws_instance.backend.id
  port             = 8084
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Eventos Javeriana: usa /api/v1/auth, /api/v1/eventos, /api/v1/inscripciones o /api/v1/pagos"
      status_code  = "200"
    }
  }
}

resource "aws_lb_listener_rule" "auth" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.auth.arn
  }

  condition {
    path_pattern {
      values = ["/auth/*", "/api/v1/auth*"]
    }
  }
}

resource "aws_lb_listener_rule" "event" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 200

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.event.arn
  }

  condition {
    path_pattern {
      values = ["/events/*", "/api/v1/eventos*", "/api/v1/tarifas*"]
    }
  }
}

resource "aws_lb_listener_rule" "inscription" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 300

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.inscription.arn
  }

  condition {
    path_pattern {
      values = ["/inscriptions/*", "/api/v1/inscripciones*", "/api/v1/asistencias*", "/api/v1/certificados*"]
    }
  }
}

resource "aws_lb_listener_rule" "payment" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 400

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.payment.arn
  }

  condition {
    path_pattern {
      values = ["/payments/*", "/api/v1/pagos*", "/api/v1/webhooks/pagos*"]
    }
  }
}
