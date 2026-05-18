resource "aws_ecs_cluster" "btl" {
  name = "${var.app_name}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = { Name = "${var.app_name}-cluster" }
}

resource "aws_ecs_task_definition" "app" {
  family                   = var.app_name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn

  container_definitions = jsonencode([{
    name      = var.app_name
    image     = "${aws_ecr_repository.btl_transport.repository_url}:${var.ecr_image_tag}"
    essential = true

    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]

    secrets = [
      { name = "SUPABASE_DB_URL",       valueFrom = aws_secretsmanager_secret.btl["btl/supabase-db-url"].arn },
      { name = "SUPABASE_DB_USERNAME",  valueFrom = aws_secretsmanager_secret.btl["btl/supabase-db-username"].arn },
      { name = "SUPABASE_DB_PASSWORD",  valueFrom = aws_secretsmanager_secret.btl["btl/supabase-db-password"].arn },
      { name = "AVIATIONSTACK_API_KEY", valueFrom = aws_secretsmanager_secret.btl["btl/aviationstack-api-key"].arn },
      { name = "TWILIO_ACCOUNT_SID",    valueFrom = aws_secretsmanager_secret.btl["btl/twilio-account-sid"].arn },
      { name = "TWILIO_AUTH_TOKEN",     valueFrom = aws_secretsmanager_secret.btl["btl/twilio-auth-token"].arn },
      { name = "TWILIO_FROM_NUMBER",    valueFrom = aws_secretsmanager_secret.btl["btl/twilio-from-number"].arn },
      { name = "TWILIO_WHATSAPP_FROM",  valueFrom = aws_secretsmanager_secret.btl["btl/twilio-whatsapp-from"].arn },
      { name = "SENDGRID_API_KEY",      valueFrom = aws_secretsmanager_secret.btl["btl/sendgrid-api-key"].arn },
      { name = "SENDGRID_FROM_EMAIL",   valueFrom = aws_secretsmanager_secret.btl["btl/sendgrid-from-email"].arn },
      { name = "FRONTEND_BASE_URL",      valueFrom = aws_secretsmanager_secret.btl["btl/frontend-base-url"].arn },
      { name = "JWT_SECRET",            valueFrom = aws_secretsmanager_secret.btl["btl/jwt-secret"].arn },
      { name = "ADMIN_1_USERNAME",      valueFrom = aws_secretsmanager_secret.btl["btl/admin-1-username"].arn },
      { name = "ADMIN_1_PASSWORD_HASH", valueFrom = aws_secretsmanager_secret.btl["btl/admin-1-password-hash"].arn },
      { name = "ADMIN_2_USERNAME",      valueFrom = aws_secretsmanager_secret.btl["btl/admin-2-username"].arn },
      { name = "ADMIN_2_PASSWORD_HASH", valueFrom = aws_secretsmanager_secret.btl["btl/admin-2-password-hash"].arn },
    ]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])

  tags = { Name = var.app_name }
}

resource "aws_ecs_service" "app" {
  name            = "${var.app_name}-service"
  cluster         = aws_ecs_cluster.btl.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  health_check_grace_period_seconds = 120

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = var.app_name
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.https]

  tags = { Name = "${var.app_name}-service" }
}
