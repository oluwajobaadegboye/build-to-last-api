ALTER TABLE notification_config
  ADD COLUMN IF NOT EXISTS template_room_assignment TEXT;

UPDATE notification_config
SET template_room_assignment =
  'Hi {{name}}, your room assignment for BTL 2026 is ready. Hotel: {{hotel}}, Room: {{room}} ({{room_type}}). View your full transport details: {{status_url}}'
WHERE template_room_assignment IS NULL;
