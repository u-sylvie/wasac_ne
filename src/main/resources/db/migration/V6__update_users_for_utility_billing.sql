-- =============================================================================
-- V6 — Extend users for Utility Billing System requirements
-- =============================================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone VARCHAR(30);

UPDATE users
SET    phone = '+250700000000'
WHERE  phone IS NULL;

ALTER TABLE users
    ALTER COLUMN phone SET NOT NULL;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_role;

ALTER TABLE users
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('ADMIN', 'OPERATOR', 'FINANCE', 'CUSTOMER', 'USER', 'MODERATOR'));

CREATE INDEX IF NOT EXISTS idx_users_phone ON users (phone);
