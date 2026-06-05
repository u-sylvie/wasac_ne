-- =============================================================================
-- V11 — Ensure seeded tariffs apply to current billing cycles
-- =============================================================================
-- V9 used CURRENT_DATE as effective_from, which can be after the 1st of the
-- billing month and block bill generation for readings in the same month.

UPDATE tariff_configs
SET    effective_from = DATE '2020-01-01',
       updated_at     = NOW(),
       updated_by     = 'system'
WHERE  effective_from > DATE '2020-01-01';
