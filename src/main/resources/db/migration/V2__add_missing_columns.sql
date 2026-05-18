-- V2: Add columns missing from the original schema

ALTER TABLE participants
    ADD COLUMN IF NOT EXISTS attention_reason TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();

ALTER TABLE flights
    ADD COLUMN IF NOT EXISTS delay_mins INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_polled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS airport_code TEXT DEFAULT 'IND',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();

ALTER TABLE runs
    ADD COLUMN IF NOT EXISTS pickup_location TEXT,
    ADD COLUMN IF NOT EXISTS dropoff_location TEXT,
    ADD COLUMN IF NOT EXISTS manifest_sent BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now(),
    ADD COLUMN IF NOT EXISTS conference_date DATE;

ALTER TABLE airport_config
    ADD COLUMN IF NOT EXISTS airport_code TEXT DEFAULT 'IND',
    ADD COLUMN IF NOT EXISTS leg4_default_cutoff_time TEXT DEFAULT '10:30',
    ADD COLUMN IF NOT EXISTS polling_start_date TEXT DEFAULT '2026-06-11',
    ADD COLUMN IF NOT EXISTS polling_interval_mins INTEGER DEFAULT 30;

ALTER TABLE notification_config
    ADD COLUMN IF NOT EXISTS template_shuttle_reminder TEXT,
    ADD COLUMN IF NOT EXISTS template_help_forward TEXT,
    ADD COLUMN IF NOT EXISTS reminder_before_mins INTEGER DEFAULT 30;
