# WASAC Utility Billing — Entity Relationship Diagram

Relational database: **PostgreSQL**

## ERD source files (import into diagram tools)

| File | Use with |
|------|----------|
| [DB_SCHEMA.dbml](DB_SCHEMA.dbml) | [dbdiagram.io](https://dbdiagram.io) — paste file → instant ERD |
| [DB_SCHEMA.sql](DB_SCHEMA.sql) | DBeaver, pgModeler, draw.io, Lucidchart |
| This file (`ERD.md`) | Mermaid preview in GitHub / VS Code |

## National ID — primary customer identifier

Every customer **must** have a unique Rwanda National ID:

- **Format:** exactly **16 digits** (e.g. `119998877665544`)
- **DB:** `UNIQUE(national_id)` + `CHECK (national_id ~ '^[0-9]{16}$')` (V13)
- **API:** required on create; lookup via `GET /api/v1/customers/by-national-id/{nationalId}`
- **Duplicate rejection:** service layer + DB constraint (409 Conflict)

## Database triggers & procedures

| Object | Type | When it runs | Effect |
|--------|------|--------------|--------|
| `fn_format_billing_period` | FUNCTION | Called by trigger/SP | Formats "June 2026" label |
| `fn_notify_bill_generated` | FUNCTION | Called by trigger | Builds notification message |
| `trg_bill_generated_notification` | **TRIGGER** | `AFTER INSERT ON bills` | Inserts `BILL_GENERATED` notification (skips duplicate & missing email) |
| `sp_record_payment` | **STORED PROCEDURE** | Called from `PaymentService` | Inserts payment, updates balance, sets `PARTIALLY_PAID`/`PAID`, inserts `PAYMENT_COMPLETED` notification |

Defined in: `V8__create_billing_routines.sql` (updated in `V12__exam_validations_and_wasac_updates.sql`)

```mermaid
erDiagram
    USERS ||--o| CUSTOMERS : "linked account"
    USERS ||--o{ OTP_TOKENS : has
    USERS ||--o{ PASSWORD_RESET_TOKENS : has
    USERS ||--o{ EMAIL_VERIFICATION_TOKENS : has

    CUSTOMERS ||--o{ METERS : owns
    CUSTOMERS ||--o{ BILLS : receives
    CUSTOMERS ||--o{ NOTIFICATIONS : receives

    METERS ||--o{ METER_READINGS : records
    METERS ||--o{ BILLS : billed_on

    METER_READINGS ||--o| BILLS : generates

    TARIFF_CONFIGS ||--o{ TARIFF_TIERS : contains
    TARIFF_CONFIGS ||--o{ BILLS : priced_by

    BILLS ||--o{ PAYMENTS : paid_by
    BILLS ||--o{ NOTIFICATIONS : triggers

    USERS {
        bigint id PK
        varchar first_name
        varchar last_name
        varchar username UK
        varchar email UK
        varchar phone
        varchar password
        varchar role
        varchar status
        timestamptz created_at
        boolean deleted
    }

    CUSTOMERS {
        bigint id PK
        varchar full_name
        varchar national_id UK "16-digit Rwanda NID"
        varchar email UK
        varchar phone UK
        varchar address
        date date_of_birth
        bigint user_id FK
        varchar status
    }

    METERS {
        bigint id PK
        bigint customer_id FK
        varchar meter_number UK
        varchar meter_type
        date installation_date
        varchar status
    }

    METER_READINGS {
        bigint id PK
        bigint meter_id FK
        numeric previous_reading
        numeric current_reading
        date reading_date
        int billing_year
        int billing_month
    }

    TARIFF_CONFIGS {
        bigint id PK
        varchar name
        varchar meter_type
        varchar tariff_type
        numeric rate_per_unit
        numeric fixed_service_charge
        numeric vat_percentage
        numeric penalty_percentage
        int version
        date effective_from
        boolean active
    }

    TARIFF_TIERS {
        bigint id PK
        bigint tariff_config_id FK
        numeric from_units
        numeric to_units
        numeric rate_per_unit
    }

    BILLS {
        bigint id PK
        varchar reference UK
        bigint customer_id FK
        bigint meter_id FK
        bigint meter_reading_id FK
        bigint tariff_config_id FK
        int billing_year
        int billing_month
        numeric total_amount
        numeric outstanding_balance
        varchar bill_status
        date due_date
    }

    PAYMENTS {
        bigint id PK
        bigint bill_id FK
        numeric amount_paid
        varchar payment_method
        date payment_date
    }

    NOTIFICATIONS {
        bigint id PK
        bigint customer_id FK
        bigint bill_id FK
        text message
        varchar notification_type
        boolean read_flag
        boolean email_sent
    }

    OTP_TOKENS {
        bigint id PK
        bigint user_id FK
        varchar code
        varchar purpose
        timestamptz expires_at
        boolean used
    }

    REVOKED_TOKENS {
        bigint id PK
        varchar token_jti UK
        timestamptz expires_at
    }

    AUDIT_LOGS {
        bigint id PK
        varchar entity_name
        bigint entity_id
        varchar action
        varchar performed_by
        text details
    }

    UPLOADED_FILES {
        bigint id PK
        varchar entity_type
        bigint entity_id
        varchar file_path
    }
```

## Key constraints

| Rule | Implementation |
|------|----------------|
| Unique customer national ID (16 digits) | `UNIQUE` + `CHECK` on `customers.national_id` |
| Unique customer email / phone | `UNIQUE` on `customers` |
| One bill per meter per month/year | `UNIQUE (meter_id, billing_year, billing_month)` on `bills` |
| Unique meter number | `UNIQUE` on `meters` |
| One reading per meter per month/year | `UNIQUE (meter_id, billing_year, billing_month)` |
| Current reading > previous | `CHECK` on `meter_readings` |
| Versioned tariffs | `UNIQUE (meter_type, version)` |
| Bill notification on generation | Trigger `trg_bill_generated_notification` |
| Payment + full-pay notification | Stored procedure `sp_record_payment` |
