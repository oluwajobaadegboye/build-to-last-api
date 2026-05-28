-- Remove duplicate ini values, keeping the most recently created program for each ini
DELETE FROM programs
WHERE id NOT IN (
    SELECT DISTINCT ON (ini) id
    FROM programs
    WHERE ini IS NOT NULL
    ORDER BY ini, created_at DESC
);

ALTER TABLE programs
    ADD CONSTRAINT programs_ini_unique UNIQUE (ini);
