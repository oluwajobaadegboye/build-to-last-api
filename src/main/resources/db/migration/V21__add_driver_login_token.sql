ALTER TABLE drivers
  ADD COLUMN IF NOT EXISTS login_token TEXT NOT NULL DEFAULT gen_random_uuid()::text;

CREATE UNIQUE INDEX IF NOT EXISTS uq_drivers_login_token ON drivers(login_token);
