-- The "Paid participants" permission is merged into "Registrations" (which also shows payment status).
UPDATE program_admins SET role = REPLACE(role, 'PAID', 'REGISTRATIONS') WHERE role LIKE '%PAID%';
UPDATE admin_users    SET role = REPLACE(role, 'PAID', 'REGISTRATIONS') WHERE role LIKE '%PAID%';
-- Collapse a duplicate if someone had both.
UPDATE program_admins SET role = REPLACE(role, 'REGISTRATIONS,REGISTRATIONS', 'REGISTRATIONS') WHERE role LIKE '%REGISTRATIONS,REGISTRATIONS%';
UPDATE admin_users    SET role = REPLACE(role, 'REGISTRATIONS,REGISTRATIONS', 'REGISTRATIONS') WHERE role LIKE '%REGISTRATIONS,REGISTRATIONS%';
