-- =============================================================================
-- V9 — Seed sample utility billing data for testing
-- =============================================================================

INSERT INTO users (first_name, last_name, username, email, phone, password, role, created_at, updated_at, created_by, updated_by, deleted, status)
VALUES
    ('Jean', 'Operator', 'operator', 'operator@wasac.rw', '+250788111111',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'OPERATOR', NOW(), NOW(), 'system', 'system', FALSE, 'ACTIVE'),
    ('Alice', 'Finance', 'finance', 'finance@wasac.rw', '+250788222222',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'FINANCE', NOW(), NOW(), 'system', 'system', FALSE, 'ACTIVE'),
    ('Eric', 'Customer', 'eric', 'customer@wasac.rw', '+250788333333',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'CUSTOMER', NOW(), NOW(), 'system', 'system', FALSE, 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

INSERT INTO customers (full_name, national_id, email, phone, address, user_id, created_at, updated_at, deleted, status)
SELECT 'Eric Customer', '1199880011223344', 'customer@wasac.rw', '+250788333333', 'Kigali, Gasabo',
       u.id, NOW(), NOW(), FALSE, 'ACTIVE'
FROM users u
WHERE u.email = 'customer@wasac.rw'
ON CONFLICT (national_id) DO NOTHING;

INSERT INTO tariff_configs (name, meter_type, tariff_type, rate_per_unit, fixed_service_charge, vat_percentage, penalty_percentage, version, effective_from, active, created_at, updated_at, deleted, status)
VALUES
    ('Water Flat Tariff v1', 'WATER', 'FLAT', 350.0000, 1500.00, 18.00, 5.00, 1, CURRENT_DATE, TRUE, NOW(), NOW(), FALSE, 'ACTIVE'),
    ('Electricity Flat Tariff v1', 'ELECTRICITY', 'FLAT', 120.0000, 2000.00, 18.00, 5.00, 1, CURRENT_DATE, TRUE, NOW(), NOW(), FALSE, 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO meters (customer_id, meter_number, meter_type, installation_date, created_at, updated_at, deleted, status)
SELECT c.id, 'WTR-0001', 'WATER', CURRENT_DATE - INTERVAL '365 days', NOW(), NOW(), FALSE, 'ACTIVE'
FROM customers c
WHERE c.national_id = '1199880011223344'
ON CONFLICT (meter_number) DO NOTHING;

INSERT INTO meters (customer_id, meter_number, meter_type, installation_date, created_at, updated_at, deleted, status)
SELECT c.id, 'ELC-0001', 'ELECTRICITY', CURRENT_DATE - INTERVAL '300 days', NOW(), NOW(), FALSE, 'ACTIVE'
FROM customers c
WHERE c.national_id = '1199880011223344'
ON CONFLICT (meter_number) DO NOTHING;
