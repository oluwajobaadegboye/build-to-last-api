-- Waitlist: once a program is full, new sign-ups join a waitlist (when the program allows it).
ALTER TABLE programs     ADD COLUMN IF NOT EXISTS waitlist_enabled BOOLEAN;          -- null = off
ALTER TABLE participants ADD COLUMN IF NOT EXISTS waitlisted_at TIMESTAMPTZ;         -- null = has a place

-- Payments ledger: refunds and how the money moved (Zelle, cash, ...).
ALTER TABLE participant_payments ADD COLUMN IF NOT EXISTS kind VARCHAR(10) NOT NULL DEFAULT 'payment'; -- payment | refund
ALTER TABLE participant_payments ADD COLUMN IF NOT EXISTS method VARCHAR(30);

-- Payment emails: receipts (payment_emails_enabled) and reminders are switched separately;
-- an optional automatic reminder goes out N days before the program starts.
ALTER TABLE programs ADD COLUMN IF NOT EXISTS payment_reminders_enabled BOOLEAN;     -- null = off
ALTER TABLE programs ADD COLUMN IF NOT EXISTS auto_reminder_days INTEGER;            -- null = no automatic reminder
ALTER TABLE programs ADD COLUMN IF NOT EXISTS auto_reminder_sent_at TIMESTAMPTZ;

-- Attendees may correct their own details from their status page (null = off).
ALTER TABLE programs ADD COLUMN IF NOT EXISTS attendee_self_edit BOOLEAN;
