resource "aws_secretsmanager_secret" "db" {
  name = "btl/db"
  tags = { Name = "btl/db" }
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id     = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({ url = "PLACEHOLDER", username = "PLACEHOLDER", password = "PLACEHOLDER" })

  lifecycle {
    ignore_changes = [secret_string]
  }
}

resource "aws_secretsmanager_secret" "app" {
  name = "btl/app"
  tags = { Name = "btl/app" }
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({
    "aviationstack-api-key" = "PLACEHOLDER"
    "twilio-account-sid"    = "PLACEHOLDER"
    "twilio-auth-token"     = "PLACEHOLDER"
    "twilio-from-number"    = "PLACEHOLDER"
    "twilio-whatsapp-from"  = "PLACEHOLDER"
    "sendgrid-api-key"      = "PLACEHOLDER"
    "sendgrid-from-email"   = "PLACEHOLDER"
    "frontend-base-url"     = "PLACEHOLDER"
    "jwt-secret"            = "PLACEHOLDER"
    "admin-1-username"      = "PLACEHOLDER"
    "admin-1-password-hash" = "PLACEHOLDER"
    "admin-2-username"      = "PLACEHOLDER"
    "admin-2-password-hash" = "PLACEHOLDER"
  })

  lifecycle {
    ignore_changes = [secret_string]
  }
}
