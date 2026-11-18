-- Registration questions can be split into several steps ("sections"). Questions with no section
-- belong to the first step, whose heading is programs.questions_title ("Additional Info" when unset).
CREATE TABLE IF NOT EXISTS registration_sections (
    id          SERIAL PRIMARY KEY,
    program_id  TEXT NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    title       VARCHAR(60) NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_registration_sections_program ON registration_sections(program_id, sort_order);

-- Removing a section moves its questions back to the first step.
ALTER TABLE registration_fields ADD COLUMN IF NOT EXISTS section_id INTEGER
    REFERENCES registration_sections(id) ON DELETE SET NULL;

-- Where souvenir size questions are asked: NULL = the first step, 'own' = a "Souvenirs" step of
-- their own, otherwise a registration_sections id.
ALTER TABLE programs ADD COLUMN IF NOT EXISTS souvenir_step VARCHAR(20);
