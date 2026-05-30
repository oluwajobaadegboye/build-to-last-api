locals {
  secret_names = [
    "btl/db-url",
    "btl/db-username",
    "btl/db-password",
    "btl/aviationstack-api-key",
    "btl/twilio-account-sid",
    "btl/twilio-auth-token",
    "btl/twilio-from-number",
    "btl/twilio-whatsapp-from",
    "btl/sendgrid-api-key",
    "btl/sendgrid-from-email",
    "btl/frontend-base-url",
    "btl/jwt-secret",
    "btl/admin-1-username",
    "btl/admin-1-password-hash",
    "btl/admin-2-username",
    "btl/admin-2-password-hash",
  ]
}

resource "aws_secretsmanager_secret" "btl" {
  for_each = toset(local.secret_names)
  name     = each.value

  tags = { Name = each.value }
}

resource "aws_secretsmanager_secret_version" "btl" {
  for_each      = toset(local.secret_names)
  secret_id     = aws_secretsmanager_secret.btl[each.key].id
  secret_string = "PLACEHOLDER — update via AWS Console or CLI before deploying"

  lifecycle {
    ignore_changes = [secret_string]
  }
}
