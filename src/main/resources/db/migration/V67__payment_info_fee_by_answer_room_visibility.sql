-- How to pay: instructions (e.g. Zelle details) and an optional QR code, shown after registering,
-- in the confirmation email and on the attendee's page.
ALTER TABLE programs ADD COLUMN IF NOT EXISTS payment_instructions TEXT;

CREATE TABLE IF NOT EXISTS program_payment_qr (
    program_id   VARCHAR(100) PRIMARY KEY REFERENCES programs(id) ON DELETE CASCADE,
    content_type VARCHAR(40)  NOT NULL,
    data         BYTEA        NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Fee by answer: one question's options can each have their own fee (e.g. Youth 200, Leader 280).
-- fee_by_option is a JSON object {"option": amount}; options without an amount pay the normal fee.
ALTER TABLE programs ADD COLUMN IF NOT EXISTS fee_question_key VARCHAR(100);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS fee_by_option TEXT;

-- When attendees see their room: always (null), after check-in, from a date/time, or whichever comes first.
ALTER TABLE programs ADD COLUMN IF NOT EXISTS room_visibility VARCHAR(20);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS room_visible_from VARCHAR(16);   -- "YYYY-MM-DDTHH:mm", program time
