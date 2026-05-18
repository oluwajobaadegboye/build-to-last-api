-- V7: Seed notification_config with default templates

INSERT INTO notification_config (
    config_key,
    template_registration, template_confirmation, template_pickup_reminder,
    template_driver_assigned, template_delay_minor, template_delay_major,
    template_cancellation, template_shuttle_reminder, template_help_forward,
    reminder_before_mins
)
SELECT
    'main',
    'Hi {{name}}, you are registered for BTL 2026! Your code: {{btl_code}}. Track your transport: {{link}}',
    'Hi {{name}} ({{btl_code}}), your pickup is confirmed for {{time}}. Driver: {{driver}}, Vehicle: {{vehicle}}.',
    'Reminder: your pickup is in {{mins}} mins from {{location}}.',
    'Your driver {{driver}} has been assigned. Vehicle: {{vehicle}}. Pickup: {{time}} from {{location}}.',
    'Hi {{name}}, flight {{flight}} is delayed {{delay_mins}} mins. New pickup time: {{time}}. No action needed.',
    'Hi {{name}}, flight {{flight}} has a major delay. A coordinator will contact you shortly.',
    'Hi {{name}}, flight {{flight}} was cancelled. Update your flight at {{link}} — your code {{btl_code}} stays the same.',
    'Shuttle reminder: departs in {{mins}} mins from {{location}}. Driver: {{driver}}.',
    'BTL-{{btl_code}} {{name}}: "{{message}}" — Context: {{context}}',
    30
WHERE NOT EXISTS (SELECT 1 FROM notification_config WHERE config_key = 'main');
