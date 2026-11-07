-- Per-program custom registration questions and participants' answers.

CREATE TABLE IF NOT EXISTS registration_fields (
    id          SERIAL PRIMARY KEY,
    program_id  TEXT NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    field_key   TEXT NOT NULL,              -- stable key answers are stored under
    label       TEXT NOT NULL,
    type        VARCHAR(20) NOT NULL,       -- text | textarea | select | multiselect | checkbox | number | date
    options     TEXT,                       -- JSON array of strings for select / multiselect
    required    BOOLEAN NOT NULL DEFAULT FALSE,
    help_text   TEXT,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE,  -- false = removed from form, answers kept
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (program_id, field_key)
);

CREATE INDEX IF NOT EXISTS idx_registration_fields_program ON registration_fields(program_id, sort_order);

-- JSON object: { "<field_key>": <answer> }. NULL for participants with no custom answers.
ALTER TABLE participants ADD COLUMN IF NOT EXISTS custom_answers TEXT;
