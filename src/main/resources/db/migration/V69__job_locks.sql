-- Timed jobs (reminders, shuttle runs, flight checks, Google Sheets backup) run on every server copy.
-- When there are two copies, a row here makes sure only one of them runs each round.
CREATE TABLE job_locks (
    name         VARCHAR(100) PRIMARY KEY,
    locked_until TIMESTAMPTZ  NOT NULL,
    locked_by    VARCHAR(200) NOT NULL
);
