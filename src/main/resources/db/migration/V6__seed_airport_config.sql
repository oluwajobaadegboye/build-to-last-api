-- V6: Seed airport_config main row

INSERT INTO airport_config (
    config_key, grouping_window_mins, polling_end_datetime,
    minor_delay_threshold_hrs, major_delay_threshold_hrs,
    airport_code, leg4_default_cutoff_time, polling_start_date, polling_interval_mins
)
SELECT
    'main', 45, '2026-06-12T12:00:00',
    2.0, 4.0,
    'IND', '10:30', '2026-06-11', 30
WHERE NOT EXISTS (SELECT 1 FROM airport_config WHERE config_key = 'main');
