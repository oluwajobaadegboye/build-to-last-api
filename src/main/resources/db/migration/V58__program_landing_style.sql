-- Design of the registration landing page, chosen in Settings → Registration.
-- NULL = the default design ("centered").
ALTER TABLE programs ADD COLUMN IF NOT EXISTS landing_style VARCHAR(20);
