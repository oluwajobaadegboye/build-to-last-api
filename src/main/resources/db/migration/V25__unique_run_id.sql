-- Reassign new run_ids to any duplicate rows (keep the lowest-id copy, rename the rest)
WITH dupes AS (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY run_id ORDER BY id) AS rn
        FROM runs
    ) ranked
    WHERE rn > 1
)
UPDATE runs
SET run_id = 'RUN-' || UPPER(SUBSTRING(MD5(id::text || NOW()::text), 1, 8))
WHERE id IN (SELECT id FROM dupes);

ALTER TABLE runs ADD CONSTRAINT runs_run_id_unique UNIQUE (run_id);
