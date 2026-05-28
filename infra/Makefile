ENV             ?= dev
TF_STATE_BUCKET ?= btl-transport-terraform-state
AWS_REGION      ?= us-east-2

BACKEND_FLAGS := \
  -backend-config="bucket=$(TF_STATE_BUCKET)" \
  -backend-config="region=$(AWS_REGION)" \
  -backend-config="key=btl-transport/$(ENV)/terraform.tfstate"

VAR_FILE := -var-file=env/$(ENV).tfvars

.PHONY: init plan apply destroy

init:
	terraform init $(BACKEND_FLAGS)

plan:
	terraform plan $(VAR_FILE)

apply:
	terraform apply $(VAR_FILE)

destroy:
	terraform destroy $(VAR_FILE)
