# WASAC Utility Billing System

A complete **Spring Boot** backend for national utility billing (water & electricity). Built for the Java Backend Developer exam — JWT security, customer/meter management, meter readings, versioned tariffs, bill generation, payments, database routines, email notifications, and Swagger API docs.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.4.5 |
| Language | Java 22 |
| Security | Spring Security + JWT (jjwt 0.12.6) |
| Database | PostgreSQL |
| ORM | Spring Data JPA + Hibernate |
| Migrations | Flyway |
| Mapping | MapStruct 1.6.3 |
| Validation | Jakarta Bean Validation |
| Email | Spring Mail (HTML templates) |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| DevTools | Spring Boot DevTools (hot restart) |

---

## Prerequisites

- Java 22+
- Maven 3.9+
- PostgreSQL 12+

---

## Quick Start

### 1. Create the database

```sql
CREATE DATABASE javat;
```

### 2. Configure credentials

Put real credentials in `src/main/resources/application-local.properties` (gitignored):

```properties
spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/javat
spring.datasource.username=postgres
spring.datasource.password=your_password

app.jwt.secret=your-base64-encoded-secret-min-32-bytes

spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
app.mail.from=noreply@yourdomain.com
app.mail.from-name=WASAC Utility Billing
app.mail.base-url=http://localhost:8080
```

Generate JWT secret:

```bash
openssl rand -base64 32
```

### 3. Run

```bash
mvn spring-boot:run
```

Flyway applies migrations **V1–V11** on startup. **Restart the app** after pulling code changes (a stale instance on port 8080 will not reflect new code).

### 4. Open Swagger UI

```
http://localhost:8080/swagger-ui.html
```

Click **Authorize** and paste: `Bearer <accessToken>`

---

## Design Documents (Exam Requirement)

| Document | Path |
|---|---|
| Entity Relationship Diagram | [docs/ERD.md](docs/ERD.md) |
| **DB schema for ERD tools** | [docs/DB_SCHEMA.dbml](docs/DB_SCHEMA.dbml) → import at [dbdiagram.io](https://dbdiagram.io) |
| PostgreSQL DDL reference | [docs/DB_SCHEMA.sql](docs/DB_SCHEMA.sql) |
| Spring Boot Flow Diagram | [docs/SPRING_BOOT_FLOW.md](docs/SPRING_BOOT_FLOW.md) |

---

## Default Test Accounts

All seeded accounts use password **`Admin@1234`**:

| Email | Role | Purpose |
|---|---|---|
| `admin@javat.com` | ADMIN | Tariffs, users, bill approval |
| `operator@wasac.rw` | OPERATOR | Capture meter readings |
| `finance@wasac.rw` | FINANCE | Approve bills, record payments |
| `customer@wasac.rw` | CUSTOMER | View own bills & payments |

Sample data (V9): customer **Eric Customer**, water meter `WTR-0001`, electricity meter `ELC-0001`, flat tariffs for water & electricity.

---

## End-to-End Test Flow (Swagger / Postman)

1. **Login** as `operator@wasac.rw` → `POST /api/v1/meter-readings`
2. **Login** as `admin@javat.com` → `POST /api/v1/bills/generate`
3. **Approve bill** → `PATCH /api/v1/bills/{id}/approve`
4. **Login** as `finance@wasac.rw` → `POST /api/v1/payments`
5. **Check notifications** → `GET /api/v1/notifications`
6. **Dispatch emails** → `POST /api/v1/notifications/send-pending-emails`
7. **Customer view** → login as `customer@wasac.rw` → `GET /api/v1/bills/me`

---

## API Overview (`/api/v1/`)

### Authentication (public except logout)

| Method | Path | Description |
|---|---|---|
| POST | `/auth/register` | Register + welcome email |
| POST | `/auth/login` | Login → access + refresh tokens |
| POST | `/auth/refresh` | Refresh token pair |
| POST | `/auth/logout` | Blacklist current access token |
| GET | `/auth/verify-email?token=` | Email link verification |
| POST | `/auth/send-otp` | Send 6-digit OTP |
| POST | `/auth/verify-otp` | Verify OTP |
| POST | `/auth/forgot-password` | Reset link + OTP |
| POST | `/auth/reset-password` | Complete password reset |

### Utility Billing

| Module | Path | Roles |
|---|---|---|
| Customers | `/customers`, `/customers/by-national-id/{nid}` | ADMIN (create), ADMIN/FINANCE/OPERATOR (lookup) |
| Meters | `/meters` | ADMIN |
| Meter Readings | `/meter-readings` | OPERATOR (create) |
| Tariffs | `/tariffs` | ADMIN |
| Bills | `/bills`, `/bills/me` | ADMIN, FINANCE, CUSTOMER |
| Payments | `/payments`, `/payments/me` | FINANCE, CUSTOMER |
| Notifications | `/notifications`, `/notifications/me` | ADMIN, FINANCE, CUSTOMER |
| Files | `/files` | Authenticated |
| Audit Logs | `/audit-logs` | ADMIN, FINANCE |
| Users | `/users` | ADMIN + self-service `/me` |

---

## Business Rules

| Rule | Enforcement |
|---|---|
| Current reading > previous | `MeterReadingService` + DB CHECK |
| One reading per meter/month/year | DB UNIQUE + service validation |
| Active meter required | `MeterReadingService` |
| Unique National ID (16 digits) | `ValidRwandaNationalId` + DB UNIQUE + CHECK |
| No duplicate customers | UNIQUE national_id / email / phone |
| Lookup customer by NID | `GET /customers/by-national-id/{nationalId}` |
| Inactive customers cannot get bills | `BillService` |
| Versioned tariffs | `TariffService` — new version for future cycles |
| Partial/full payments | `sp_record_payment` stored procedure |
| Bill PAID when balance = 0 | Stored procedure |
| Bill notification on generation | PostgreSQL trigger |
| Payment notification on full pay | PostgreSQL stored procedure |

---

## Database Migrations

| Version | Description |
|---|---|
| V1 | Users table |
| V2 | Seed admin user |
| V3–V4 | Password reset & email verification tokens |
| V5 | Update admin password |
| V6 | Add phone + utility roles |
| V7 | Billing tables (customers, meters, bills, payments, …) |
| V8 | Triggers & `sp_record_payment` |
| V9 | Sample tariffs, users, customer, meters |
| V10 | Sync seeded user passwords |
| V11 | Fix tariff effective dates for billing |
| V12 | Exam validations, bill statuses, triggers/SP updates |
| V13 | National ID format constraint (16 digits) |

---

## Project Structure

```
src/main/java/com/spring/JavaT/
├── auth/           # JWT, OTP, registration, password reset
├── user/           # User management & roles
├── customer/       # Customer CRUD
├── meter/          # Meter management
├── meterreading/   # Operator meter readings
├── tariff/         # Versioned tariff configuration
├── bill/           # Bill generation & approval
├── payment/        # Payment recording (stored procedure)
├── notification/   # In-app + email notifications
├── file/           # Document/profile upload
├── audit/          # Audit log trail
├── security/       # JWT filter, config
├── config/         # Security, JPA, Swagger, async, files
├── common/         # DTOs, validation, pagination, enums
└── exception/      # Global exception handler

src/main/resources/
├── db/migration/   # Flyway SQL
└── templates/email/  # HTML email templates
```

---

## Features Checklist (Exam Guide)

- [x] JWT authentication + refresh tokens + logout blacklist
- [x] Role-based access (ADMIN, OPERATOR, FINANCE, CUSTOMER)
- [x] BCrypt password encryption
- [x] Email verification (link + OTP)
- [x] Password recovery (link + OTP)
- [x] DTO validation + global exception handling
- [x] Full CRUD on all entities
- [x] Pagination, sorting, search
- [x] Audit fields + audit log API
- [x] Swagger/OpenAPI documentation
- [x] Structured logging (`@Slf4j`)
- [x] HTML email templates (welcome, OTP, bill, payment, …)
- [x] File upload
- [x] Layered architecture (Controller → Service → Repository)
- [x] Database routines (trigger + stored procedure)

---

## Sample Request Bodies (Swagger / Postman)

**Register**
```json
{
  "firstName": "Marie",
  "lastName": "Uwera",
  "username": "marieuwera",
  "email": "marie@example.com",
  "phone": "+250788999888",
  "password": "Secret@123"
}
```

**Meter reading (OPERATOR)**
```json
{
  "meterId": 1,
  "previousReading": 265,
  "currentReading": 310,
  "readingDate": "2026-07-01",
  "billingYear": 2026,
  "billingMonth": 7
}
```

**Create customer (ADMIN) — National ID required**
```json
{
  "fullName": "Marie Uwera",
  "nationalId": "119998877665544",
  "email": "marie@example.com",
  "phone": "0788123456",
  "address": "Kigali, Nyarugenge",
  "dateOfBirth": "1995-03-15"
}
```

**Look up customer by National ID**
```
GET /api/v1/customers/by-national-id/119998877665544
```

**Generate bill (ADMIN / FINANCE)**
```json
{ "meterReadingId": 2 }
```

**Record payment (FINANCE)**
```json
{
  "billId": 1,
  "amountPaid": 15000.00,
  "paymentMethod": "MOMO",
  "paymentDate": "2026-07-05"
}
```

**Tariff (ADMIN) — flat water**
```json
{
  "name": "Water Flat v2",
  "meterType": "WATER",
  "tariffType": "FLAT",
  "ratePerUnit": 400,
  "fixedServiceCharge": 2000,
  "vatPercentage": 18,
  "penaltyPercentage": 5,
  "effectiveFrom": "2026-08-01"
}
```

---

## License

MIT — use freely for personal and commercial projects.
