-- Standalone accommodation contacts table (replaces notification_config 2-slot approach)
CREATE TABLE accommodation_contacts (
  id         SERIAL PRIMARY KEY,
  program_id TEXT NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
  name       TEXT NOT NULL,
  phone      TEXT,
  whatsapp   TEXT,
  sort_order SMALLINT DEFAULT 0
);
CREATE INDEX idx_accom_contacts_program ON accommodation_contacts(program_id);

-- Migrate existing 2-slot contacts from notification_config
INSERT INTO accommodation_contacts (program_id, name, phone, whatsapp, sort_order)
  SELECT p.id, nc.accommodation_name_1, nc.accommodation_phone_1, nc.accommodation_whatsapp_1, 0
  FROM programs p JOIN notification_config nc ON nc.program_id = p.id
  WHERE nc.accommodation_name_1 IS NOT NULL AND nc.accommodation_name_1 <> '';

INSERT INTO accommodation_contacts (program_id, name, phone, whatsapp, sort_order)
  SELECT p.id, nc.accommodation_name_2, nc.accommodation_phone_2, nc.accommodation_whatsapp_2, 1
  FROM programs p JOIN notification_config nc ON nc.program_id = p.id
  WHERE nc.accommodation_name_2 IS NOT NULL AND nc.accommodation_name_2 <> '';

-- Room assignments (hotel_id optional FK to transport hotels table)
CREATE TABLE room_assignments (
  id         SERIAL PRIMARY KEY,
  program_id TEXT    NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
  hotel_id   INTEGER REFERENCES hotels(id) ON DELETE SET NULL,
  hotel_name TEXT    NOT NULL,
  room_label TEXT    NOT NULL,
  room_type  TEXT    NOT NULL DEFAULT '2-person',
  gender     TEXT,
  notes      TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_room_assignments_program ON room_assignments(program_id);

CREATE TABLE room_occupants (
  id             SERIAL PRIMARY KEY,
  room_id        INTEGER  NOT NULL REFERENCES room_assignments(id) ON DELETE CASCADE,
  slot           SMALLINT NOT NULL CHECK (slot BETWEEN 0 AND 3),
  participant_id INTEGER  REFERENCES participants(id) ON DELETE SET NULL,
  name           TEXT     NOT NULL,
  email          TEXT,
  phone          TEXT,
  UNIQUE(room_id, slot)
);

ALTER TABLE programs ADD COLUMN IF NOT EXISTS roommate_visible BOOLEAN DEFAULT true;
