-- =============================================================================
-- V10 — Align all seeded account passwords with admin (Admin@1234)
-- =============================================================================
-- V2 used a placeholder BCrypt hash that does not match Admin@1234.
-- V5 set the correct hash for admin@javat.com — apply the same to other seeds.

UPDATE users
SET    password   = '$2a$12$LFzYDzFjW5/sobDavnnG5ON314MRp5dvkjOfbwVXGROl/Y.S4HMji',
       updated_at = NOW(),
       updated_by = 'system'
WHERE  email IN (
    'operator@wasac.rw',
    'finance@wasac.rw',
    'customer@wasac.rw'
);
