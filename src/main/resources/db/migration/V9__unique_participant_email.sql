ALTER TABLE participants
    ADD CONSTRAINT uq_participants_email UNIQUE (email);
