variable "aws_region" {
  description = "AWS region"
  default     = "us-east-2"
}

variable "environment" {
  description = "Deployment environment"
  default     = "conference"
}

variable "app_name" {
  description = "Application name"
  default     = "btl-transport"
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

variable "tf_lock_table" {
  description = "DynamoDB table for Terraform state locking"
  type        = string
}

variable "github_org_repo" {
  description = "GitHub org/repo for OIDC trust (e.g. myorg/btl-transport)"
  type        = string
}
