-- Speed-ups for lookups that run on every registration / status page / list view, and removal of
-- duplicate indexes (each one slows every insert and update for no benefit).

-- Rooms: "which room is this person in" (by linked participant, or by email before they register).
CREATE INDEX IF NOT EXISTS idx_room_occupants_participant ON room_occupants (participant_id);
CREATE INDEX IF NOT EXISTS idx_room_occupants_email_lower ON room_occupants (LOWER(email));

-- Registration / waitlist checks look people up by email, ignoring case, within a program.
CREATE INDEX IF NOT EXISTS idx_participants_program_email_lower ON participants (program_id, LOWER(email));

-- Payments ledger export (program, oldest first).
CREATE INDEX IF NOT EXISTS idx_participant_payments_program ON participant_payments (program_id, recorded_at);

-- Duplicates: the same unique rule existed twice (V14 and V24), and reg_code is already indexed by its UNIQUE key.
ALTER TABLE participants DROP CONSTRAINT IF EXISTS participants_email_program_uniq;
DROP INDEX IF EXISTS idx_participants_reg_code;
