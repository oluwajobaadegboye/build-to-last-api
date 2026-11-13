-- One-time links for "Forgot password?" on the admin sign-in page.
-- Only a SHA-256 hash of each token is stored, so a copy of this table can't be used to reset accounts.
CREATE TABLE IF NOT EXISTS admin_password_resets (
    id            SERIAL PRIMARY KEY,
    admin_user_id INTEGER     NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_admin_password_resets_user ON admin_password_resets(admin_user_id);
