output "ecr_repository_url" {
  description = "ECR repository URL"
  value       = aws_ecr_repository.btl_transport.repository_url
}

output "alb_dns_name" {
  description = "ALB DNS name"
  value       = aws_lb.main.dns_name
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.btl.name
}

output "ecs_service_name" {
  description = "ECS service name"
  value       = aws_ecs_service.app.name
}

output "api_url" {
  description = "Full API URL"
  value       = var.create_dns ? "https://${var.domain_name}" : "http://${aws_lb.main.dns_name}"
}

output "uploads_bucket" {
  description = "S3 bucket for file uploads"
  value       = aws_s3_bucket.uploads.id
}

output "github_deploy_role_arn" {
  description = "IAM role ARN for GitHub Actions OIDC"
  value       = aws_iam_role.github_deploy.arn
}
