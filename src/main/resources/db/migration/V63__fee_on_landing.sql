-- Show the registration fee on the public registration page (off unless the program turns it on),
-- under a label the admin can change (NULL = "Registration fee").
ALTER TABLE programs ADD COLUMN IF NOT EXISTS show_fee_on_landing BOOLEAN DEFAULT FALSE;  -- NULL = off
ALTER TABLE programs ADD COLUMN IF NOT EXISTS fee_label VARCHAR(40);
