-- V1: Full schema creation for dev/test environments.
--
-- PRODUCTION (Supabase): This migration is SKIPPED automatically because
--   spring.flyway.baseline-on-migrate=true and baseline-version=1.
--   Flyway marks V1 as already applied without executing it.
--
-- DEV / TEST: Set spring.flyway.baseline-on-migrate=false (see application-local.yml).
--   Flyway runs this migration and creates everything from scratch.
--
-- All statements are idempotent (IF NOT EXISTS) so it is safe to run twice.

-- ── Enum types ──────────────────────────────────────────────────────────────
-- Values are lowercase to match Supabase conventions.
-- Java enums (uppercase) are translated by EnumConverters.java (autoApply converters).

DO $$ BEGIN
    CREATE TYPE participant_status AS ENUM (
        'registered', 'confirmed', 'picked_up', 'active',
        'dropped_off', 'closed', 'needs_attention'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE flight_status_type AS ENUM (
        'unknown', 'scheduled', 'delayed', 'cancelled', 'diverted', 'landed'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE leg4_pickup_from_type AS ENUM ('hotel', 'church');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE run_status_enum AS ENUM (
        'scheduled', 'boarding', 'en_route', 'completed', 'cancelled', 'delayed', 'extra'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE run_type AS ENUM ('airport', 'shuttle');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE direction AS ENUM ('to_church', 'to_hotel', 'to_airport');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE conference_day AS ENUM ('thursday', 'friday', 'saturday', 'sunday');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE vehicle_type AS ENUM ('bus', 'suv', 'van');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ── Tables ──────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS hotels (
    id                    SERIAL PRIMARY KEY,
    hotel_name            TEXT NOT NULL,
    pickup_address        TEXT,
    drive_to_church_mins  INTEGER,
    drive_to_airport_mins INTEGER,
    leg4_cutoff_time      TEXT,                 -- "HH:MM" e.g. "10:30"
    shuttle_stop_order    INTEGER,
    created_at            TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS drivers (
    id               SERIAL PRIMARY KEY,
    name             TEXT NOT NULL,
    phone            TEXT,
    whatsapp         TEXT,
    available_dates  DATE[],
    active_from      DATE,
    created_at       TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vehicles (
    id         SERIAL PRIMARY KEY,
    label      TEXT NOT NULL,
    type       vehicle_type,
    capacity   INTEGER NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS participants (
    id               SERIAL PRIMARY KEY,
    btl_code         TEXT NOT NULL UNIQUE,
    full_name        TEXT NOT NULL,
    email            TEXT,
    phone            TEXT,
    status           participant_status DEFAULT 'registered',
    needs_attention  BOOLEAN DEFAULT false,
    shuttle_opt_in   BOOLEAN DEFAULT true,
    hotel_id         INTEGER REFERENCES hotels(id),
    notes            TEXT,
    attention_reason TEXT,
    created_at       TIMESTAMPTZ DEFAULT now(),
    updated_at       TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS flights (
    id                 SERIAL PRIMARY KEY,
    flight_id          TEXT,
    participant_id     INTEGER NOT NULL REFERENCES participants(id),
    airline            TEXT,
    flight_number      TEXT,
    direction          direction,
    submitted_datetime TIMESTAMPTZ,
    live_eta           TIMESTAMPTZ,
    flight_status      flight_status_type DEFAULT 'unknown',
    polling_active     BOOLEAN DEFAULT false,
    leg4_pickup_from   leg4_pickup_from_type,
    delay_mins         INTEGER DEFAULT 0,
    last_polled_at     TIMESTAMPTZ,
    airport_code       TEXT DEFAULT 'IND',
    created_at         TIMESTAMPTZ DEFAULT now(),
    updated_at         TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS runs (
    id               SERIAL PRIMARY KEY,
    run_id           TEXT NOT NULL,
    run_type         run_type,
    direction        direction,
    conference_day   conference_day,
    conference_date  DATE,
    depart_time      TEXT,                     -- "HH:MM"
    seats_total      INTEGER,
    seats_filled     INTEGER DEFAULT 0,
    status           run_status_enum DEFAULT 'scheduled',
    vehicle_id       INTEGER REFERENCES vehicles(id),
    driver_id        INTEGER REFERENCES drivers(id),
    pickup_location  TEXT,
    dropoff_location TEXT,
    manifest_sent    BOOLEAN DEFAULT false,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ DEFAULT now(),
    updated_at       TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS run_participants (
    run_id         INTEGER NOT NULL REFERENCES runs(id),
    participant_id INTEGER NOT NULL REFERENCES participants(id),
    boarded        BOOLEAN DEFAULT false,
    boarded_at     TIMESTAMPTZ,
    PRIMARY KEY (run_id, participant_id)
);

CREATE TABLE IF NOT EXISTS shuttle_config (
    id                SERIAL PRIMARY KEY,
    config_label      TEXT NOT NULL,
    conference_day    conference_day,
    direction         direction,
    window_start      TEXT,                    -- "HH:MM"
    window_end        TEXT,                    -- "HH:MM"
    interval_mins     INTEGER DEFAULT 15,
    max_vehicles      INTEGER DEFAULT 3,
    seats_per_vehicle INTEGER DEFAULT 15
);

CREATE TABLE IF NOT EXISTS airport_config (
    id                          SERIAL PRIMARY KEY,
    config_key                  TEXT DEFAULT 'main',
    grouping_window_mins        INTEGER DEFAULT 45,
    polling_end_datetime        TEXT,          -- "2026-06-12T12:00:00"
    minor_delay_threshold_hrs   NUMERIC DEFAULT 2.0,
    major_delay_threshold_hrs   NUMERIC DEFAULT 4.0,
    airport_code                TEXT DEFAULT 'IND',
    leg4_default_cutoff_time    TEXT DEFAULT '10:30',
    polling_start_date          TEXT DEFAULT '2026-06-11',
    polling_interval_mins       INTEGER DEFAULT 30
);

CREATE TABLE IF NOT EXISTS notification_config (
    id                        SERIAL PRIMARY KEY,
    config_key                TEXT DEFAULT 'main',
    admin_name_1              TEXT,
    admin_phone_1             TEXT,
    admin_whatsapp_1          TEXT,
    admin_name_2              TEXT,
    admin_phone_2             TEXT,
    admin_whatsapp_2          TEXT,
    template_registration     TEXT,
    template_confirmation     TEXT,
    template_pickup_reminder  TEXT,
    template_driver_assigned  TEXT,
    template_delay_minor      TEXT,
    template_delay_major      TEXT,
    template_cancellation     TEXT,
    template_shuttle_reminder TEXT,
    template_help_forward     TEXT,
    reminder_before_mins      INTEGER DEFAULT 30
);

-- ── BTL code sequence ────────────────────────────────────────────────────────
CREATE SEQUENCE IF NOT EXISTS btl_code_seq
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    CACHE 1;

-- ── Indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_participants_status         ON participants(status);
CREATE INDEX IF NOT EXISTS idx_participants_needs_attention ON participants(needs_attention);
CREATE INDEX IF NOT EXISTS idx_participants_hotel_id       ON participants(hotel_id);
CREATE INDEX IF NOT EXISTS idx_participants_btl_code       ON participants(btl_code);
CREATE INDEX IF NOT EXISTS idx_flights_participant_id      ON flights(participant_id);
CREATE INDEX IF NOT EXISTS idx_flights_polling             ON flights(polling_active);
CREATE INDEX IF NOT EXISTS idx_runs_conference_date        ON runs(conference_date);
CREATE INDEX IF NOT EXISTS idx_runs_status                 ON runs(status);
CREATE INDEX IF NOT EXISTS idx_run_participants_run        ON run_participants(run_id);
CREATE INDEX IF NOT EXISTS idx_run_participants_participant ON run_participants(participant_id);
