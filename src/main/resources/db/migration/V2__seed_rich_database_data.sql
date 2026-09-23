-- =============================================================================
-- V2__seed_rich_database_data.sql
-- Rich Database Seed Script for MoveInSync Smart Employee Cab Pooling System
-- Seeds corporate offices, shifts, fleet cabs, employees, and ride bookings.
-- =============================================================================

-- 1. Insert Corporate Office Hubs
INSERT INTO offices (id, name, address, latitude, longitude, created_at)
VALUES 
  (1, 'MoveInSync Tech Park (Bellandur HQ)', 'Outer Ring Road, Bellandur, Bengaluru, Karnataka 560103', 12.9279, 77.6771, CURRENT_TIMESTAMP),
  (2, 'Electronic City Campus', 'Hosur Rd, Phase 1, Electronic City, Bengaluru, Karnataka 560100', 12.8452, 77.6602, CURRENT_TIMESTAMP),
  (3, 'Whitefield SEZ Hub', 'ITPL Main Rd, KIADB Export Promotion Area, Whitefield, Bengaluru 560066', 12.9866, 77.7381, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

SELECT setval('offices_id_seq', COALESCE((SELECT MAX(id) FROM offices), 1));

-- 2. Insert Shift Schedules
INSERT INTO shifts (id, office_id, name, start_time, end_time, shift_type, cutoff_minutes, created_at)
VALUES
  (1, 1, 'Morning Inbound (09:00 - 18:00)', '09:00:00', '18:00:00', 'INBOUND', 60, CURRENT_TIMESTAMP),
  (2, 1, 'Evening Outbound (18:00 - 03:00)', '18:00:00', '03:00:00', 'OUTBOUND', 60, CURRENT_TIMESTAMP),
  (3, 1, 'Night Outbound (21:30 - 06:00)', '21:30:00', '06:00:00', 'OUTBOUND', 90, CURRENT_TIMESTAMP),
  (4, 1, 'Late Night Inbound (23:00 - 08:00)', '23:00:00', '08:00:00', 'INBOUND', 90, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

SELECT setval('shifts_id_seq', COALESCE((SELECT MAX(id) FROM shifts), 1));

-- 3. Insert Commercial Fleet Cabs
INSERT INTO cabs (license_plate, model, capacity, driver_name, driver_phone, is_active, created_at, updated_at)
VALUES
  ('KA-01-AB-1001', 'Toyota Innova Crysta', 6, 'Manjunath Gowda', '+91 98450 11223', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('KA-03-CD-2002', 'Maruti Suzuki Ertiga', 4, 'Suresh Babu', '+91 98450 33445', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('KA-05-EF-3003', 'Mahindra Marazzo', 4, 'Ganesh Hegde', '+91 98450 55667', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('KA-01-GH-4004', 'Toyota Rumion', 4, 'Ravi Shankar', '+91 98450 77889', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('KA-04-IJ-5005', 'Honda City', 3, 'Vijay Bhaskar', '+91 98450 99001', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (license_plate) DO NOTHING;

SELECT setval('cabs_id_seq', COALESCE((SELECT MAX(id) FROM cabs), 1));

-- 4. Insert Employees & Admin Dispatcher
INSERT INTO employees (email, password_hash, name, role, gender, phone_number, home_address, home_latitude, home_longitude, created_at, updated_at)
VALUES
  ('admin@moveinsync.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Bm1n3Q8Jq0F5i', 'Chief Dispatcher Rajesh', 'ROLE_ADMIN', 'MALE', '+91 99001 12233', 'MG Road Corporate Center, Bengaluru', 12.9716, 77.5946, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('pooja.sharma@moveinsync.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Bm1n3Q8Jq0F5i', 'Pooja Sharma', 'ROLE_EMPLOYEE', 'FEMALE', '+91 98765 43201', '100ft Road, Indiranagar, Bengaluru', 12.9719, 77.6412, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('arun.kumar@moveinsync.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Bm1n3Q8Jq0F5i', 'Arun Kumar', 'ROLE_EMPLOYEE', 'MALE', '+91 98765 43202', 'HSR Layout Sector 1, Bengaluru', 12.9121, 77.6446, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('sneha.reddy@moveinsync.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Bm1n3Q8Jq0F5i', 'Sneha Reddy', 'ROLE_EMPLOYEE', 'FEMALE', '+91 98765 43203', 'Koramangala 4th Block, Bengaluru', 12.9352, 77.6245, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('vikas.mehta@moveinsync.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Bm1n3Q8Jq0F5i', 'Vikas Mehta', 'ROLE_EMPLOYEE', 'MALE', '+91 98765 43204', 'Sarjapur Main Road, Bengaluru', 12.9237, 77.6833, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('ananya.iyer@moveinsync.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Bm1n3Q8Jq0F5i', 'Ananya Iyer', 'ROLE_EMPLOYEE', 'FEMALE', '+91 98765 43205', 'BTM Layout 2nd Stage, Bengaluru', 12.9166, 77.6101, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('rohit.verma@moveinsync.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Bm1n3Q8Jq0F5i', 'Rohit Verma', 'ROLE_EMPLOYEE', 'MALE', '+91 98765 43206', 'Marathahalli Bridge, Bengaluru', 12.9569, 77.7011, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

SELECT setval('employees_id_seq', COALESCE((SELECT MAX(id) FROM employees), 1));
