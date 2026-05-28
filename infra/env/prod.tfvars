environment    = "prod"
create_dns     = true
app_name       = "btl-transport"
domain_name    = "transport.btl2026.com"
ecr_image_tag  = "latest"
desired_count  = 1
cpu            = 512
memory         = 1024
min_capacity   = 1
max_capacity   = 3
alert_email    = "oluwajobaadegboye@hotmail.com"

tf_state_bucket = "btl-transport-terraform-state"
github_org_repo = "your-org/btl-transport"   # REPLACE
