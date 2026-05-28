environment    = "test"
create_dns     = false
app_name       = "btl-transport-test"
domain_name    = "test-transport.btl2026.com"
ecr_image_tag  = "test"
desired_count  = 1
cpu            = 512
memory         = 1024
min_capacity   = 1
max_capacity   = 2
alert_email    = "oluwajobaadegboye@hotmail.com"

tf_state_bucket = "btl-transport-terraform-state"
github_org_repo = "your-org/btl-transport"   # REPLACE
