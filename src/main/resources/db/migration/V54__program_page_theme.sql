-- Colour set for a program's registration page and header, chosen in Settings → Registration.
-- NULL = automatic (picked from the program name, today's behaviour).
ALTER TABLE programs ADD COLUMN IF NOT EXISTS page_theme VARCHAR(20);
