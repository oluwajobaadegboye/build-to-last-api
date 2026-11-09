-- Admin permissions: program_admins.role now holds a comma-separated permission set
-- (FULL, REGISTRATIONS, PAID, PAYMENTS, ACCOMMODATION). Existing values stay valid.
ALTER TABLE program_admins ALTER COLUMN role TYPE VARCHAR(200);
ALTER TABLE admin_users    ALTER COLUMN role TYPE VARCHAR(200);

-- Optional registration fee per program (payments happen outside the app).
ALTER TABLE programs ADD COLUMN IF NOT EXISTS registration_fee NUMERIC(10,2);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS fee_currency     VARCHAR(3) NOT NULL DEFAULT 'USD';

-- Payment status per participant: unpaid | partial | paid.
ALTER TABLE participants ADD COLUMN IF NOT EXISTS payment_status VARCHAR(10) NOT NULL DEFAULT 'unpaid';

-- Individual payments (supports part payments / instalments).
CREATE TABLE IF NOT EXISTS participant_payments (
    id             SERIAL PRIMARY KEY,
    participant_id INTEGER NOT NULL REFERENCES participants(id) ON DELETE CASCADE,
    program_id     TEXT,
    amount         NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    note           TEXT,
    recorded_by    TEXT,
    recorded_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_participant_payments_participant ON participant_payments(participant_id);
