-- =============================================================================
-- V12 — Exam validations, bill statuses, payment methods, user flags
-- =============================================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS date_of_birth DATE;

ALTER TABLE customers
    DROP CONSTRAINT IF EXISTS uq_customers_phone;

ALTER TABLE customers
    ADD CONSTRAINT uq_customers_phone UNIQUE (phone);

-- Bill status migration: PENDING -> UNPAID
UPDATE bills SET bill_status = 'UNPAID' WHERE bill_status = 'PENDING';

ALTER TABLE bills DROP CONSTRAINT IF EXISTS chk_bills_status;

ALTER TABLE bills
    ADD CONSTRAINT chk_bills_status
        CHECK (bill_status IN ('UNPAID', 'APPROVED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED'));

ALTER TABLE bills
    DROP CONSTRAINT IF EXISTS uq_bills_meter_period;

ALTER TABLE bills
    ADD CONSTRAINT uq_bills_meter_period UNIQUE (meter_id, billing_year, billing_month);

ALTER TABLE bills ALTER COLUMN bill_status SET DEFAULT 'UNPAID';

-- Payment method values
UPDATE payments SET payment_method = 'MOMO' WHERE payment_method = 'MOBILE_MONEY';
UPDATE payments SET payment_method = 'BANK' WHERE payment_method = 'BANK_TRANSFER';

-- Meter / entity status: allow DISCONNECTED
ALTER TABLE meters DROP CONSTRAINT IF EXISTS chk_meters_status;
ALTER TABLE meters
    ADD CONSTRAINT chk_meters_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'PENDING', 'DISCONNECTED'));

ALTER TABLE customers DROP CONSTRAINT IF EXISTS chk_customers_status;
ALTER TABLE customers
    ADD CONSTRAINT chk_customers_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'PENDING'));

-- Prevent duplicate bill notifications
CREATE OR REPLACE FUNCTION fn_notify_bill_generated()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_customer customers%ROWTYPE;
    v_message  TEXT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM notifications
        WHERE bill_id = NEW.id AND notification_type = 'BILL_GENERATED'
    ) THEN
        RETURN NEW;
    END IF;

    SELECT * INTO v_customer FROM customers WHERE id = NEW.customer_id;

    IF v_customer.email IS NULL OR TRIM(v_customer.email) = '' THEN
        RETURN NEW;
    END IF;

    v_message := 'Dear ' || v_customer.full_name || ', '
        || 'Your ' || TRIM(fn_format_billing_period(NEW.billing_year, NEW.billing_month))
        || ' utility bill of ' || NEW.total_amount || ' FRW has been successfully processed.';

    INSERT INTO notifications (customer_id, bill_id, message, notification_type, read_flag, email_sent)
    VALUES (NEW.customer_id, NEW.id, v_message, 'BILL_GENERATED', FALSE, FALSE);

    RETURN NEW;
END;
$$;

CREATE OR REPLACE PROCEDURE sp_record_payment(
    p_bill_id        BIGINT,
    p_amount_paid    NUMERIC,
    p_payment_method VARCHAR,
    p_payment_date   DATE,
  OUT p_payment_id   BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_bill     bills%ROWTYPE;
    v_customer customers%ROWTYPE;
    v_message  TEXT;
BEGIN
    SELECT * INTO v_bill FROM bills WHERE id = p_bill_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Bill not found: %', p_bill_id;
    END IF;

    IF v_bill.bill_status NOT IN ('APPROVED', 'PARTIALLY_PAID') THEN
        RAISE EXCEPTION 'Bill % must be approved before payment', v_bill.reference;
    END IF;

    IF v_bill.bill_status = 'PAID' THEN
        RAISE EXCEPTION 'Bill % is already fully paid', v_bill.reference;
    END IF;

    IF p_amount_paid <= 0 THEN
        RAISE EXCEPTION 'Payment amount must be greater than zero';
    END IF;

    IF p_amount_paid > v_bill.outstanding_balance THEN
        RAISE EXCEPTION 'Payment amount exceeds outstanding balance of %', v_bill.outstanding_balance;
    END IF;

    IF p_payment_date > CURRENT_DATE THEN
        RAISE EXCEPTION 'Payment date cannot be in the future';
    END IF;

    INSERT INTO payments (bill_id, amount_paid, payment_method, payment_date, reference, created_at, updated_at, deleted, status)
    VALUES (p_bill_id, p_amount_paid, p_payment_method, p_payment_date, 'PAY-' || TO_CHAR(NOW(), 'YYYYMMDDHH24MISS'), NOW(), NOW(), FALSE, 'ACTIVE')
    RETURNING id INTO p_payment_id;

    UPDATE bills
    SET amount_paid         = amount_paid + p_amount_paid,
        outstanding_balance = outstanding_balance - p_amount_paid,
        updated_at          = NOW()
    WHERE id = p_bill_id
    RETURNING * INTO v_bill;

    IF v_bill.outstanding_balance <= 0 THEN
        UPDATE bills
        SET bill_status         = 'PAID',
            outstanding_balance = 0,
            updated_at          = NOW()
        WHERE id = p_bill_id
        RETURNING * INTO v_bill;

        SELECT * INTO v_customer FROM customers WHERE id = v_bill.customer_id;

        IF NOT EXISTS (
            SELECT 1 FROM notifications
            WHERE bill_id = v_bill.id AND notification_type = 'PAYMENT_COMPLETED'
        ) THEN
            v_message := 'Dear ' || v_customer.full_name || ', '
                || 'Your ' || TRIM(fn_format_billing_period(v_bill.billing_year, v_bill.billing_month))
                || ' utility bill of ' || v_bill.total_amount || ' FRW has been successfully processed.';

            INSERT INTO notifications (customer_id, bill_id, message, notification_type, read_flag, email_sent)
            VALUES (v_bill.customer_id, v_bill.id, v_message, 'PAYMENT_COMPLETED', FALSE, FALSE);
        END IF;
    ELSE
        UPDATE bills
        SET bill_status = 'PARTIALLY_PAID',
            updated_at  = NOW()
        WHERE id = p_bill_id;
    END IF;
END;
$$;
