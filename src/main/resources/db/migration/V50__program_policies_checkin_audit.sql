-- Per-program policies (all default to today's behaviour).
ALTER TABLE programs ADD COLUMN IF NOT EXISTS show_payment_to_attendees BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE programs ADD COLUMN IF NOT EXISTS registrations_see_amounts BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE programs ADD COLUMN IF NOT EXISTS payment_emails_enabled   BOOLEAN NOT NULL DEFAULT FALSE;
-- Registration closes automatically at this local time ("YYYY-MM-DDTHH:mm", program timezone) and/or when full.
ALTER TABLE programs ADD COLUMN IF NOT EXISTS registration_closes_at   VARCHAR(16);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS max_registrations        INTEGER;

-- Venue check-in.
ALTER TABLE participants ADD COLUMN IF NOT EXISTS checked_in_at TIMESTAMPTZ;
ALTER TABLE participants ADD COLUMN IF NOT EXISTS checked_in_by TEXT;

-- Email verification for self sign-up (existing accounts count as verified).
ALTER TABLE admin_users ADD COLUMN IF NOT EXISTS email_verified       BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE admin_users ADD COLUMN IF NOT EXISTS verification_token   VARCHAR(80);
ALTER TABLE admin_users ADD COLUMN IF NOT EXISTS verification_sent_at TIMESTAMPTZ;

-- Who changed what (written in the same request as the change).
CREATE TABLE IF NOT EXISTS admin_audit_log (
    id         BIGSERIAL PRIMARY KEY,
    program_id TEXT,
    username   TEXT,
    action     VARCHAR(160) NOT NULL,
    target     VARCHAR(160),
    method     VARCHAR(10),
    path       VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_admin_audit_program_time ON admin_audit_log(program_id, created_at DESC);
