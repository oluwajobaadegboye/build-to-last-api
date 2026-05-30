terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # All backend values are passed via -backend-config flags — see Makefile.
  # terraform init \
  #   -backend-config="bucket=BUCKET" \
  #   -backend-config="region=us-east-2" \
  #   -backend-config="key=btl-transport/ENV/terraform.tfstate"
  backend "s3" {
    use_lockfile = true
    encrypt      = true
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
