# BTL 2026 Conference Transport System — Backend

Spring Boot 3.x backend for the Built to Last 2026 conference transport system.
Manages airport pickups, daily hotel-to-church shuttles, flight monitoring, and SMS/email notifications
for 250–400 participants across June 11–14, 2026 in Indianapolis.

## Tech Stack

- **Java 21** / Spring Boot 3.3 / Maven
- **Database**: Supabase (PostgreSQL) — tables already exist, Flyway runs ALTER migrations only
- **Notifications**: Twilio (SMS/WhatsApp) + SendGrid (email)
- **Flight data**: AviationStack REST API
- **Infrastructure**: AWS ECS Fargate + ALB + ECR via Terraform
- **CI/CD**: GitHub Actions with OIDC (no static AWS keys)

## Local Setup

### Prerequisites
- Java 21 (Eclipse Temurin recommended)
- Maven 3.9+
- A running Supabase PostgreSQL instance with the existing schema

### 1. Configure environment

```bash
cp .env.example .env
# Edit .env with your real credentials
```

### 2. Export env vars and run

```bash
set -a && source .env && set +a
./mvnw spring-boot:run
```

Or with explicit env:

```bash
SUPABASE_DB_URL=jdbc:postgresql://... \
SUPABASE_DB_USERNAME=postgres \
SUPABASE_DB_PASSWORD=... \
ADMIN_API_KEY=local-dev-key \
AVIATIONSTACK_API_KEY=... \
TWILIO_ACCOUNT_SID=... \
TWILIO_AUTH_TOKEN=... \
TWILIO_FROM_NUMBER=+1... \
SENDGRID_API_KEY=... \
FRONTEND_BASE_URL=http://localhost:5173 \
./mvnw spring-boot:run
```

### 3. Verify

```bash
curl http://localhost:8080/api/v1/health
# → {"status":"UP","db":"UP","timestamp":"..."}

curl http://localhost:8080/api/v1/hotels
curl http://localhost:8080/api/v1/shuttle-status
```

### 4. Run tests

```bash
./mvnw test
```

## API Reference

### Public Endpoints (no auth)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/health` | Health check |
| GET | `/api/v1/hotels` | List hotels |
| GET | `/api/v1/shuttle-status` | Full shuttle schedule |
| GET | `/api/v1/participant-status?code=BTL-042` | Participant transport status |
| POST | `/api/v1/register` | Register a participant |
| POST | `/api/v1/update-flight` | Update flight info |
| GET | `/api/v1/coordinator-contacts` | Coordinator contact info |
| POST | `/api/v1/twilio-webhook` | Inbound SMS webhook |

### Admin Endpoints (require `ADMIN_API_KEY` header)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/admin/dashboard` | Stats overview |
| GET | `/api/v1/admin/participants` | Paginated participant list |
| PATCH | `/api/v1/admin/participants/{id}/hotel` | Update hotel |
| PATCH | `/api/v1/admin/participants/{id}/attention` | Flag/unflag attention |
| GET | `/api/v1/admin/alerts` | Active alerts |
| POST | `/api/v1/admin/alerts/{btlCode}/resolve` | Resolve alert |
| GET | `/api/v1/admin/runs` | List runs by day |
| PATCH | `/api/v1/admin/runs/{id}/driver` | Assign driver |
| PATCH | `/api/v1/admin/runs/{id}/vehicle` | Assign vehicle |
| PATCH | `/api/v1/admin/run-participants/boarded` | Mark passenger boarded |
| GET/POST/PATCH | `/api/v1/admin/drivers` | Driver management |
| GET/POST/PATCH | `/api/v1/admin/vehicles` | Vehicle management |

Admin header: `ADMIN_API_KEY: your-api-key`

API docs: `http://localhost:8080/api/swagger-ui`

## Flyway Migrations

Tables already exist in Supabase. Flyway only runs ALTER TABLE additions.

| Version | Description |
|---------|-------------|
| V1 | Baseline (no-op — marks existing tables) |
| V2 | Add missing columns (attention_reason, delay_mins, etc.) |
| V3 | Performance indexes |
| V4 | BTL code sequence |
| V5 | Seed shuttle_config |
| V6 | Seed airport_config |
| V7 | Seed notification_config |

**Before first run** — verify TEXT columns are clean in Supabase:
```sql
SELECT depart_time FROM runs WHERE depart_time !~ '^\d{2}:\d{2}$';
SELECT window_start FROM shuttle_config WHERE window_start !~ '^\d{2}:\d{2}$';
```
Empty results = safe.

## Deployment

### Prerequisites
1. AWS account with Terraform state bucket + DynamoDB lock table
2. Route 53 hosted zone for your domain

### First-time infrastructure setup

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your values

terraform init \
  -backend-config="bucket=YOUR_STATE_BUCKET" \
  -backend-config="region=us-east-2" \
  -backend-config="dynamodb_table=YOUR_LOCK_TABLE"

terraform plan
terraform apply
```

After apply, note the outputs:
- `github_deploy_role_arn` → add as `AWS_ROLE_ARN` GitHub secret
- `ecr_repository_url` → used by CI/CD

### Set secrets in AWS Secrets Manager

```bash
aws secretsmanager put-secret-value \
  --secret-id btl/supabase-db-url \
  --secret-string "jdbc:postgresql://db.xxx.supabase.co:5432/postgres"

# Repeat for all secrets in infra/secrets.tf
```

### GitHub repository secrets

Only two secrets needed in GitHub (Settings → Secrets → Actions):
```
AWS_ROLE_ARN    arn:aws:iam::ACCOUNT_ID:role/btl-transport-github-deploy-role
AWS_REGION      us-east-2
```

### Deploy

Push to `main` → CI tests run → Docker image pushed to ECR → ECS service updated automatically.

Manual deploy trigger: Actions → "Deploy Backend" → Run workflow.

### Scale down between conferences

```bash
aws ecs update-service \
  --cluster btl-transport-cluster \
  --service btl-transport-service \
  --desired-count 0 \
  --region us-east-2
```

## Switching the Vercel Frontend

Change one environment variable in Vercel:

```
# Before (n8n)
VITE_API_BASE_URL=https://your-n8n.railway.app/webhook

# After (Spring Boot)
VITE_API_BASE_URL=https://transport.btl2026.com/api/v1
```

Response shapes are preserved exactly — the frontend needs no code changes.

Test each endpoint individually before switching production traffic:
```bash
BASE=https://transport.btl2026.com/api/v1
curl $BASE/health
curl $BASE/shuttle-status
curl "$BASE/participant-status?code=BTL-001"
```

## Key Business Rules

- **BTL codes**: Generated atomically from PostgreSQL sequence `btl_code_seq` (format: `BTL-001`)
- **Flight polling**: Active Jun 11 all day + Jun 12 until noon (America/Indiana/Indianapolis)
- **Leg 4 pickup**: If departure before hotel cutoff time → pick up from church; after → from hotel
- **Delay classification**: < 2hrs = minor SMS only; 2–4hrs = major SMS+email; ≥ 4hrs = critical
- **Notifications**: Best-effort — SMS failure never fails a registration
- **Admin auth**: `ADMIN_API_KEY` header on all `/api/v1/admin/*` routes (no JWT)
