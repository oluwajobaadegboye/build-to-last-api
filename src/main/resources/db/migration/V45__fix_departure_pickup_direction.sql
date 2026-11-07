-- The "leave from the venue" rule was stored reversed (early flights were marked "church").
-- The rule is now: before the cutoff → picked up at the lodging ('hotel'); at/after → at the venue ('church').
-- Every stored value was computed by the old rule, so swap them to match.
UPDATE flights
SET leg4_pickup_from = CASE leg4_pickup_from
        WHEN 'church'::leg4_pickup_from_type THEN 'hotel'::leg4_pickup_from_type
        ELSE 'church'::leg4_pickup_from_type
    END
WHERE leg4_pickup_from IS NOT NULL;
