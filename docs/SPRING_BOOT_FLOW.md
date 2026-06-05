# Utility Billing System — Spring Boot Flow Diagram

```mermaid
flowchart TB
    subgraph Client
        SW[Swagger UI / Postman]
    end

    subgraph Security
        JWT[JwtAuthenticationFilter]
        SEC[SecurityConfig]
        RBAC["@PreAuthorize RBAC"]
    end

    subgraph Controllers
        AUTH[AuthController]
        USER[UserController]
        CUST[CustomerController]
        METER[MeterController]
        READ[MeterReadingController]
        TAR[TariffController]
        BILL[BillController]
        PAY[PaymentController]
        NOTIF[NotificationController]
        FILE[FileController]
    end

    subgraph Services
        AS[AuthService]
        US[UserService]
        CS[CustomerService]
        MS[MeterService]
        RS[MeterReadingService]
        TS[TariffService]
        BS[BillService]
        PS[PaymentService]
        NS[NotificationService]
        ES[EmailService]
        AUD[AuditService]
        FS[FileStorageService]
    end

    subgraph Persistence
        REPO[(Spring Data JPA Repositories)]
        EM[EntityManager / Stored Procedures]
        PG[(PostgreSQL + Flyway)]
        TRG[DB Triggers]
    end

    SW --> JWT
    JWT --> SEC
    SEC --> RBAC
    RBAC --> Controllers

    AUTH --> AS
    USER --> US
    CUST --> CS
    METER --> MS
    READ --> RS
    TAR --> TS
    BILL --> BS
    PAY --> PS
    NOTIF --> NS
    FILE --> FS

    AS --> ES
    AS --> REPO
    BS --> TS
    BS --> REPO
    PS --> EM
    NS --> ES
    NS --> REPO

    CS --> AUD
    MS --> AUD
    RS --> AUD
    TS --> AUD
    BS --> AUD
    PS --> AUD

    REPO --> PG
    EM --> PG
    BS --> TRG
    PS --> TRG
    TRG --> PG
```

## Request lifecycle

1. **Authentication** — `POST /api/v1/auth/register|login` returns JWT access + refresh tokens.
2. **Authorization** — Every protected request passes through `JwtAuthenticationFilter` (validates signature, expiry, blacklist, token type).
3. **Validation** — DTOs validated with Jakarta Bean Validation; errors handled by `GlobalExceptionHandler`.
4. **Business logic** — Service layer enforces domain rules (readings, tariffs, inactive customers, payments).
5. **Persistence** — JPA repositories persist entities; payments call `sp_record_payment`; bill insert fires notification trigger.
6. **Messaging** — `EmailService` sends HTML emails asynchronously; `NotificationService` dispatches pending notification emails.
7. **Audit** — `AuditService` records create/update/delete actions in `audit_logs`.

## Role matrix

| Role | Capabilities |
|------|----------------|
| ADMIN | Users, tariffs, customers, meters, bill approval |
| OPERATOR | Capture meter readings |
| FINANCE | Approve bills, record payments |
| CUSTOMER | View own bills, payments, notifications |
