-- V8: Seed reference data matching the Supabase production database.
--
-- PRODUCTION: All records already exist — ON CONFLICT DO NOTHING is a no-op for
--   hotels / drivers / vehicles. notification_config upserts with the real templates
--   (same data already in Supabase, so it has no visible effect).
--
-- DEV (fresh DB): Tables are empty after V1–V7, all rows are inserted fresh.
--   Sequences are advanced past the explicit IDs so future inserts don't collide.
--
-- leg4_cutoff_time is stored as "HH:MM" (Hotel.getLeg4CutoffTimeAsLocalTime handles
-- both "HH:MM" and "HH:MM AM" formats for backwards compatibility with existing rows).

-- ── Hotels ──────────────────────────────────────────────────────────────────
INSERT INTO hotels (id, hotel_name, pickup_address,
                    drive_to_church_mins, drive_to_airport_mins,
                    leg4_cutoff_time, shuttle_stop_order)
VALUES
    (1, 'Hampton Inn', '5860 W 73rd St, Indianapolis, IN 46278',
        10, 15, '10:08', 1),
    (2, 'Holiday Inn', '6240 Intech Commons Dr, Indianapolis, IN 46278',
        9, 18, '10:02', 2),
    (3, 'La Quinta',   '2650 Executive Dr, Indianapolis, IN 46241',
        18, 10, '10:12', 3)
ON CONFLICT (id) DO NOTHING;

-- Advance sequence so the next auto-insert gets id=4+
SELECT setval('hotels_id_seq', GREATEST((SELECT MAX(id) FROM hotels), 1));

-- ── Drivers ──────────────────────────────────────────────────────────────────
INSERT INTO drivers (id, name, phone, whatsapp, active_from)
VALUES
    (1, 'Joba A.',  '+16418191032', '+16418191032', '2026-06-11'),
    (2, 'Moses A.', '+13152441715', '+13152441715', '2026-06-11')
ON CONFLICT (id) DO NOTHING;

SELECT setval('drivers_id_seq', GREATEST((SELECT MAX(id) FROM drivers), 1));

-- ── Vehicles ──────────────────────────────────────────────────────────────────
INSERT INTO vehicles (id, label, type, capacity)
VALUES
    (1, 'Bus 1',      'bus', 12),
    (2, 'Mini Van 1', 'van', 7),
    (3, 'Mini Van 2', 'van', 7)
ON CONFLICT (id) DO NOTHING;

SELECT setval('vehicles_id_seq', GREATEST((SELECT MAX(id) FROM vehicles), 1));

-- ── Notification config ───────────────────────────────────────────────────────
-- Upsert: replaces V7's placeholder templates with real content.
-- Uses chr(10) for newlines so the \n in template strings becomes actual line breaks.
INSERT INTO notification_config (
    id, config_key,
    admin_name_1,   admin_phone_1,   admin_whatsapp_1,
    admin_name_2,   admin_phone_2,   admin_whatsapp_2,
    template_registration,
    template_confirmation,
    template_pickup_reminder,
    template_driver_assigned,
    template_delay_minor,
    template_delay_major,
    template_cancellation,
    template_shuttle_reminder,
    template_help_forward,
    reminder_before_mins
)
VALUES (
    1, 'main',
    'Bro Joba A.',   '+16418191032', '+16418191032',
    'Bro. Moses A.', '+13152441715', '+13152441715',

    -- template_registration
    'Hi {{name}}! You''re registered for Built to Last 2026 ' || chr(9989) || chr(10) ||
    'Your code: {{btl_code}}' || chr(10) ||
    'Track your transport: {{status_url}}' || chr(10) ||
    'Reply HELP for assistance.',

    -- template_confirmation
    'Hi {{name}} ({{btl_code}}), flight {{airline}} {{flight_number}} confirmed ' || chr(9992) || chr(65039) || chr(10) ||
    'Airport pickup scheduled: {{pickup_time}}' || chr(10) ||
    'Driver details coming soon.',

    -- template_pickup_reminder
    'Reminder: Your airport pickup is in 1 hour ' || chr(9200) || chr(10) ||
    'Driver {{driver_name}} — {{pickup_location}}' || chr(10) ||
    'Your code: {{btl_code}}',

    -- template_driver_assigned
    'Hi {{name}}, your driver for {{run_type}} is {{driver_name}} ' || chr(128222) || ' {{driver_phone}}' || chr(10) ||
    'Vehicle: {{vehicle_label}} | Departs: {{depart_time}}',

    -- template_delay_minor
    'Hi {{name}}, flight {{airline}} {{flight_number}} is delayed ' || chr(9203) || chr(10) ||
    'New ETA: {{new_eta}}' || chr(10) ||
    'Your pickup has been rescheduled automatically.',

    -- template_delay_major
    'Hi {{name}}, flight {{airline}} {{flight_number}} has a major delay ' || chr(9888) || chr(65039) || chr(10) ||
    'New ETA: {{new_eta}}' || chr(10) ||
    'Please contact a coordinator for updated pickup.',

    -- template_cancellation
    'Hi {{name}} ({{btl_code}}), your flight appears cancelled or changed ' || chr(10060) || chr(10) ||
    'Please contact a coordinator ASAP or reply HELP.',

    -- shuttle_reminder and help_forward — not in original data, keep null
    NULL,
    NULL,

    30  -- reminder_before_mins
)
ON CONFLICT (id) DO UPDATE SET
    config_key              = EXCLUDED.config_key,
    admin_name_1            = EXCLUDED.admin_name_1,
    admin_phone_1           = EXCLUDED.admin_phone_1,
    admin_whatsapp_1        = EXCLUDED.admin_whatsapp_1,
    admin_name_2            = EXCLUDED.admin_name_2,
    admin_phone_2           = EXCLUDED.admin_phone_2,
    admin_whatsapp_2        = EXCLUDED.admin_whatsapp_2,
    template_registration    = EXCLUDED.template_registration,
    template_confirmation    = EXCLUDED.template_confirmation,
    template_pickup_reminder = EXCLUDED.template_pickup_reminder,
    template_driver_assigned = EXCLUDED.template_driver_assigned,
    template_delay_minor     = EXCLUDED.template_delay_minor,
    template_delay_major     = EXCLUDED.template_delay_major,
    template_cancellation    = EXCLUDED.template_cancellation,
    reminder_before_mins     = EXCLUDED.reminder_before_mins;
