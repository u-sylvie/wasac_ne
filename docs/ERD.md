# Utility Billing System — Entity Relationship Diagram

Relational database: **PostgreSQL**

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
        varchar national_id UK
        varchar email UK
        varchar phone
        varchar address
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
| Unique customer national ID / email | `UNIQUE` on `customers` |
| Unique meter number | `UNIQUE` on `meters` |
| One reading per meter per month/year | `UNIQUE (meter_id, billing_year, billing_month)` |
| Current reading > previous | `CHECK` on `meter_readings` |
| Versioned tariffs | `UNIQUE (meter_type, version)` |
| Bill notification on generation | Trigger `trg_bill_generated_notification` |
| Payment + full-pay notification | Stored procedure `sp_record_payment` |
