-- =============================================================================
-- V1__init_schema.sql
-- Initial Database Schema for Smart Employee Cab Pooling System
-- =============================================================================

-- 1. Offices table (Dispatch Hubs / Corporate Offices)
CREATE TABLE offices (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Employees table
CREATE TABLE employees (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(150) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('ROLE_EMPLOYEE', 'ROLE_ADMIN')),
    gender VARCHAR(20) NOT NULL CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    phone_number VARCHAR(25) NOT NULL,
    home_address VARCHAR(255) NOT NULL,
    home_latitude DOUBLE PRECISION NOT NULL,
    home_longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Cabs table (Fleet Vehicles)
CREATE TABLE cabs (
    id BIGSERIAL PRIMARY KEY,
    license_plate VARCHAR(30) NOT NULL UNIQUE,
    model VARCHAR(100) NOT NULL,
    capacity INT NOT NULL CHECK (capacity > 0),
    driver_name VARCHAR(150) NOT NULL,
    driver_phone VARCHAR(25) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Shifts table
CREATE TABLE shifts (
    id BIGSERIAL PRIMARY KEY,
    office_id BIGINT NOT NULL REFERENCES offices(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    shift_type VARCHAR(20) NOT NULL CHECK (shift_type IN ('INBOUND', 'OUTBOUND')),
    cutoff_minutes INT NOT NULL DEFAULT 120 CHECK (cutoff_minutes >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. Bookings table (Idempotent per employee, shift, and date)
CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    shift_id BIGINT NOT NULL REFERENCES shifts(id) ON DELETE CASCADE,
    booking_date DATE NOT NULL,
    pickup_latitude DOUBLE PRECISION NOT NULL,
    pickup_longitude DOUBLE PRECISION NOT NULL,
    pickup_address VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_booking_employee_shift_date UNIQUE (employee_id, shift_id, booking_date)
);

-- 6. Cab Assignments table (Cab allocated to a shift on a specific date)
CREATE TABLE cab_assignments (
    id BIGSERIAL PRIMARY KEY,
    cab_id BIGINT NOT NULL REFERENCES cabs(id) ON DELETE RESTRICT,
    shift_id BIGINT NOT NULL REFERENCES shifts(id) ON DELETE RESTRICT,
    assignment_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    total_distance_km DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    total_duration_minutes INT NOT NULL DEFAULT 0,
    has_escort BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. Pickup Stops table (Ordered route sequence for cab assignments)
CREATE TABLE pickup_stops (
    id BIGSERIAL PRIMARY KEY,
    cab_assignment_id BIGINT NOT NULL REFERENCES cab_assignments(id) ON DELETE CASCADE,
    booking_id BIGINT REFERENCES bookings(id) ON DELETE SET NULL,
    stop_order INT NOT NULL CHECK (stop_order >= 1),
    stop_type VARCHAR(20) NOT NULL CHECK (stop_type IN ('PICKUP', 'DROPOFF', 'OFFICE')),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    address VARCHAR(255) NOT NULL,
    planned_time TIMESTAMP WITH TIME ZONE,
    actual_time TIMESTAMP WITH TIME ZONE,
    distance_from_previous_km DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    duration_from_previous_minutes INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cab_assignment_stop_order UNIQUE (cab_assignment_id, stop_order)
);

-- 8. Escort Assignments table (Attached for night safety)
CREATE TABLE escort_assignments (
    id BIGSERIAL PRIMARY KEY,
    cab_assignment_id BIGINT NOT NULL UNIQUE REFERENCES cab_assignments(id) ON DELETE CASCADE,
    escort_name VARCHAR(150) NOT NULL,
    escort_contact VARCHAR(25) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED' CHECK (status IN ('ASSIGNED', 'COMPLETED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance and query optimization
CREATE INDEX idx_employees_email ON employees(email);
CREATE INDEX idx_cabs_license_plate ON cabs(license_plate);
CREATE INDEX idx_cabs_is_active ON cabs(is_active);
CREATE INDEX idx_shifts_office_id ON shifts(office_id);
CREATE INDEX idx_bookings_employee_date ON bookings(employee_id, booking_date);
CREATE INDEX idx_bookings_shift_date ON bookings(shift_id, booking_date);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_cab_assignments_shift_date ON cab_assignments(shift_id, assignment_date);
CREATE INDEX idx_cab_assignments_status ON cab_assignments(status);
CREATE INDEX idx_pickup_stops_cab_assignment ON pickup_stops(cab_assignment_id);
CREATE INDEX idx_pickup_stops_booking ON pickup_stops(booking_id);
