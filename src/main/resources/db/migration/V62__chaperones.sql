-- Chaperones: registered attendees who look after a room or a hostel/hotel. Off unless the program turns it on.
ALTER TABLE programs ADD COLUMN IF NOT EXISTS chaperones_enabled BOOLEAN DEFAULT FALSE;  -- NULL = off

-- One chaperone per room.
ALTER TABLE room_assignments ADD COLUMN IF NOT EXISTS chaperone_participant_id INTEGER
    REFERENCES participants(id) ON DELETE SET NULL;

-- Several per hostel/hotel (matched by name, like rooms). gender = 'male' / 'female' to show a
-- chaperone only to attendees of that gender; NULL = everyone in the hostel.
CREATE TABLE IF NOT EXISTS hotel_chaperones (
    id              SERIAL PRIMARY KEY,
    program_id      TEXT NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    hotel_name      TEXT NOT NULL,
    participant_id  INTEGER NOT NULL REFERENCES participants(id) ON DELETE CASCADE,
    gender          VARCHAR(10),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_hotel_chaperones_program ON hotel_chaperones(program_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_hotel_chaperones ON hotel_chaperones(program_id, LOWER(hotel_name), participant_id);
