-- =============================================================================
-- V3__capacity_and_constraints.sql
-- Align fleet capacity with case study (4 or 6 seats) and harden booking uniqueness
-- so cancelled bookings can be reactivated without duplicate-row conflicts.
-- =============================================================================

-- Promote any non-standard cab capacities to valid fleet sizes
UPDATE cabs SET capacity = 4 WHERE capacity NOT IN (4, 6);

ALTER TABLE cabs DROP CONSTRAINT IF EXISTS cabs_capacity_check;
ALTER TABLE cabs ADD CONSTRAINT cabs_capacity_check CHECK (capacity IN (4, 6));

-- Replace blanket unique with partial unique: only one CONFIRMED booking
-- per employee / shift / date. Cancelled history may coexist for audit.
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS uk_booking_employee_shift_date;

CREATE UNIQUE INDEX IF NOT EXISTS uk_booking_confirmed_employee_shift_date
    ON bookings (employee_id, shift_id, booking_date)
    WHERE status = 'CONFIRMED';
