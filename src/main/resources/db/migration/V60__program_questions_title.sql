-- Heading of the registration step that holds the program's custom questions, set in
-- Settings → Registration questions. NULL = the default heading ("Additional Info").
ALTER TABLE programs ADD COLUMN IF NOT EXISTS questions_title VARCHAR(60);
