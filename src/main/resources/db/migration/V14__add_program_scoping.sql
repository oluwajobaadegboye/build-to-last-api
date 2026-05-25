-- Program scoping: add program_id to all core transport tables

ALTER TABLE participants ADD COLUMN IF NOT EXISTS program_id TEXT REFERENCES programs(id);
ALTER TABLE runs         ADD COLUMN IF NOT EXISTS program_id TEXT REFERENCES programs(id);
ALTER TABLE hotels       ADD COLUMN IF NOT EXISTS program_id TEXT REFERENCES programs(id);
ALTER TABLE drivers      ADD COLUMN IF NOT EXISTS program_id TEXT REFERENCES programs(id);
ALTER TABLE vehicles     ADD COLUMN IF NOT EXISTS program_id TEXT REFERENCES programs(id);

-- Allow hotels to carry the short ID from the program JSONB blob (e.g. "h1") for upsert
ALTER TABLE hotels ADD COLUMN IF NOT EXISTS external_ref TEXT;

-- Per-program email uniqueness (replaces global unique constraint from V9)
ALTER TABLE participants DROP CONSTRAINT IF EXISTS participants_email_key;
ALTER TABLE participants ADD CONSTRAINT participants_email_program_uniq
    UNIQUE (email, program_id);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_participants_program ON participants(program_id);
CREATE INDEX IF NOT EXISTS idx_runs_program         ON runs(program_id);
CREATE INDEX IF NOT EXISTS idx_hotels_program       ON hotels(program_id);
CREATE INDEX IF NOT EXISTS idx_drivers_program      ON drivers(program_id);
CREATE INDEX IF NOT EXISTS idx_vehicles_program     ON vehicles(program_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_hotels_program_ref
    ON hotels(program_id, external_ref)
    WHERE external_ref IS NOT NULL;

-- Admin users table (replaces YAML-only credentials for program-scoped admins)
CREATE TABLE IF NOT EXISTS admin_users (
    id            SERIAL PRIMARY KEY,
    program_id    TEXT REFERENCES programs(id),
    username      TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    display_name  TEXT,
    created_at    TIMESTAMPTZ DEFAULT now(),
    UNIQUE(program_id, username)
);

-- Program-scoped notification / coordinator contacts
ALTER TABLE notification_config ADD COLUMN IF NOT EXISTS program_id TEXT REFERENCES programs(id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_notification_config_program
    ON notification_config(program_id)
    WHERE program_id IS NOT NULL;
