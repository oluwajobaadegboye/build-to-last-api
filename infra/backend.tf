terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Backend values must be literals — pass via -backend-config or terraform.tfvars
  # terraform init -backend-config="bucket=YOUR_BUCKET" -backend-config="dynamodb_table=YOUR_TABLE"
  backend "s3" {
    key     = "btl-transport/terraform.tfstate"
    encrypt = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "BTL2026"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}
