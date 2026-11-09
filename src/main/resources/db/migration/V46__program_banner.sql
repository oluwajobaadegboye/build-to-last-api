-- Optional banner/poster image per program (shown as the hero on its landing and registration pages).
ALTER TABLE programs ADD COLUMN IF NOT EXISTS banner_url TEXT;
