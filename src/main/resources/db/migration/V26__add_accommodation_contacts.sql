ALTER TABLE notification_config
    ADD COLUMN IF NOT EXISTS accommodation_name_1      TEXT,
    ADD COLUMN IF NOT EXISTS accommodation_phone_1     TEXT,
    ADD COLUMN IF NOT EXISTS accommodation_whatsapp_1  TEXT,
    ADD COLUMN IF NOT EXISTS accommodation_name_2      TEXT,
    ADD COLUMN IF NOT EXISTS accommodation_phone_2     TEXT,
    ADD COLUMN IF NOT EXISTS accommodation_whatsapp_2  TEXT;
