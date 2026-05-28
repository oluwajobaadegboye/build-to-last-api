variable "aws_region" {
  description = "AWS region"
  default     = "us-east-2"
}

variable "environment" {
  description = "Deployment environment (dev, test, prod)"
  type        = string
}

variable "app_name" {
  description = "Application name — include env suffix for dev/test (e.g. btl-transport-dev)"
  type        = string
}

variable "domain_name" {
  description = "Full domain name for the API (e.g. transport.btl2026.com)"
  type        = string
}

variable "ecr_image_tag" {
  description = "Docker image tag to deploy"
  default     = "latest"
}

variable "desired_count" {
  description = "ECS desired task count (set to 0 between conferences)"
  default     = 1
}

variable "cpu" {
  description = "ECS task CPU units"
  default     = 512
}

variable "memory" {
  description = "ECS task memory (MB)"
  default     = 1024
}

variable "tf_state_bucket" {
  description = "S3 bucket for Terraform state"
  type        = string
}

variable "github_org_repo" {
  description = "GitHub org/repo for OIDC trust (e.g. myorg/btl-transport)"
  type        = string
}

variable "create_dns" {
  description = "Create Route 53 record and ACM certificate (set false until domain is registered)"
  type        = bool
  default     = false
}

variable "alert_email" {
  description = "Email address to receive CloudWatch alarm notifications"
  type        = string
}

variable "min_capacity" {
  description = "Minimum ECS task count for auto-scaling"
  default     = 1
}

variable "max_capacity" {
  description = "Maximum ECS task count for auto-scaling"
  default     = 2
}
