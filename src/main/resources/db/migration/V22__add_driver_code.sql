ALTER TABLE drivers ADD COLUMN driver_code TEXT;

UPDATE drivers
SET driver_code = 'DRV-' || LPAD(CAST(id AS TEXT), 3, '0');

ALTER TABLE drivers ALTER COLUMN driver_code SET NOT NULL;
CREATE UNIQUE INDEX drivers_driver_code_uidx ON drivers(driver_code);
