-- Registration asks for a WhatsApp/phone number unless the program turns it off.
ALTER TABLE programs ADD COLUMN IF NOT EXISTS ask_phone BOOLEAN NOT NULL DEFAULT TRUE;
