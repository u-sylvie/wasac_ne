-- =============================================================================
-- V13 — Rwanda National ID format constraint (16 numeric digits)
-- =============================================================================

ALTER TABLE customers
    DROP CONSTRAINT IF EXISTS chk_customers_national_id_format;

ALTER TABLE customers
    ADD CONSTRAINT chk_customers_national_id_format
        CHECK (national_id ~ '^[0-9]{16}$');

COMMENT ON COLUMN customers.national_id IS
    'Rwanda National ID — exactly 16 digits. UNIQUE. Primary customer identifier for billing.';
