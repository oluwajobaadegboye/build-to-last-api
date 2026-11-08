-- Souvenirs a program gives out (T-shirt, cap, …). Items with options (e.g. sizes) can be asked at registration.
CREATE TABLE IF NOT EXISTS souvenir_items (
    id                  SERIAL PRIMARY KEY,
    program_id          TEXT NOT NULL,
    name                VARCHAR(120) NOT NULL,
    options             TEXT,                          -- JSON array, e.g. ["S","M","L"]; null = one size
    ask_at_registration BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    active              BOOLEAN NOT NULL DEFAULT TRUE, -- removed items are hidden, records kept
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_souvenir_items_program ON souvenir_items(program_id);

-- Per participant and item: the option they chose and whether they've received it.
CREATE TABLE IF NOT EXISTS participant_souvenirs (
    id             SERIAL PRIMARY KEY,
    participant_id INTEGER NOT NULL REFERENCES participants(id) ON DELETE CASCADE,
    item_id        INTEGER NOT NULL REFERENCES souvenir_items(id) ON DELETE CASCADE,
    program_id     TEXT,
    choice         VARCHAR(120),
    received       BOOLEAN NOT NULL DEFAULT FALSE,
    received_at    TIMESTAMPTZ,
    received_by    TEXT,
    CONSTRAINT uq_participant_souvenir UNIQUE (participant_id, item_id)
);
CREATE INDEX IF NOT EXISTS idx_participant_souvenirs_program ON participant_souvenirs(program_id);
