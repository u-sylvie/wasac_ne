-- =============================================================================
-- V8 — Database routines for billing notifications and payment processing
-- =============================================================================

CREATE OR REPLACE FUNCTION fn_format_billing_period(p_year INT, p_month INT)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN TO_CHAR(TO_DATE(p_year || '-' || p_month || '-01', 'YYYY-MM-DD'), 'Month YYYY');
END;
$$;

CREATE OR REPLACE FUNCTION fn_notify_bill_generated()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_customer customers%ROWTYPE;
    v_message  TEXT;
BEGIN
    SELECT * INTO v_customer FROM customers WHERE id = NEW.customer_id;

    v_message := 'Dear ' || v_customer.full_name || ', '
        || 'Your ' || TRIM(fn_format_billing_period(NEW.billing_year, NEW.billing_month))
        || ' utility bill of ' || NEW.total_amount || ' FRW has been successfully processed.';

    INSERT INTO notifications (customer_id, bill_id, message, notification_type, read_flag, email_sent)
    VALUES (NEW.customer_id, NEW.id, v_message, 'BILL_GENERATED', FALSE, FALSE);

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_bill_generated_notification ON bills;

CREATE TRIGGER trg_bill_generated_notification
    AFTER INSERT ON bills
    FOR EACH ROW
    EXECUTE FUNCTION fn_notify_bill_generated();

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

    IF v_bill.bill_status = 'PAID' THEN
        RAISE EXCEPTION 'Bill % is already fully paid', v_bill.reference;
    END IF;

    IF p_amount_paid <= 0 THEN
        RAISE EXCEPTION 'Payment amount must be greater than zero';
    END IF;

    INSERT INTO payments (bill_id, amount_paid, payment_method, payment_date, reference, created_at, updated_at, deleted, status)
    VALUES (p_bill_id, p_amount_paid, p_payment_method, p_payment_date, 'PAY-' || TO_CHAR(NOW(), 'YYYYMMDDHH24MISS'), NOW(), NOW(), FALSE, 'ACTIVE')
    RETURNING id INTO p_payment_id;

    UPDATE bills
    SET amount_paid         = amount_paid + p_amount_paid,
        outstanding_balance = GREATEST(outstanding_balance - p_amount_paid, 0),
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

        v_message := 'Dear ' || v_customer.full_name || ', '
            || 'Your ' || TRIM(fn_format_billing_period(v_bill.billing_year, v_bill.billing_month))
            || ' utility bill of ' || v_bill.total_amount || ' FRW has been successfully processed.';

        INSERT INTO notifications (customer_id, bill_id, message, notification_type, read_flag, email_sent)
        VALUES (v_bill.customer_id, v_bill.id, v_message, 'PAYMENT_COMPLETED', FALSE, FALSE);
    END IF;
END;
$$;
