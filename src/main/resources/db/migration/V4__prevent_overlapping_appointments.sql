
-- Enable GiST support for equality comparisons on integer IDs.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Prevent overlapping active appointments for the same veterinarian.
-- Adjacent appointments are allowed: [10:00, 10:30) and [10:30, 11:00).
ALTER TABLE appointments
    ADD CONSTRAINT appointments_no_overlapping_active_vet
    EXCLUDE USING gist (
    vet_profile_id WITH =,
    tstzrange(scheduled_start, scheduled_end, '[)') WITH &&
)
WHERE (status IN ('PENDING', 'CONFIRMED'));
