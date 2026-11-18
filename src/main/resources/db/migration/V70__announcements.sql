-- Announcements: an email from the organisers to everyone (or a filtered group) in a program, also shown on
-- each recipient's status page. Recipients are kept so the status page (and later the mobile app) shows each
-- person only what was sent to them, and so an interrupted send can carry on without emailing anyone twice.
CREATE TABLE announcements (
    id              SERIAL PRIMARY KEY,
    program_id      VARCHAR(255) NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    subject         VARCHAR(200) NOT NULL,
    body            TEXT         NOT NULL,
    audience        TEXT,                       -- the filter as JSON
    audience_label  VARCHAR(500),               -- e.g. "Everyone registered · Unpaid"
    show_on_status  BOOLEAN      NOT NULL DEFAULT TRUE,
    status          VARCHAR(20)  NOT NULL,      -- sending | sent
    recipients      INTEGER      NOT NULL DEFAULT 0,
    sent_count      INTEGER      NOT NULL DEFAULT 0,
    failed_count    INTEGER      NOT NULL DEFAULT 0,
    sent_by         VARCHAR(255),
    request_id      VARCHAR(64),                -- the composer's id for this send: a double click sends once
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ
);
CREATE INDEX announcements_program_idx ON announcements (program_id, created_at DESC);
CREATE UNIQUE INDEX announcements_request_uidx ON announcements (program_id, request_id) WHERE request_id IS NOT NULL;

CREATE TABLE announcement_recipients (
    announcement_id INTEGER NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    participant_id  INTEGER NOT NULL REFERENCES participants(id) ON DELETE CASCADE,
    delivered       BOOLEAN,                    -- null = not tried yet, true = sent, false = failed
    PRIMARY KEY (announcement_id, participant_id)
);
CREATE INDEX announcement_recipients_participant_idx ON announcement_recipients (participant_id);
