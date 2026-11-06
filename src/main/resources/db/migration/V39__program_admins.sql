-- Multi-program admin accounts: one admin user can belong to many programs.
-- admin_users.program_id is kept as the user's "primary" program for backward compatibility.

CREATE TABLE IF NOT EXISTS program_admins (
    id            SERIAL PRIMARY KEY,
    admin_user_id INTEGER NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    program_id    TEXT    NOT NULL REFERENCES programs(id)    ON DELETE CASCADE,
    role          VARCHAR(50) NOT NULL DEFAULT 'FULL',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (admin_user_id, program_id)
);

CREATE INDEX IF NOT EXISTS idx_program_admins_program ON program_admins(program_id);

-- Backfill: every existing program-scoped admin becomes a member of their program with their current role.
INSERT INTO program_admins (admin_user_id, program_id, role, created_at)
SELECT id, program_id, COALESCE(role, 'FULL'), COALESCE(created_at, now())
FROM admin_users
WHERE program_id IS NOT NULL
ON CONFLICT (admin_user_id, program_id) DO NOTHING;

-- Usernames must be globally unique now that login is account-based (not program-based).
-- Only enforce at the DB level if existing data allows it; the application also checks.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT lower(username) FROM admin_users GROUP BY lower(username) HAVING count(*) > 1
    ) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uq_admin_users_username_lower ON admin_users (lower(username));
    ELSE
        RAISE NOTICE 'Duplicate admin usernames exist; skipping global unique index';
    END IF;
END $$;
