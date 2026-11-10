-- Registration asks for first and last name separately, and (optionally per program) gender.
-- full_name stays and is kept as "First Last", so everything that shows a name is unchanged.
-- Existing participants keep only full_name (no backfill).
ALTER TABLE participants ADD COLUMN IF NOT EXISTS first_name VARCHAR(120);
ALTER TABLE participants ADD COLUMN IF NOT EXISTS last_name  VARCHAR(120);
ALTER TABLE participants ADD COLUMN IF NOT EXISTS gender     VARCHAR(10);

-- On by default: registration asks for gender (required) unless the admin turns it off.
ALTER TABLE programs ADD COLUMN IF NOT EXISTS ask_gender BOOLEAN NOT NULL DEFAULT TRUE;
