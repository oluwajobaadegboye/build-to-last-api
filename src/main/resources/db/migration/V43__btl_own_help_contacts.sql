-- Help contacts are now per program only (no fallback to the global "main" config).
-- btl2026 relied on that fallback, so give it its own copy of those contacts — only where it has none,
-- so attendees keep seeing the same coordinators. No-op if btl2026 or the "main" row doesn't exist.
UPDATE notification_config nc
SET admin_name_1     = m.admin_name_1,
    admin_phone_1    = m.admin_phone_1,
    admin_whatsapp_1 = m.admin_whatsapp_1,
    admin_name_2     = m.admin_name_2,
    admin_phone_2    = m.admin_phone_2,
    admin_whatsapp_2 = m.admin_whatsapp_2
FROM notification_config m
WHERE m.config_key = 'main'
  AND nc.program_id = 'btl2026'
  AND COALESCE(NULLIF(TRIM(nc.admin_name_1), ''), NULLIF(TRIM(nc.admin_name_2), '')) IS NULL;
