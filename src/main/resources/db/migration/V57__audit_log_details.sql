-- Make the activity log readable: what kind of change, who/what it was about (by name, kept even
-- after a delete), and a short detail such as "Paid" or "email, phone". Older rows keep NULLs and
-- are described from their stored method + path when read.
ALTER TABLE admin_audit_log ADD COLUMN IF NOT EXISTS category    VARCHAR(30);
ALTER TABLE admin_audit_log ADD COLUMN IF NOT EXISTS target_name VARCHAR(200);
ALTER TABLE admin_audit_log ADD COLUMN IF NOT EXISTS detail      VARCHAR(300);
