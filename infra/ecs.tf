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
      { name = "DB_URL",      valueFrom = aws_secretsmanager_secret.btl["btl/db-url"].arn },
      { name = "DB_USERNAME", valueFrom = aws_secretsmanager_secret.btl["btl/db-username"].arn },
      { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.btl["btl/db-password"].arn },
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

  force_new_deployment = true

  depends_on = [aws_lb_listener.https, aws_lb_listener.http]

  lifecycle {
    ignore_changes = [desired_count]
  }

  tags = { Name = "${var.app_name}-service" }
}

resource "aws_appautoscaling_target" "ecs" {
  max_capacity       = var.max_capacity
  min_capacity       = var.min_capacity
  resource_id        = "service/${aws_ecs_cluster.btl.name}/${aws_ecs_service.app.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${var.app_name}-cpu-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 65.0
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
  }
}
