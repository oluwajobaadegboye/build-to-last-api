ALTER TABLE notification_config
ADD COLUMN IF NOT EXISTS template_reschedule TEXT DEFAULT
'Hi {{name}}, your flight {{flight}} has been rescheduled. Original arrival: {{old_time}}. New arrival: {{new_time}}. Please check {{link}} for updated shuttle details.';
