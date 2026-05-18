-- V5: Seed shuttle_config — skips if already populated

INSERT INTO shuttle_config (config_label, conference_day, direction, window_start, window_end, interval_mins, max_vehicles, seats_per_vehicle)
SELECT * FROM (VALUES
    ('Thu Jun 11 - Hotel to Church', 'thursday'::conference_day, 'to_church'::direction, '07:30', '09:00', 15, 3, 15),
    ('Thu Jun 11 - Church to Hotel', 'thursday'::conference_day, 'to_hotel'::direction,  '20:00', '21:30', 15, 3, 15),
    ('Fri Jun 12 - Hotel to Church', 'friday'::conference_day,   'to_church'::direction, '07:30', '09:00', 15, 3, 15),
    ('Fri Jun 12 - Church to Hotel', 'friday'::conference_day,   'to_hotel'::direction,  '20:00', '21:30', 15, 3, 15),
    ('Sat Jun 13 - Hotel to Church', 'saturday'::conference_day, 'to_church'::direction, '07:30', '09:00', 15, 3, 15),
    ('Sat Jun 13 - Church to Hotel', 'saturday'::conference_day, 'to_hotel'::direction,  '20:00', '21:30', 15, 3, 15),
    ('Sun Jun 14 - Hotel to Church', 'sunday'::conference_day,   'to_church'::direction, '07:30', '09:00', 15, 2, 15),
    ('Sun Jun 14 - Church to Hotel', 'sunday'::conference_day,   'to_hotel'::direction,  '20:00', '21:30', 15, 2, 15)
) AS v(config_label, conference_day, direction, window_start, window_end, interval_mins, max_vehicles, seats_per_vehicle)
WHERE NOT EXISTS (SELECT 1 FROM shuttle_config LIMIT 1);
