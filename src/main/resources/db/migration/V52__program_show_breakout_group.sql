-- Whether attendees see their breakout group on their status page. On by default (today's behaviour).
ALTER TABLE programs ADD COLUMN IF NOT EXISTS show_breakout_group BOOLEAN NOT NULL DEFAULT TRUE;
