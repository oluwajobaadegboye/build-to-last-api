-- Who created each program (protected from removal / changes by other admins). Exact from now on;
-- existing programs take their earliest admin membership (ties broken by membership id, so it's stable).
ALTER TABLE programs ADD COLUMN IF NOT EXISTS created_by_admin_id INTEGER;

UPDATE programs p
SET created_by_admin_id = first.admin_user_id
FROM (
    SELECT DISTINCT ON (program_id) program_id, admin_user_id
    FROM program_admins
    ORDER BY program_id, created_at ASC, id ASC
) first
WHERE first.program_id = p.id AND p.created_by_admin_id IS NULL;
