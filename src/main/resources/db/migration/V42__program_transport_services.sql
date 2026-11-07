-- Per-program transport services and lodging type.
-- Defaults keep every existing program exactly as it is today (both services on, "hotel").
ALTER TABLE programs ADD COLUMN IF NOT EXISTS daily_shuttle_enabled   BOOLEAN     NOT NULL DEFAULT TRUE;
ALTER TABLE programs ADD COLUMN IF NOT EXISTS airport_pickups_enabled BOOLEAN     NOT NULL DEFAULT TRUE;
ALTER TABLE programs ADD COLUMN IF NOT EXISTS lodging_type            VARCHAR(20) NOT NULL DEFAULT 'hotel';
