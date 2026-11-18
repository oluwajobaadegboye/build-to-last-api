-- The registration code is no longer tied to one event: btl_code -> reg_code.
ALTER TABLE participants RENAME COLUMN btl_code TO reg_code;
ALTER INDEX IF EXISTS idx_participants_btl_code RENAME TO idx_participants_reg_code;
ALTER INDEX IF EXISTS participants_btl_code_key RENAME TO participants_reg_code_key;
ALTER SEQUENCE IF EXISTS btl_code_seq RENAME TO reg_code_seq;
