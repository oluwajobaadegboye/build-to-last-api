-- "Leave from the venue" rule for departure pickups: per program, off by default.
-- btl2026 used it (via the global airport_config cutoff), so keep it on there with the same time.
ALTER TABLE programs ADD COLUMN IF NOT EXISTS venue_departure_rule   BOOLEAN    NOT NULL DEFAULT FALSE;
ALTER TABLE programs ADD COLUMN IF NOT EXISTS venue_departure_cutoff VARCHAR(5);

UPDATE programs p
SET venue_departure_rule = TRUE,
    venue_departure_cutoff = COALESCE((SELECT a.leg4_default_cutoff_time FROM airport_config a WHERE a.config_key = 'main'), '10:30')
WHERE p.id = 'btl2026';
