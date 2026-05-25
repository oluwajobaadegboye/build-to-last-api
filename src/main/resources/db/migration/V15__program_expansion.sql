ALTER TABLE programs
  ADD COLUMN IF NOT EXISTS city                   TEXT,
  ADD COLUMN IF NOT EXISTS state                  TEXT,
  ADD COLUMN IF NOT EXISTS logo_url               TEXT,
  ADD COLUMN IF NOT EXISTS hotel_selection_enabled BOOLEAN NOT NULL DEFAULT TRUE;
