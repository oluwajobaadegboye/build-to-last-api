-- V3: Performance indexes

CREATE INDEX IF NOT EXISTS idx_participants_status ON participants(status);
CREATE INDEX IF NOT EXISTS idx_participants_needs_attention ON participants(needs_attention);
CREATE INDEX IF NOT EXISTS idx_participants_hotel_id ON participants(hotel_id);
CREATE INDEX IF NOT EXISTS idx_participants_btl_code ON participants(btl_code);
CREATE INDEX IF NOT EXISTS idx_flights_participant_id ON flights(participant_id);
CREATE INDEX IF NOT EXISTS idx_flights_polling ON flights(polling_active);
CREATE INDEX IF NOT EXISTS idx_runs_conference_date ON runs(conference_date);
CREATE INDEX IF NOT EXISTS idx_runs_status ON runs(status);
CREATE INDEX IF NOT EXISTS idx_run_participants_run ON run_participants(run_id);
CREATE INDEX IF NOT EXISTS idx_run_participants_participant ON run_participants(participant_id);
