-- Google Sheets backup: the program's participants mirrored to a sheet the admin shared with ProgramHub.
ALTER TABLE programs ADD COLUMN IF NOT EXISTS sheet_id         VARCHAR(200);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS sheet_tab        VARCHAR(100);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS sheet_title      VARCHAR(300);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS sheet_synced_at  TIMESTAMPTZ;
ALTER TABLE programs ADD COLUMN IF NOT EXISTS sheet_error      TEXT;
