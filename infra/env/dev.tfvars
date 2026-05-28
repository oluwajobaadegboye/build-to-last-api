environment    = "dev"
create_dns     = false
app_name       = "btl-transport-dev"
domain_name    = "dev-transport.btl2026.com"
ecr_image_tag  = "dev"
desired_count  = 1
cpu            = 256
memory         = 512
min_capacity   = 1
max_capacity   = 1
alert_email    = "oluwajobaadegboye@hotmail.com"

tf_state_bucket = "btl-transport-terraform-state"
github_org_repo = "your-org/btl-transport"   # REPLACE
