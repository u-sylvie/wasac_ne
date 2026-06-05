--
-- PostgreSQL database dump
--

\restrict QnRbJ1E05FcdHOMXt1MbC8eLma9SZ5pHj8tam5SEFSZPEoutWyXsLUhOAXOVT5R

-- Dumped from database version 18.0
-- Dumped by pg_dump version 18.0

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: fn_format_billing_period(integer, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_format_billing_period(p_year integer, p_month integer) RETURNS text
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN TO_CHAR(TO_DATE(p_year || '-' || p_month || '-01', 'YYYY-MM-DD'), 'Month YYYY');
END;
$$;


--
-- Name: fn_notify_bill_generated(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_notify_bill_generated() RETURNS trigger
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


--
-- Name: sp_record_payment(bigint, numeric, character varying, date); Type: PROCEDURE; Schema: public; Owner: -
--

CREATE PROCEDURE public.sp_record_payment(IN p_bill_id bigint, IN p_amount_paid numeric, IN p_payment_method character varying, IN p_payment_date date, OUT p_payment_id bigint)
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


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id bigint NOT NULL,
    entity_name character varying(100) NOT NULL,
    entity_id bigint NOT NULL,
    action character varying(30) NOT NULL,
    performed_by character varying(100),
    details text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_logs_id_seq OWNED BY public.audit_logs.id;


--
-- Name: bills; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bills (
    id bigint NOT NULL,
    reference character varying(50) NOT NULL,
    customer_id bigint NOT NULL,
    meter_id bigint NOT NULL,
    meter_reading_id bigint NOT NULL,
    tariff_config_id bigint NOT NULL,
    billing_year integer NOT NULL,
    billing_month integer NOT NULL,
    consumption numeric(12,3) NOT NULL,
    consumption_charge numeric(12,2) NOT NULL,
    fixed_charge numeric(12,2) NOT NULL,
    tax_amount numeric(12,2) NOT NULL,
    penalty_amount numeric(12,2) DEFAULT 0 NOT NULL,
    total_amount numeric(12,2) NOT NULL,
    amount_paid numeric(12,2) DEFAULT 0 NOT NULL,
    outstanding_balance numeric(12,2) NOT NULL,
    due_date date NOT NULL,
    bill_status character varying(20) DEFAULT 'UNPAID'::character varying NOT NULL,
    approved_at timestamp with time zone,
    approved_by character varying(100),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(100),
    updated_by character varying(100),
    deleted boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT chk_bills_status CHECK (((bill_status)::text = ANY ((ARRAY['UNPAID'::character varying, 'APPROVED'::character varying, 'PARTIALLY_PAID'::character varying, 'PAID'::character varying, 'OVERDUE'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: bills_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bills_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bills_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bills_id_seq OWNED BY public.bills.id;


--
-- Name: customers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customers (
    id bigint NOT NULL,
    full_name character varying(150) NOT NULL,
    national_id character varying(20) NOT NULL,
    email character varying(254) NOT NULL,
    phone character varying(30) NOT NULL,
    address character varying(255) NOT NULL,
    user_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(100),
    updated_by character varying(100),
    deleted boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    date_of_birth date,
    CONSTRAINT chk_customers_national_id_format CHECK (((national_id)::text ~ '^[0-9]{16}$'::text)),
    CONSTRAINT chk_customers_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'PENDING'::character varying])::text[])))
);


--
-- Name: COLUMN customers.national_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customers.national_id IS 'Rwanda National ID — exactly 16 digits. UNIQUE. Primary customer identifier for billing.';


--
-- Name: customers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customers_id_seq OWNED BY public.customers.id;


--
-- Name: email_verification_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_verification_tokens (
    id bigint NOT NULL,
    token character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: email_verification_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_verification_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_verification_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_verification_tokens_id_seq OWNED BY public.email_verification_tokens.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: meter_readings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meter_readings (
    id bigint NOT NULL,
    meter_id bigint NOT NULL,
    previous_reading numeric(12,3) NOT NULL,
    current_reading numeric(12,3) NOT NULL,
    reading_date date NOT NULL,
    billing_year integer NOT NULL,
    billing_month integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(100),
    updated_by character varying(100),
    deleted boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT chk_meter_readings_month CHECK (((billing_month >= 1) AND (billing_month <= 12))),
    CONSTRAINT chk_meter_readings_order CHECK ((current_reading > previous_reading))
);


--
-- Name: meter_readings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.meter_readings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: meter_readings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.meter_readings_id_seq OWNED BY public.meter_readings.id;


--
-- Name: meters; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meters (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    meter_number character varying(50) NOT NULL,
    meter_type character varying(20) NOT NULL,
    installation_date date NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(100),
    updated_by character varying(100),
    deleted boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT chk_meters_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'PENDING'::character varying, 'DISCONNECTED'::character varying])::text[]))),
    CONSTRAINT chk_meters_type CHECK (((meter_type)::text = ANY ((ARRAY['WATER'::character varying, 'ELECTRICITY'::character varying])::text[])))
);


--
-- Name: meters_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.meters_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: meters_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.meters_id_seq OWNED BY public.meters.id;


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    bill_id bigint,
    message text NOT NULL,
    notification_type character varying(30) NOT NULL,
    read_flag boolean DEFAULT false NOT NULL,
    email_sent boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notifications_id_seq OWNED BY public.notifications.id;


--
-- Name: otp_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.otp_tokens (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    code character varying(6) NOT NULL,
    purpose character varying(30) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: otp_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.otp_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: otp_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.otp_tokens_id_seq OWNED BY public.otp_tokens.id;


--
-- Name: password_reset_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_tokens (
    id bigint NOT NULL,
    token character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: password_reset_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.password_reset_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: password_reset_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.password_reset_tokens_id_seq OWNED BY public.password_reset_tokens.id;


--
-- Name: payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payments (
    id bigint NOT NULL,
    bill_id bigint NOT NULL,
    amount_paid numeric(12,2) NOT NULL,
    payment_method character varying(30) NOT NULL,
    payment_date date NOT NULL,
    reference character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(100),
    updated_by character varying(100),
    deleted boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT chk_payments_amount CHECK ((amount_paid > (0)::numeric))
);


--
-- Name: payments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payments_id_seq OWNED BY public.payments.id;


--
-- Name: revoked_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.revoked_tokens (
    id bigint NOT NULL,
    token_jti character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: revoked_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.revoked_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: revoked_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.revoked_tokens_id_seq OWNED BY public.revoked_tokens.id;


--
-- Name: tariff_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tariff_configs (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    meter_type character varying(20) NOT NULL,
    tariff_type character varying(20) NOT NULL,
    rate_per_unit numeric(12,4),
    fixed_service_charge numeric(12,2) DEFAULT 0 NOT NULL,
    vat_percentage numeric(5,2) DEFAULT 18 NOT NULL,
    penalty_percentage numeric(5,2) DEFAULT 0 NOT NULL,
    version integer NOT NULL,
    effective_from date NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(100),
    updated_by character varying(100),
    deleted boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT chk_tariff_meter_type CHECK (((meter_type)::text = ANY ((ARRAY['WATER'::character varying, 'ELECTRICITY'::character varying])::text[]))),
    CONSTRAINT chk_tariff_type CHECK (((tariff_type)::text = ANY ((ARRAY['FLAT'::character varying, 'TIERED'::character varying])::text[])))
);


--
-- Name: tariff_configs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tariff_configs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tariff_configs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tariff_configs_id_seq OWNED BY public.tariff_configs.id;


--
-- Name: tariff_tiers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tariff_tiers (
    id bigint NOT NULL,
    tariff_config_id bigint NOT NULL,
    from_units numeric(12,3) NOT NULL,
    to_units numeric(12,3),
    rate_per_unit numeric(12,4) NOT NULL
);


--
-- Name: tariff_tiers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tariff_tiers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tariff_tiers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tariff_tiers_id_seq OWNED BY public.tariff_tiers.id;


--
-- Name: uploaded_files; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.uploaded_files (
    id bigint NOT NULL,
    entity_type character varying(50) NOT NULL,
    entity_id bigint NOT NULL,
    original_name character varying(255) NOT NULL,
    stored_name character varying(255) NOT NULL,
    content_type character varying(100),
    file_size bigint NOT NULL,
    file_path character varying(500) NOT NULL,
    uploaded_by character varying(100),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: uploaded_files_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.uploaded_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: uploaded_files_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.uploaded_files_id_seq OWNED BY public.uploaded_files.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    first_name character varying(50) NOT NULL,
    last_name character varying(50) NOT NULL,
    username character varying(50) NOT NULL,
    email character varying(254) NOT NULL,
    password character varying(255) NOT NULL,
    role character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(100),
    updated_by character varying(100),
    deleted boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    phone character varying(30) NOT NULL,
    must_change_password boolean DEFAULT false NOT NULL,
    CONSTRAINT chk_users_role CHECK (((role)::text = ANY ((ARRAY['ADMIN'::character varying, 'OPERATOR'::character varying, 'FINANCE'::character varying, 'CUSTOMER'::character varying, 'USER'::character varying, 'MODERATOR'::character varying])::text[]))),
    CONSTRAINT chk_users_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'PENDING'::character varying])::text[])))
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ALTER COLUMN id SET DEFAULT nextval('public.audit_logs_id_seq'::regclass);


--
-- Name: bills id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills ALTER COLUMN id SET DEFAULT nextval('public.bills_id_seq'::regclass);


--
-- Name: customers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers ALTER COLUMN id SET DEFAULT nextval('public.customers_id_seq'::regclass);


--
-- Name: email_verification_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens ALTER COLUMN id SET DEFAULT nextval('public.email_verification_tokens_id_seq'::regclass);


--
-- Name: meter_readings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meter_readings ALTER COLUMN id SET DEFAULT nextval('public.meter_readings_id_seq'::regclass);


--
-- Name: meters id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meters ALTER COLUMN id SET DEFAULT nextval('public.meters_id_seq'::regclass);


--
-- Name: notifications id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications ALTER COLUMN id SET DEFAULT nextval('public.notifications_id_seq'::regclass);


--
-- Name: otp_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.otp_tokens ALTER COLUMN id SET DEFAULT nextval('public.otp_tokens_id_seq'::regclass);


--
-- Name: password_reset_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens ALTER COLUMN id SET DEFAULT nextval('public.password_reset_tokens_id_seq'::regclass);


--
-- Name: payments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments ALTER COLUMN id SET DEFAULT nextval('public.payments_id_seq'::regclass);


--
-- Name: revoked_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revoked_tokens ALTER COLUMN id SET DEFAULT nextval('public.revoked_tokens_id_seq'::regclass);


--
-- Name: tariff_configs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tariff_configs ALTER COLUMN id SET DEFAULT nextval('public.tariff_configs_id_seq'::regclass);


--
-- Name: tariff_tiers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tariff_tiers ALTER COLUMN id SET DEFAULT nextval('public.tariff_tiers_id_seq'::regclass);


--
-- Name: uploaded_files id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uploaded_files ALTER COLUMN id SET DEFAULT nextval('public.uploaded_files_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Data for Name: audit_logs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.audit_logs (id, entity_name, entity_id, action, performed_by, details, created_at) FROM stdin;
1	MeterReading	1	CREATE	operator@wasac.rw	Created reading for meter WTR-0001	2026-06-05 10:26:13.783197+02
2	Bill	1	CREATE	admin@javat.com	Generated bill BILL-A4B7CFF9 for customer customer@wasac.rw	2026-06-05 10:28:14.209237+02
3	Bill	1	UPDATE	admin@javat.com	Approved bill BILL-A4B7CFF9	2026-06-05 10:28:14.242187+02
4	Payment	1	CREATE	finance@wasac.rw	Recorded payment of 28615.00 for bill BILL-A4B7CFF9	2026-06-05 10:28:14.676671+02
5	User	13	CREATE	admin@javat.com	Admin created OPERATOR user uwerarca@gmail.com	2026-06-05 11:22:33.649013+02
6	User	14	UPDATE	admin@javat.com	Role changed from CUSTOMER to FINANCE	2026-06-05 12:21:44.055199+02
7	Notification	0	UPDATE	admin@javat.com	Sent 2 pending notification emails	2026-06-05 12:24:12.284936+02
8	Customer	2	CREATE	admin@javat.com	Created customer NID=1199988776655445 email=sylvieuwera369@gmail.com	2026-06-05 12:49:23.163886+02
9	Meter	3	CREATE	admin@javat.com	Created meter ELC-0002	2026-06-05 12:49:59.005723+02
11	Meter	2	DELETE	admin@javat.com	Deleted meter ELC-0001	2026-06-05 12:53:37.219397+02
12	Meter	3	UPDATE	admin@javat.com	Updated meter WTR-002	2026-06-05 12:54:19.576417+02
13	MeterReading	5	CREATE	uwerarca@gmail.com	Created reading for meter WTR-0001	2026-06-05 13:46:49.911694+02
14	MeterReading	6	CREATE	uwerarca@gmail.com	Created reading for meter WTR-002	2026-06-05 13:48:26.96089+02
15	Bill	2	CREATE	sylvieuwera71@gmail.com	Generated bill BILL-CED139A3 for customer sylvieuwera369@gmail.com	2026-06-05 14:07:27.976732+02
16	Notification	0	UPDATE	system-scheduler	Sent 1 pending notification emails	2026-06-05 14:09:27.074023+02
17	Bill	2	UPDATE	sylvieuwera71@gmail.com	Approved bill BILL-CED139A3	2026-06-05 14:09:48.745951+02
\.


--
-- Data for Name: bills; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.bills (id, reference, customer_id, meter_id, meter_reading_id, tariff_config_id, billing_year, billing_month, consumption, consumption_charge, fixed_charge, tax_amount, penalty_amount, total_amount, amount_paid, outstanding_balance, due_date, bill_status, approved_at, approved_by, created_at, updated_at, created_by, updated_by, deleted, deleted_at, deleted_by, status) FROM stdin;
1	BILL-A4B7CFF9	1	1	1	1	2026	6	65.000	22750.00	1500.00	4365.00	0.00	28615.00	28615.00	0.00	2026-07-01	PAID	2026-06-05 10:28:14.242187+02	admin@javat.com	2026-06-05 10:28:14.128044+02	2026-06-05 10:28:14.67759+02	admin@javat.com	admin@javat.com	f	\N	\N	ACTIVE
2	BILL-CED139A3	2	3	6	1	2026	8	300.000	105000.00	1500.00	19170.00	0.00	125670.00	0.00	125670.00	2026-06-05	APPROVED	2026-06-05 14:09:48.744978+02	sylvieuwera71@gmail.com	2026-06-05 14:07:27.912491+02	2026-06-05 14:09:48.748872+02	sylvieuwera71@gmail.com	sylvieuwera71@gmail.com	f	\N	\N	ACTIVE
\.


--
-- Data for Name: customers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.customers (id, full_name, national_id, email, phone, address, user_id, created_at, updated_at, created_by, updated_by, deleted, deleted_at, deleted_by, status, date_of_birth) FROM stdin;
1	Eric Customer	1199880011223344	customer@wasac.rw	+250788333333	Kigali, Gasabo	9	2026-06-05 10:13:54.965455+02	2026-06-05 10:13:54.965455+02	\N	\N	f	\N	\N	ACTIVE	\N
2	Sylvie Uwera	1199988776655445	sylvieuwera369@gmail.com	0788123456	KGL-01	12	2026-06-05 12:49:23.149226+02	2026-06-05 12:49:23.149226+02	admin@javat.com	admin@javat.com	f	\N	\N	ACTIVE	2005-06-05
\.


--
-- Data for Name: email_verification_tokens; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.email_verification_tokens (id, token, user_id, expires_at, created_at) FROM stdin;
7	72SMfjTSc0UtPTFKl9JB1KlRJUjA-c-mmtfBLU4unZlMR_bZPzx7PN60K-W08bfD	11	2026-06-06 10:46:25.359384+02	2026-06-05 10:46:25.359384+02
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	create users table	SQL	V1__create_users_table.sql	753537118	postgres	2026-06-04 20:58:07.461833	20	t
2	2	seed admin user	SQL	V2__seed_admin_user.sql	-1413552071	postgres	2026-06-04 20:58:07.496317	4	t
3	3	create password reset tokens table	SQL	V3__create_password_reset_tokens_table.sql	1003729252	postgres	2026-06-04 20:58:07.507248	7	t
4	4	create email verification tokens table	SQL	V4__create_email_verification_tokens_table.sql	-699825629	postgres	2026-06-04 20:58:07.518872	5	t
5	5	update admin password	SQL	V5__update_admin_password.sql	1770104125	postgres	2026-06-04 20:58:07.529022	2	t
6	6	update users for utility billing	SQL	V6__update_users_for_utility_billing.sql	-1061626226	postgres	2026-06-05 10:05:39.923188	115	t
7	7	create utility billing tables	SQL	V7__create_utility_billing_tables.sql	1022755524	postgres	2026-06-05 10:05:40.062934	80	t
8	8	create billing routines	SQL	V8__create_billing_routines.sql	377271111	postgres	2026-06-05 10:05:40.155622	73	t
9	9	seed sample utility data	SQL	V9__seed_sample_utility_data.sql	1157049419	postgres	2026-06-05 10:13:54.95632	29	t
10	10	sync seeded user passwords	SQL	V10__sync_seeded_user_passwords.sql	1367284058	postgres	2026-06-05 10:25:57.499371	6	t
11	11	fix tariff effective dates	SQL	V11__fix_tariff_effective_dates.sql	-2086229526	postgres	2026-06-05 10:27:55.852485	5	t
12	12	exam validations and wasac updates	SQL	V12__exam_validations_and_wasac_updates.sql	595359402	postgres	2026-06-05 10:43:18.100033	231	t
13	13	national id format constraint	SQL	V13__national_id_format_constraint.sql	1309863275	postgres	2026-06-05 11:05:05.67016	41	t
\.


--
-- Data for Name: meter_readings; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.meter_readings (id, meter_id, previous_reading, current_reading, reading_date, billing_year, billing_month, created_at, updated_at, created_by, updated_by, deleted, deleted_at, deleted_by, status) FROM stdin;
1	1	200.000	265.000	2026-06-01	2026	6	2026-06-05 10:26:13.759025+02	2026-06-05 10:26:13.759025+02	operator@wasac.rw	operator@wasac.rw	f	\N	\N	ACTIVE
5	1	265.000	300.000	2026-06-05	2026	7	2026-06-05 13:46:49.905706+02	2026-06-05 13:46:49.905706+02	uwerarca@gmail.com	uwerarca@gmail.com	f	\N	\N	ACTIVE
6	3	0.000	300.000	2026-06-05	2026	8	2026-06-05 13:48:26.96089+02	2026-06-05 13:48:26.96089+02	uwerarca@gmail.com	uwerarca@gmail.com	f	\N	\N	ACTIVE
\.


--
-- Data for Name: meters; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.meters (id, customer_id, meter_number, meter_type, installation_date, created_at, updated_at, created_by, updated_by, deleted, deleted_at, deleted_by, status) FROM stdin;
1	1	WTR-0001	WATER	2025-06-05	2026-06-05 10:13:54.965455+02	2026-06-05 10:13:54.965455+02	\N	\N	f	\N	\N	ACTIVE
3	2	WTR-002	WATER	2026-06-05	2026-06-05 12:49:59.005723+02	2026-06-05 12:54:19.576417+02	admin@javat.com	admin@javat.com	f	\N	\N	ACTIVE
\.


--
-- Data for Name: notifications; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notifications (id, customer_id, bill_id, message, notification_type, read_flag, email_sent, created_at) FROM stdin;
1	1	1	Dear Eric Customer, Your June      2026 utility bill of 28615.00 FRW has been successfully processed.	BILL_GENERATED	f	t	2026-06-05 10:28:14.057361+02
2	1	1	Dear Eric Customer, Your June      2026 utility bill of 28615.00 FRW has been successfully processed.	PAYMENT_COMPLETED	f	t	2026-06-05 10:28:14.67759+02
3	2	2	Dear Sylvie Uwera, Your August    2026 utility bill of 125670.00 FRW has been successfully processed.	BILL_GENERATED	f	t	2026-06-05 14:07:27.875259+02
\.


--
-- Data for Name: otp_tokens; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.otp_tokens (id, user_id, code, purpose, expires_at, used, created_at) FROM stdin;
2	13	217254	EMAIL_VERIFICATION	2026-06-05 11:38:16.219885+02	t	2026-06-05 11:28:16.219885+02
8	13	393003	PASSWORD_RESET	2026-06-05 13:41:23.566208+02	f	2026-06-05 13:31:23.566208+02
\.


--
-- Data for Name: password_reset_tokens; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.password_reset_tokens (id, token, user_id, expires_at, used, created_at) FROM stdin;
7	nYYLlzPNIAgF0nitkJfnmfqKs9UZFWTN4QLs9GX56-hXMhHWMCN8JtrOZUiCAHNH	13	2026-06-05 13:46:23.548755+02	t	2026-06-05 13:31:23.548755+02
\.


--
-- Data for Name: payments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.payments (id, bill_id, amount_paid, payment_method, payment_date, reference, created_at, updated_at, created_by, updated_by, deleted, deleted_at, deleted_by, status) FROM stdin;
1	1	28615.00	MOMO	2026-06-05	PAY-20260605102814	2026-06-05 10:28:14.67759+02	2026-06-05 10:28:14.67759+02	\N	\N	f	\N	\N	ACTIVE
\.


--
-- Data for Name: revoked_tokens; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.revoked_tokens (id, token_jti, expires_at, revoked_at) FROM stdin;
\.


--
-- Data for Name: tariff_configs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.tariff_configs (id, name, meter_type, tariff_type, rate_per_unit, fixed_service_charge, vat_percentage, penalty_percentage, version, effective_from, active, created_at, updated_at, created_by, updated_by, deleted, deleted_at, deleted_by, status) FROM stdin;
1	Water Flat Tariff v1	WATER	FLAT	350.0000	1500.00	18.00	5.00	1	2020-01-01	t	2026-06-05 10:13:54.965455+02	2026-06-05 10:27:55.861146+02	\N	system	f	\N	\N	ACTIVE
2	Electricity Flat Tariff v1	ELECTRICITY	FLAT	120.0000	2000.00	18.00	5.00	1	2020-01-01	t	2026-06-05 10:13:54.965455+02	2026-06-05 10:27:55.861146+02	\N	system	f	\N	\N	ACTIVE
\.


--
-- Data for Name: tariff_tiers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.tariff_tiers (id, tariff_config_id, from_units, to_units, rate_per_unit) FROM stdin;
\.


--
-- Data for Name: uploaded_files; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.uploaded_files (id, entity_type, entity_id, original_name, stored_name, content_type, file_size, file_path, uploaded_by, created_at) FROM stdin;
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users (id, first_name, last_name, username, email, password, role, created_at, updated_at, created_by, updated_by, deleted, deleted_at, deleted_by, status, phone, must_change_password) FROM stdin;
1	Admin	User	admin	admin@javat.com	$2a$12$LFzYDzFjW5/sobDavnnG5ON314MRp5dvkjOfbwVXGROl/Y.S4HMji	ADMIN	2026-06-04 20:58:07.500997+02	2026-06-04 20:58:07.530979+02	system	system	f	\N	\N	ACTIVE	+250700000000	f
7	Jean	Operator	operator	operator@wasac.rw	$2a$12$LFzYDzFjW5/sobDavnnG5ON314MRp5dvkjOfbwVXGROl/Y.S4HMji	OPERATOR	2026-06-05 10:13:54.965455+02	2026-06-05 10:25:57.506719+02	system	system	f	\N	\N	ACTIVE	+250788111111	f
8	Alice	Finance	finance	finance@wasac.rw	$2a$12$LFzYDzFjW5/sobDavnnG5ON314MRp5dvkjOfbwVXGROl/Y.S4HMji	FINANCE	2026-06-05 10:13:54.965455+02	2026-06-05 10:25:57.506719+02	system	system	f	\N	\N	ACTIVE	+250788222222	f
9	Eric	Customer	eric	customer@wasac.rw	$2a$12$LFzYDzFjW5/sobDavnnG5ON314MRp5dvkjOfbwVXGROl/Y.S4HMji	CUSTOMER	2026-06-05 10:13:54.965455+02	2026-06-05 10:25:57.506719+02	system	system	f	\N	\N	ACTIVE	+250788333333	f
11	Sylvie	Uwera	Sylvie	sylvieuwera@gmail.com	$2a$10$qnBXwGRTtMkzCYWcRcdhxecNlb.GX7JRdHpRx42THgwzlkv5uwaWW	CUSTOMER	2026-06-05 10:46:25.343063+02	2026-06-05 10:46:25.343063+02	system	system	f	\N	\N	PENDING	+250788123456	f
14	Rolanda	Darlen	Rolanda	sylvieuwera71@gmail.com	$2a$10$jdxeOw0WqYEOup0PGBR7TetYOvbpwvJza342nO2QTBsbEUnRgMpDG	FINANCE	2026-06-05 12:16:57.313837+02	2026-06-05 12:21:44.055199+02	system	admin@javat.com	f	\N	\N	ACTIVE	+250788123454	f
12	Sylvie	Uwera	Keza	sylvieuwera369@gmail.com	$2a$10$lZnWdMfYX09ktMSneaywsegPQSSUzkdy1POKQle/SNxqSitsdO6i2	CUSTOMER	2026-06-05 10:47:33.044266+02	2026-06-05 12:58:05.386188+02	system	admin@javat.com	f	\N	\N	ACTIVE	+250788123456	f
13	Kaneza	Uwase	Kaneza	uwerarca@gmail.com	$2a$10$v3Ee/1dWSJ73OiFhG8WZbebce5z3LuzNFu6ZvfgTv37Hdj7YYUK6q	OPERATOR	2026-06-05 11:22:33.615679+02	2026-06-05 13:32:27.381459+02	admin@javat.com	system	f	\N	\N	ACTIVE	0788334422	f
\.


--
-- Name: audit_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.audit_logs_id_seq', 17, true);


--
-- Name: bills_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.bills_id_seq', 2, true);


--
-- Name: customers_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.customers_id_seq', 2, true);


--
-- Name: email_verification_tokens_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.email_verification_tokens_id_seq', 9, true);


--
-- Name: meter_readings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.meter_readings_id_seq', 6, true);


--
-- Name: meters_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.meters_id_seq', 3, true);


--
-- Name: notifications_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notifications_id_seq', 3, true);


--
-- Name: otp_tokens_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.otp_tokens_id_seq', 8, true);


--
-- Name: password_reset_tokens_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.password_reset_tokens_id_seq', 7, true);


--
-- Name: payments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.payments_id_seq', 1, true);


--
-- Name: revoked_tokens_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revoked_tokens_id_seq', 1, false);


--
-- Name: tariff_configs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tariff_configs_id_seq', 2, true);


--
-- Name: tariff_tiers_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tariff_tiers_id_seq', 1, false);


--
-- Name: uploaded_files_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.uploaded_files_id_seq', 1, false);


--
-- Name: users_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.users_id_seq', 14, true);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: audit_logs pk_audit_logs; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT pk_audit_logs PRIMARY KEY (id);


--
-- Name: bills pk_bills; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT pk_bills PRIMARY KEY (id);


--
-- Name: customers pk_customers; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT pk_customers PRIMARY KEY (id);


--
-- Name: email_verification_tokens pk_email_verification_tokens; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT pk_email_verification_tokens PRIMARY KEY (id);


--
-- Name: meter_readings pk_meter_readings; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meter_readings
    ADD CONSTRAINT pk_meter_readings PRIMARY KEY (id);


--
-- Name: meters pk_meters; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meters
    ADD CONSTRAINT pk_meters PRIMARY KEY (id);


--
-- Name: notifications pk_notifications; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT pk_notifications PRIMARY KEY (id);


--
-- Name: otp_tokens pk_otp_tokens; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.otp_tokens
    ADD CONSTRAINT pk_otp_tokens PRIMARY KEY (id);


--
-- Name: password_reset_tokens pk_password_reset_tokens; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id);


--
-- Name: payments pk_payments; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT pk_payments PRIMARY KEY (id);


--
-- Name: revoked_tokens pk_revoked_tokens; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revoked_tokens
    ADD CONSTRAINT pk_revoked_tokens PRIMARY KEY (id);


--
-- Name: tariff_configs pk_tariff_configs; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tariff_configs
    ADD CONSTRAINT pk_tariff_configs PRIMARY KEY (id);


--
-- Name: tariff_tiers pk_tariff_tiers; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tariff_tiers
    ADD CONSTRAINT pk_tariff_tiers PRIMARY KEY (id);


--
-- Name: uploaded_files pk_uploaded_files; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uploaded_files
    ADD CONSTRAINT pk_uploaded_files PRIMARY KEY (id);


--
-- Name: users pk_users; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT pk_users PRIMARY KEY (id);


--
-- Name: bills uq_bills_meter_period; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT uq_bills_meter_period UNIQUE (meter_id, billing_year, billing_month);


--
-- Name: bills uq_bills_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT uq_bills_reference UNIQUE (reference);


--
-- Name: customers uq_customers_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT uq_customers_email UNIQUE (email);


--
-- Name: customers uq_customers_national_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT uq_customers_national_id UNIQUE (national_id);


--
-- Name: customers uq_customers_phone; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT uq_customers_phone UNIQUE (phone);


--
-- Name: email_verification_tokens uq_evt_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT uq_evt_token UNIQUE (token);


--
-- Name: email_verification_tokens uq_evt_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT uq_evt_user_id UNIQUE (user_id);


--
-- Name: meter_readings uq_meter_readings_period; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meter_readings
    ADD CONSTRAINT uq_meter_readings_period UNIQUE (meter_id, billing_year, billing_month);


--
-- Name: meters uq_meters_meter_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meters
    ADD CONSTRAINT uq_meters_meter_number UNIQUE (meter_number);


--
-- Name: password_reset_tokens uq_password_reset_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT uq_password_reset_token UNIQUE (token);


--
-- Name: revoked_tokens uq_revoked_tokens_jti; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revoked_tokens
    ADD CONSTRAINT uq_revoked_tokens_jti UNIQUE (token_jti);


--
-- Name: tariff_configs uq_tariff_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tariff_configs
    ADD CONSTRAINT uq_tariff_version UNIQUE (meter_type, version);


--
-- Name: users uq_users_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_email UNIQUE (email);


--
-- Name: users uq_users_username; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_username UNIQUE (username);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_audit_logs_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_entity ON public.audit_logs USING btree (entity_name, entity_id);


--
-- Name: idx_bills_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bills_customer_id ON public.bills USING btree (customer_id);


--
-- Name: idx_bills_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bills_reference ON public.bills USING btree (reference);


--
-- Name: idx_bills_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bills_status ON public.bills USING btree (bill_status);


--
-- Name: idx_customers_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customers_email ON public.customers USING btree (email);


--
-- Name: idx_customers_national_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customers_national_id ON public.customers USING btree (national_id);


--
-- Name: idx_customers_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customers_status ON public.customers USING btree (status);


--
-- Name: idx_evt_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evt_expires ON public.email_verification_tokens USING btree (expires_at);


--
-- Name: idx_evt_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evt_token ON public.email_verification_tokens USING btree (token);


--
-- Name: idx_meter_readings_meter_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meter_readings_meter_id ON public.meter_readings USING btree (meter_id);


--
-- Name: idx_meter_readings_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meter_readings_period ON public.meter_readings USING btree (billing_year, billing_month);


--
-- Name: idx_meters_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meters_customer_id ON public.meters USING btree (customer_id);


--
-- Name: idx_meters_meter_number; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meters_meter_number ON public.meters USING btree (meter_number);


--
-- Name: idx_notifications_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_customer_id ON public.notifications USING btree (customer_id);


--
-- Name: idx_otp_tokens_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_otp_tokens_code ON public.otp_tokens USING btree (code);


--
-- Name: idx_otp_tokens_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_otp_tokens_user_id ON public.otp_tokens USING btree (user_id);


--
-- Name: idx_payments_bill_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_bill_id ON public.payments USING btree (bill_id);


--
-- Name: idx_prt_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_prt_expires ON public.password_reset_tokens USING btree (expires_at);


--
-- Name: idx_prt_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_prt_token ON public.password_reset_tokens USING btree (token);


--
-- Name: idx_prt_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_prt_user_id ON public.password_reset_tokens USING btree (user_id);


--
-- Name: idx_revoked_tokens_jti; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_revoked_tokens_jti ON public.revoked_tokens USING btree (token_jti);


--
-- Name: idx_uploaded_files_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uploaded_files_entity ON public.uploaded_files USING btree (entity_type, entity_id);


--
-- Name: idx_users_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_deleted ON public.users USING btree (deleted);


--
-- Name: idx_users_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_email ON public.users USING btree (email);


--
-- Name: idx_users_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_phone ON public.users USING btree (phone);


--
-- Name: idx_users_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_status ON public.users USING btree (status);


--
-- Name: idx_users_username; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_username ON public.users USING btree (username);


--
-- Name: bills trg_bill_generated_notification; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_bill_generated_notification AFTER INSERT ON public.bills FOR EACH ROW EXECUTE FUNCTION public.fn_notify_bill_generated();


--
-- Name: bills fk_bills_customer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT fk_bills_customer FOREIGN KEY (customer_id) REFERENCES public.customers(id);


--
-- Name: bills fk_bills_meter; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT fk_bills_meter FOREIGN KEY (meter_id) REFERENCES public.meters(id);


--
-- Name: bills fk_bills_reading; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT fk_bills_reading FOREIGN KEY (meter_reading_id) REFERENCES public.meter_readings(id);


--
-- Name: bills fk_bills_tariff; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT fk_bills_tariff FOREIGN KEY (tariff_config_id) REFERENCES public.tariff_configs(id);


--
-- Name: customers fk_customers_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: email_verification_tokens fk_evt_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT fk_evt_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: meter_readings fk_meter_readings_meter; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meter_readings
    ADD CONSTRAINT fk_meter_readings_meter FOREIGN KEY (meter_id) REFERENCES public.meters(id) ON DELETE CASCADE;


--
-- Name: meters fk_meters_customer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meters
    ADD CONSTRAINT fk_meters_customer FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: notifications fk_notifications_bill; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fk_notifications_bill FOREIGN KEY (bill_id) REFERENCES public.bills(id) ON DELETE SET NULL;


--
-- Name: notifications fk_notifications_customer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fk_notifications_customer FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: otp_tokens fk_otp_tokens_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.otp_tokens
    ADD CONSTRAINT fk_otp_tokens_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: payments fk_payments_bill; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fk_payments_bill FOREIGN KEY (bill_id) REFERENCES public.bills(id) ON DELETE CASCADE;


--
-- Name: password_reset_tokens fk_prt_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: tariff_tiers fk_tariff_tiers_config; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tariff_tiers
    ADD CONSTRAINT fk_tariff_tiers_config FOREIGN KEY (tariff_config_id) REFERENCES public.tariff_configs(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict QnRbJ1E05FcdHOMXt1MbC8eLma9SZ5pHj8tam5SEFSZPEoutWyXsLUhOAXOVT5R

