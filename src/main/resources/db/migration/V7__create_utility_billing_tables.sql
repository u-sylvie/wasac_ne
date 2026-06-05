-- =============================================================================
-- V7 — Utility Billing core tables
-- =============================================================================

CREATE TABLE IF NOT EXISTS customers
(
    id           BIGSERIAL       NOT NULL,
    full_name    VARCHAR(150)    NOT NULL,
    national_id  VARCHAR(20)     NOT NULL,
    email        VARCHAR(254)    NOT NULL,
    phone        VARCHAR(30)     NOT NULL,
    address      VARCHAR(255)    NOT NULL,
    user_id      BIGINT,
    created_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    deleted      BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at   TIMESTAMPTZ,
    deleted_by   VARCHAR(100),
    status       VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uq_customers_national_id UNIQUE (national_id),
    CONSTRAINT uq_customers_email UNIQUE (email),
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'PENDING'))
);

CREATE INDEX IF NOT EXISTS idx_customers_national_id ON customers (national_id);
CREATE INDEX IF NOT EXISTS idx_customers_email ON customers (email);
CREATE INDEX IF NOT EXISTS idx_customers_status ON customers (status);

CREATE TABLE IF NOT EXISTS meters
(
    id                BIGSERIAL       NOT NULL,
    customer_id       BIGINT          NOT NULL,
    meter_number      VARCHAR(50)     NOT NULL,
    meter_type        VARCHAR(20)     NOT NULL,
    installation_date DATE            NOT NULL,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    deleted           BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMPTZ,
    deleted_by        VARCHAR(100),
    status            VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_meters PRIMARY KEY (id),
    CONSTRAINT uq_meters_meter_number UNIQUE (meter_number),
    CONSTRAINT fk_meters_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT chk_meters_type CHECK (meter_type IN ('WATER', 'ELECTRICITY')),
    CONSTRAINT chk_meters_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'PENDING'))
);

CREATE INDEX IF NOT EXISTS idx_meters_customer_id ON meters (customer_id);
CREATE INDEX IF NOT EXISTS idx_meters_meter_number ON meters (meter_number);

CREATE TABLE IF NOT EXISTS meter_readings
(
    id                BIGSERIAL       NOT NULL,
    meter_id          BIGINT          NOT NULL,
    previous_reading  NUMERIC(12, 3)  NOT NULL,
    current_reading   NUMERIC(12, 3)  NOT NULL,
    reading_date      DATE            NOT NULL,
    billing_year      INT             NOT NULL,
    billing_month     INT             NOT NULL,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    deleted           BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMPTZ,
    deleted_by        VARCHAR(100),
    status            VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_meter_readings PRIMARY KEY (id),
    CONSTRAINT fk_meter_readings_meter FOREIGN KEY (meter_id) REFERENCES meters (id) ON DELETE CASCADE,
    CONSTRAINT uq_meter_readings_period UNIQUE (meter_id, billing_year, billing_month),
    CONSTRAINT chk_meter_readings_order CHECK (current_reading > previous_reading),
    CONSTRAINT chk_meter_readings_month CHECK (billing_month BETWEEN 1 AND 12)
);

CREATE INDEX IF NOT EXISTS idx_meter_readings_meter_id ON meter_readings (meter_id);
CREATE INDEX IF NOT EXISTS idx_meter_readings_period ON meter_readings (billing_year, billing_month);

CREATE TABLE IF NOT EXISTS tariff_configs
(
    id                    BIGSERIAL       NOT NULL,
    name                  VARCHAR(100)    NOT NULL,
    meter_type            VARCHAR(20)     NOT NULL,
    tariff_type           VARCHAR(20)     NOT NULL,
    rate_per_unit         NUMERIC(12, 4),
    fixed_service_charge  NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    vat_percentage        NUMERIC(5, 2)   NOT NULL DEFAULT 18,
    penalty_percentage    NUMERIC(5, 2)   NOT NULL DEFAULT 0,
    version               INT             NOT NULL,
    effective_from        DATE            NOT NULL,
    active                BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(100),
    updated_by            VARCHAR(100),
    deleted               BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at            TIMESTAMPTZ,
    deleted_by            VARCHAR(100),
    status                VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_tariff_configs PRIMARY KEY (id),
    CONSTRAINT uq_tariff_version UNIQUE (meter_type, version),
    CONSTRAINT chk_tariff_meter_type CHECK (meter_type IN ('WATER', 'ELECTRICITY')),
    CONSTRAINT chk_tariff_type CHECK (tariff_type IN ('FLAT', 'TIERED'))
);

CREATE TABLE IF NOT EXISTS tariff_tiers
(
    id               BIGSERIAL       NOT NULL,
    tariff_config_id BIGINT          NOT NULL,
    from_units       NUMERIC(12, 3)  NOT NULL,
    to_units         NUMERIC(12, 3),
    rate_per_unit    NUMERIC(12, 4)  NOT NULL,
    CONSTRAINT pk_tariff_tiers PRIMARY KEY (id),
    CONSTRAINT fk_tariff_tiers_config FOREIGN KEY (tariff_config_id) REFERENCES tariff_configs (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bills
(
    id                  BIGSERIAL       NOT NULL,
    reference           VARCHAR(50)     NOT NULL,
    customer_id         BIGINT          NOT NULL,
    meter_id            BIGINT          NOT NULL,
    meter_reading_id    BIGINT          NOT NULL,
    tariff_config_id    BIGINT          NOT NULL,
    billing_year        INT             NOT NULL,
    billing_month       INT             NOT NULL,
    consumption         NUMERIC(12, 3)  NOT NULL,
    consumption_charge  NUMERIC(12, 2)  NOT NULL,
    fixed_charge        NUMERIC(12, 2)  NOT NULL,
    tax_amount          NUMERIC(12, 2)  NOT NULL,
    penalty_amount      NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    total_amount        NUMERIC(12, 2)  NOT NULL,
    amount_paid         NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    outstanding_balance NUMERIC(12, 2)  NOT NULL,
    due_date            DATE            NOT NULL,
    bill_status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    approved_at         TIMESTAMPTZ,
    approved_by         VARCHAR(100),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMPTZ,
    deleted_by          VARCHAR(100),
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_bills PRIMARY KEY (id),
    CONSTRAINT uq_bills_reference UNIQUE (reference),
    CONSTRAINT fk_bills_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_bills_meter FOREIGN KEY (meter_id) REFERENCES meters (id),
    CONSTRAINT fk_bills_reading FOREIGN KEY (meter_reading_id) REFERENCES meter_readings (id),
    CONSTRAINT fk_bills_tariff FOREIGN KEY (tariff_config_id) REFERENCES tariff_configs (id),
    CONSTRAINT chk_bills_status CHECK (bill_status IN ('PENDING', 'APPROVED', 'PAID', 'OVERDUE', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_bills_customer_id ON bills (customer_id);
CREATE INDEX IF NOT EXISTS idx_bills_reference ON bills (reference);
CREATE INDEX IF NOT EXISTS idx_bills_status ON bills (bill_status);

CREATE TABLE IF NOT EXISTS payments
(
    id             BIGSERIAL       NOT NULL,
    bill_id        BIGINT          NOT NULL,
    amount_paid    NUMERIC(12, 2)  NOT NULL,
    payment_method VARCHAR(30)     NOT NULL,
    payment_date   DATE            NOT NULL,
    reference      VARCHAR(50),
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    deleted        BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at     TIMESTAMPTZ,
    deleted_by     VARCHAR(100),
    status         VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_payments_bill FOREIGN KEY (bill_id) REFERENCES bills (id) ON DELETE CASCADE,
    CONSTRAINT chk_payments_amount CHECK (amount_paid > 0)
);

CREATE INDEX IF NOT EXISTS idx_payments_bill_id ON payments (bill_id);

CREATE TABLE IF NOT EXISTS notifications
(
    id                BIGSERIAL       NOT NULL,
    customer_id       BIGINT          NOT NULL,
    bill_id           BIGINT,
    message           TEXT            NOT NULL,
    notification_type VARCHAR(30)     NOT NULL,
    read_flag         BOOLEAN         NOT NULL DEFAULT FALSE,
    email_sent        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_bill FOREIGN KEY (bill_id) REFERENCES bills (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_notifications_customer_id ON notifications (customer_id);

CREATE TABLE IF NOT EXISTS otp_tokens
(
    id         BIGSERIAL       NOT NULL,
    user_id    BIGINT          NOT NULL,
    code       VARCHAR(6)      NOT NULL,
    purpose    VARCHAR(30)     NOT NULL,
    expires_at TIMESTAMPTZ     NOT NULL,
    used       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_otp_tokens PRIMARY KEY (id),
    CONSTRAINT fk_otp_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_otp_tokens_user_id ON otp_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_otp_tokens_code ON otp_tokens (code);

CREATE TABLE IF NOT EXISTS revoked_tokens
(
    id         BIGSERIAL       NOT NULL,
    token_jti  VARCHAR(64)     NOT NULL,
    expires_at TIMESTAMPTZ     NOT NULL,
    revoked_at TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_revoked_tokens PRIMARY KEY (id),
    CONSTRAINT uq_revoked_tokens_jti UNIQUE (token_jti)
);

CREATE INDEX IF NOT EXISTS idx_revoked_tokens_jti ON revoked_tokens (token_jti);

CREATE TABLE IF NOT EXISTS audit_logs
(
    id           BIGSERIAL       NOT NULL,
    entity_name  VARCHAR(100)    NOT NULL,
    entity_id    BIGINT          NOT NULL,
    action       VARCHAR(30)     NOT NULL,
    performed_by VARCHAR(100),
    details      TEXT,
    created_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs (entity_name, entity_id);

CREATE TABLE IF NOT EXISTS uploaded_files
(
    id            BIGSERIAL       NOT NULL,
    entity_type   VARCHAR(50)     NOT NULL,
    entity_id     BIGINT          NOT NULL,
    original_name VARCHAR(255)    NOT NULL,
    stored_name   VARCHAR(255)    NOT NULL,
    content_type  VARCHAR(100),
    file_size     BIGINT          NOT NULL,
    file_path     VARCHAR(500)    NOT NULL,
    uploaded_by   VARCHAR(100),
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_uploaded_files PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_uploaded_files_entity ON uploaded_files (entity_type, entity_id);
