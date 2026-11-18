-- Follow-up questions: a question can be shown only when an earlier choice question has one of some answers.
-- show_if_key    = field_key of the question it depends on (NULL = always shown)
-- show_if_values = JSON array of that question's options that make it appear
ALTER TABLE registration_fields ADD COLUMN IF NOT EXISTS show_if_key    TEXT;
ALTER TABLE registration_fields ADD COLUMN IF NOT EXISTS show_if_values TEXT;
