-- Programs can now run on any day of the week (open platform), not just Thursday–Sunday.
-- Additive only: existing rows and values are unchanged.
ALTER TYPE conference_day ADD VALUE IF NOT EXISTS 'monday';
ALTER TYPE conference_day ADD VALUE IF NOT EXISTS 'tuesday';
ALTER TYPE conference_day ADD VALUE IF NOT EXISTS 'wednesday';
