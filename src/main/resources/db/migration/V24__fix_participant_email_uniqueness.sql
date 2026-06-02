ALTER TABLE participants
    DROP CONSTRAINT IF EXISTS uq_participants_email;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_participants_email_program'
    ) THEN
        ALTER TABLE participants
            ADD CONSTRAINT uq_participants_email_program UNIQUE (email, program_id);
    END IF;
END $$;
