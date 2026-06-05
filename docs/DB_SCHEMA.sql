-- =============================================================================
-- WASAC Utility Billing — Consolidated PostgreSQL Schema (Reference for ERD)
-- =============================================================================
-- This file documents the FINAL database structure after Flyway V1–V13.
-- Do NOT run this on an existing database — use Flyway migrations instead.
--
-- Generate ERD from this file using:
--   • https://dbdiagram.io  → import docs/DB_SCHEMA.dbml (recommended)
--   • DBeaver → ER Diagram from DDL
--   • pgModeler → import SQL
--   • draw.io / Lucidchart → manual import from this file
-- =============================================================================

-- -----------------------------------------------------------------------------
-- USERS & AUTH
-- -----------------------------------------------------------------------------

CREATE TABLE users (
    id                      BIGSERIAL       PRIMARY KEY,
    first_name              VARCHAR(50)     NOT NULL,
    last_name               VARCHAR(50)     NOT NULL,
    username                VARCHAR(50)     NOT NULL UNIQUE,
    email                   VARCHAR(254)    NOT NULL UNIQUE,
    phone                   VARCHAR(30)     NOT NULL,
    password                VARCHAR(255)    NOT NULL,
    role                    VARCHAR(20)     NOT NULL
                            CHECK (role IN ('ADMIN','OPERATOR','FINANCE','CUSTOMER','USER','MODERATOR')),
    must_change_password    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100),
    deleted                 BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMPTZ,
    deleted_by              VARCHAR(100),
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','PENDING'))
);

CREATE TABLE password_reset_tokens (
    id          BIGSERIAL   PRIMARY KEY,
    token       VARCHAR(64) NOT NULL UNIQUE,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE email_verification_tokens (
    id          BIGSERIAL   PRIMARY KEY,
    token       VARCHAR(64) NOT NULL UNIQUE,
    user_id     BIGINT      NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE otp_tokens (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code        VARCHAR(6)  NOT NULL,
    purpose     VARCHAR(30) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE revoked_tokens (
    id          BIGSERIAL   PRIMARY KEY,
    token_jti   VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- CUSTOMERS — National ID is the primary business identifier
-- -----------------------------------------------------------------------------

CREATE TABLE customers (
    id              BIGSERIAL       PRIMARY KEY,
    full_name       VARCHAR(150)    NOT NULL,
    national_id     VARCHAR(16)     NOT NULL UNIQUE,   -- Rwanda NID: 16 digits
    email           VARCHAR(254)    NOT NULL UNIQUE,
    phone           VARCHAR(30)     NOT NULL UNIQUE,
    address         VARCHAR(255)    NOT NULL,
    date_of_birth   DATE,
    user_id         BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    deleted_by      VARCHAR(100),
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','PENDING')),
    CONSTRAINT chk_customers_national_id_format CHECK (national_id ~ '^[0-9]{16}$')
);

CREATE INDEX idx_customers_national_id ON customers (national_id);
CREATE INDEX idx_customers_email ON customers (email);

-- -----------------------------------------------------------------------------
-- METERS & READINGS
-- -----------------------------------------------------------------------------

CREATE TABLE meters (
    id                  BIGSERIAL   PRIMARY KEY,
    customer_id         BIGINT      NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    meter_number        VARCHAR(50) NOT NULL UNIQUE,
    meter_type          VARCHAR(20) NOT NULL CHECK (meter_type IN ('WATER','ELECTRICITY')),
    installation_date   DATE        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','PENDING','DISCONNECTED'))
);

CREATE TABLE meter_readings (
    id                  BIGSERIAL       PRIMARY KEY,
    meter_id            BIGINT          NOT NULL REFERENCES meters(id) ON DELETE CASCADE,
    previous_reading    NUMERIC(12,3)   NOT NULL,
    current_reading     NUMERIC(12,3)   NOT NULL,
    reading_date        DATE            NOT NULL,
    billing_year        INT             NOT NULL,
    billing_month       INT             NOT NULL CHECK (billing_month BETWEEN 1 AND 12),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_meter_readings_period UNIQUE (meter_id, billing_year, billing_month),
    CONSTRAINT chk_meter_readings_order CHECK (current_reading > previous_reading)
);

-- -----------------------------------------------------------------------------
-- TARIFFS
-- -----------------------------------------------------------------------------

CREATE TABLE tariff_configs (
    id                    BIGSERIAL       PRIMARY KEY,
    name                  VARCHAR(100)    NOT NULL,
    meter_type            VARCHAR(20)     NOT NULL CHECK (meter_type IN ('WATER','ELECTRICITY')),
    tariff_type           VARCHAR(20)     NOT NULL CHECK (tariff_type IN ('FLAT','TIERED')),
    rate_per_unit         NUMERIC(12,4),
    fixed_service_charge  NUMERIC(12,2)   NOT NULL DEFAULT 0,
    vat_percentage        NUMERIC(5,2)    NOT NULL DEFAULT 18,
    penalty_percentage    NUMERIC(5,2)    NOT NULL DEFAULT 0,
    version               INT             NOT NULL,
    effective_from        DATE            NOT NULL,
    active                BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tariff_version UNIQUE (meter_type, version)
);

CREATE TABLE tariff_tiers (
    id               BIGSERIAL      PRIMARY KEY,
    tariff_config_id BIGINT         NOT NULL REFERENCES tariff_configs(id) ON DELETE CASCADE,
    from_units       NUMERIC(12,3)  NOT NULL,
    to_units         NUMERIC(12,3),
    rate_per_unit    NUMERIC(12,4)  NOT NULL
);

-- -----------------------------------------------------------------------------
-- BILLS & PAYMENTS
-- -----------------------------------------------------------------------------

CREATE TABLE bills (
    id                  BIGSERIAL       PRIMARY KEY,
    reference           VARCHAR(50)     NOT NULL UNIQUE,
    customer_id         BIGINT          NOT NULL REFERENCES customers(id),
    meter_id            BIGINT          NOT NULL REFERENCES meters(id),
    meter_reading_id    BIGINT          NOT NULL REFERENCES meter_readings(id),
    tariff_config_id    BIGINT          NOT NULL REFERENCES tariff_configs(id),
    billing_year        INT             NOT NULL,
    billing_month       INT             NOT NULL,
    consumption         NUMERIC(12,3)   NOT NULL,
    consumption_charge  NUMERIC(12,2)   NOT NULL,
    fixed_charge        NUMERIC(12,2)   NOT NULL,
    tax_amount          NUMERIC(12,2)   NOT NULL,
    penalty_amount      NUMERIC(12,2)   NOT NULL DEFAULT 0,
    total_amount        NUMERIC(12,2)   NOT NULL,
    amount_paid         NUMERIC(12,2)   NOT NULL DEFAULT 0,
    outstanding_balance NUMERIC(12,2)   NOT NULL,
    due_date            DATE            NOT NULL,
    bill_status         VARCHAR(20)     NOT NULL DEFAULT 'UNPAID'
                        CHECK (bill_status IN ('UNPAID','APPROVED','PARTIALLY_PAID','PAID','OVERDUE','CANCELLED')),
    approved_at         TIMESTAMPTZ,
    approved_by         VARCHAR(100),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_bills_meter_period UNIQUE (meter_id, billing_year, billing_month)
);

CREATE TABLE payments (
    id              BIGSERIAL       PRIMARY KEY,
    bill_id         BIGINT          NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
    amount_paid     NUMERIC(12,2)   NOT NULL CHECK (amount_paid > 0),
    payment_method  VARCHAR(30)     NOT NULL,
    payment_date    DATE            NOT NULL,
    reference       VARCHAR(50),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- NOTIFICATIONS, AUDIT, FILES
-- -----------------------------------------------------------------------------

CREATE TABLE notifications (
    id                  BIGSERIAL   PRIMARY KEY,
    customer_id         BIGINT      NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    bill_id             BIGINT      REFERENCES bills(id) ON DELETE SET NULL,
    message             TEXT        NOT NULL,
    notification_type   VARCHAR(30) NOT NULL,
    read_flag           BOOLEAN     NOT NULL DEFAULT FALSE,
    email_sent          BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id              BIGSERIAL       PRIMARY KEY,
    entity_name     VARCHAR(100)    NOT NULL,
    entity_id       BIGINT          NOT NULL,
    action          VARCHAR(30)     NOT NULL,
    performed_by    VARCHAR(100),
    details         TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE uploaded_files (
    id              BIGSERIAL       PRIMARY KEY,
    entity_type     VARCHAR(50)     NOT NULL,
    entity_id       BIGINT          NOT NULL,
    original_name   VARCHAR(255)    NOT NULL,
    stored_name     VARCHAR(255)    NOT NULL,
    content_type    VARCHAR(100),
    file_size       BIGINT          NOT NULL,
    file_path       VARCHAR(500)    NOT NULL,
    uploaded_by     VARCHAR(100),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- =============================================================================
-- DATABASE TRIGGERS & STORED PROCEDURES (V8, updated V12)
-- =============================================================================

-- FUNCTION: format billing period label
-- CREATE FUNCTION fn_format_billing_period(p_year INT, p_month INT) RETURNS TEXT ...

-- TRIGGER: after bill INSERT → notification (no duplicate per bill)
-- CREATE TRIGGER trg_bill_generated_notification
--     AFTER INSERT ON bills
--     FOR EACH ROW EXECUTE FUNCTION fn_notify_bill_generated();

-- PROCEDURE: record payment, update bill status, payment notification
-- CALL sp_record_payment(bill_id, amount, method, date, OUT payment_id);
