# Surense — Customer Support Hub

REST API for a small CRM / customer-support backend: **three roles** (`ADMIN`, `AGENT`, `CUSTOMER`), **JWT** access tokens with **opaque refresh-token** rotation persisted in MySQL, and role-aware **customers** and **tickets** endpoints under `/api/v1`.

This repository was built as a **take-home assignment**: correctness, security boundaries, and clear tradeoffs matter more than feature breadth.

---

## Tech stack

| Area | Choice |
|------|--------|
| Runtime | **Java 21** (Eclipse Temurin) |
| Framework | **Spring Boot 3.5** — Web, Validation, Data JPA, Security, Actuator |
| Database | **MySQL 8** (`utf8mb4`), **Flyway** migrations |
| Build | **Maven** (wrapper: `mvnw` / `mvnw.cmd`) |
| Tests | **JUnit 5**, **Mockito**, **Spring Security Test**; **H2** (MySQL mode) for integration tests |
| Tokens | **JJWT** — HS256 JWTs + opaque refresh rows |

### Authentication model (assignment vs. implementation)

The brief mentions **Spring OAuth**. This service uses **Spring Security** with **`POST /api/v1/auth/login`** (username/password), returns a **Bearer JWT** for API calls, and **`POST /api/v1/auth/refresh`** with an opaque refresh token (stored in DB, rotated). That matches common **resource-server + refresh** patterns without shipping a full **OAuth 2.0 Authorization Server** (e.g. Spring Authorization Server), which would be heavier than needed for the exercise.

---

## Prerequisites

- **JDK 21** on `PATH`
- **Docker** (for MySQL locally; optional for running the built API image)
- **Port 3306** free if you use the bundled MySQL Compose service  
- **Port 8080** free for the API

Verified on Windows 10/11 with PowerShell; paths below use `.\mvnw.cmd` — use `./mvnw` on macOS/Linux.

---

## Getting started

### Option A — MySQL in Docker, API on the host (best for debugging)

```bash
docker compose up -d mysql
docker compose ps    # wait until mysql is "healthy"
./mvnw spring-boot:run    # Windows: .\mvnw.cmd spring-boot:run
curl http://localhost:8080/actuator/health
```

### Option B — MySQL and API both in Docker

```bash
docker compose up -d --build
```

Starts **`mysql`** (health-checked), then the **`app`** service on **8080**, using the **`Dockerfile`** image.

Stop containers when done:

```bash
docker compose down          # keeps the MySQL volume
docker compose down -v         # removes volume (wipes DB data)
```

### Customer login ↔ CRM record (`customers.user_id`)

Self-service users log in as **`CUSTOMER`** (`users` role). To **open tickets**, that account must be linked to **exactly one** CRM row via `customers.user_id → users.id`.

Staff can set the link when creating or updating a CRM customer:

| When | JSON field | Who may set it |
|------|------------|----------------|
| `POST /api/v1/customers` | `linkedCustomerUsername` (optional) | **ADMIN**, **AGENT** |
| `PATCH /api/v1/customers/{id}` | `linkedCustomerUsername` (optional) | **ADMIN**, owning **AGENT** — not on self-service **CUSTOMER** PATCH |

Value is an existing **`users.username`** whose role is **`CUSTOMER`**. If that login is already linked to another CRM row, the API returns **409** with code **`CUSTOMER_LOGIN_ALREADY_LINKED`**. Create the user account first (your provisioning flow), then pass **`linkedCustomerUsername`**.

### Run only the API image (against any MySQL)

The repo **`Dockerfile`** is multi-stage (Maven build + JRE runtime). Example against Docker Desktop’s host DB:

```bash
docker build -t surense:dev .

docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL='jdbc:mysql://host.docker.internal:3306/surense?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC' \
  -e SPRING_DATASOURCE_USERNAME=surense \
  -e SPRING_DATASOURCE_PASSWORD=surense-dev-password \
  surense:dev
```

Adjust JDBC URL/credentials for your environment (Linux hosts often use compose networking or an explicit DB hostname instead of `host.docker.internal`).

### Production-style JSON logs (local)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

---

## Configuration

Base settings live in `application.yml` (defaults assume Compose MySQL on localhost).

| Profile | File | Purpose |
|---------|------|---------|
| `dev` | `src/main/resources/application-dev.yml` | Local development (default), text logs |
| `test` | `src/test/resources/application-test.yml` | Tests against H2 |
| `prod` | Env-driven | Structured JSON logs |

Override via environment variables (e.g. `SPRING_DATASOURCE_PASSWORD`, `SURENSE_JWT_SECRET`).

| Property | Default | Purpose |
|----------|---------|---------|
| `surense.rate-limit.enabled` | `true` / `false` in `test` | Servlet IP + user rate limiters |
| `surense.rate-limit.cache-max-entries` | `100000` | Caffeine bucket store cap |
| `surense.rate-limit.cache-expire-after-access` | `PT1H` | Idle bucket eviction |
| `surense.rate-limit.exempt-paths` | `/actuator/health`, `/actuator/health/**` | Paths skipped by IP limiter |
| `surense.rate-limit.unauth-ip.{capacity,refill}` | `30` / `PT1M` | Anonymous requests per client IP |
| `surense.rate-limit.auth-user.{capacity,refill}` | `60` / `PT1M` | Authenticated requests per user id |
| `surense.rate-limit.login.{capacity,refill}` | `5` / `PT15M` | Failed login attempts |
| `surense.rate-limit.refresh.{capacity,refill}` | `10` / `PT1M` | Refresh calls per hashed refresh token |
| `surense.auth.jwt-secret` | 256-bit hex in YAML | HS256 key — override in production |
| `surense.auth.issuer` | `surense` | JWT `iss` |
| `surense.auth.access-token-ttl` | `PT15M` | Access JWT TTL |
| `surense.auth.refresh-token-ttl` | `P7D` | Refresh token row lifetime |

For local Docker MySQL, Flyway **`V3`** seeds an **`admin`** user (password **`AdminChangeMe1!`** — dev only). See [Database](#database).

---

## API summary

### Endpoints

| Method & Path | Typical statuses | Notes |
|---------------|------------------|-------|
| `GET /actuator/health` | 200 | Liveness/readiness |
| `POST /api/v1/auth/login` | 200, 401, 429 | Body `{ username, password }` → access + refresh tokens |
| `POST /api/v1/auth/refresh` | 200, 401, 429 | Body `{ refreshToken }` — rotates refresh row |
| `POST /api/v1/auth/logout` | 204, 401 | Body `{ refreshToken }` — revokes session |
| `GET /api/v1/customers` | 200, 401, 403 | **ADMIN**: all; **AGENT**: created by self; **CUSTOMER**: linked row only |
| `GET /api/v1/customers/{id}` | 200, 403, 404 | Same visibility rules |
| `POST /api/v1/customers` | 201, …, 409 | **ADMIN**, **AGENT** — `{ email, displayName, phoneNumber?, linkedCustomerUsername? }` |
| `PATCH /api/v1/customers/{id}` | 200, …, 409 | **ADMIN** (any); **AGENT** (own customers); **CUSTOMER** (linked row). Optional `linkedCustomerUsername` only for **ADMIN** / owning **AGENT** |
| `GET /api/v1/tickets` | 200, 401, 403 | Query `customerId`, `status` — scoped by role |
| `GET /api/v1/tickets/{id}` | 200, 403, 404 | Same read rules |
| `POST /api/v1/tickets` | 201, …, 404 | **ADMIN**: `{ customerId, subject, body? }`; **CUSTOMER**: `{ subject, body? }` (CRM row from login; **403** if unlinked) |
| `PATCH /api/v1/tickets/{id}` | 200, … | **ADMIN**, **AGENT** — `{ subject?, body?, status?, assignedToAgentId? }` |

Unified error JSON: stable **`code`**, localized **`message`**, optional **`fieldErrors`**, **`correlationId`**. See [Error handling](#error-handling).

---

## Authentication examples

Assume **`http://localhost:8080`** and seeded **`admin`** / **`AdminChangeMe1!`** when using Flyway against Docker MySQL.

### Login

Returns `accessToken`, `refreshToken`, `tokenType`, `expiresInSeconds`.

```bash
curl -sS -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"AdminChangeMe1!"}'
```

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" `
  -Method POST -ContentType "application/json; charset=utf-8" `
  -Body '{"username":"admin","password":"AdminChangeMe1!"}'
```

PowerShell may truncate token lines in table output — use `$r.accessToken`, `$r.refreshToken`, or `$r | ConvertTo-Json -Depth 5`.

### Refresh

No `Authorization` header. Body must be JSON (hashtables need `| ConvertTo-Json`).

```bash
curl -sS -X POST "http://localhost:8080/api/v1/auth/refresh" \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"PASTE_REFRESH_TOKEN"}'
```

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/refresh" `
  -Method POST -ContentType "application/json; charset=utf-8" `
  -Body '{"refreshToken":"PASTE_REFRESH_TOKEN"}'
```

### Logout

Response **204** with empty body.

```bash
curl -sS -X POST "http://localhost:8080/api/v1/auth/logout" \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"PASTE_REFRESH_TOKEN"}'
```

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/logout" `
  -Method POST -ContentType "application/json; charset=utf-8" `
  -Body '{"refreshToken":"PASTE_REFRESH_TOKEN"}'
```

### Call CRM with the access JWT

```bash
curl -sS "http://localhost:8080/api/v1/customers" \
  -H "Authorization: Bearer PASTE_ACCESS_JWT"
```

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/customers" `
  -Headers @{ Authorization = "Bearer PASTE_ACCESS_JWT" }
```

On Windows PowerShell, prefer **`curl.exe`** for Unix-style `curl` flags; bare **`curl`** aliases **`Invoke-WebRequest`**.

---

## Architecture

### Package layout

```
com.surense
├── CustomerSupportHubApplication.java
├── api/                       ← REST controllers & request/response DTOs
│   ├── auth/
│   ├── customers/
│   └── tickets/
├── service/                   ← Domain services (auth, customers, tickets)
└── infra/
    ├── config/                ← SecurityFilterChain, rate-limit filter beans
    ├── security/              ← JWT filter, token service, 401 JSON entry point
    ├── error/                 ← GlobalExceptionHandler, ErrorCode, ApiException types
    ├── i18n/
    ├── logging/               ← Correlation ID filter, log masking
    ├── ratelimit/             ← Bucket4j + servlet filters
    └── persistence/           ← JPA entities & Spring Data repositories
        ├── auth/
        ├── customers/
        └── tickets/
```

### Database

Flyway scripts: `src/main/resources/db/migration/`.

| Migration | Contents |
|-----------|----------|
| `V1` | `users`, `refresh_tokens` |
| `V2` | `customers`, `tickets` |
| `V3` | Dev seed: user **`admin`** / role **`ADMIN`** (BCrypt in SQL — **local dev only**; rotate for shared environments) |

The **`test`** profile disables Flyway and lets Hibernate create an H2 schema so tests stay deterministic without the SQL seed.

---

## Error handling

Example body shape:

```json
{
  "timestamp": "2026-05-04T08:30:01.234Z",
  "status": 404,
  "error": "Not Found",
  "code": "CUSTOMER_NOT_FOUND",
  "message": "Customer with id 42 was not found",
  "path": "/api/v1/customers/42",
  "correlationId": "8c9b7a6e-1234-4567-89ab-cdef01234567",
  "fieldErrors": [
    { "field": "email", "message": "must be a well-formed email address" }
  ]
```

- Branch on **`code`**, not **`message`**.
- **`message`** is resolved from `messages.properties` (i18n-ready).
- **`correlationId`** matches **`X-Correlation-Id`** on the response and appears in logs.
- **`fieldErrors`** only on validation failures; **`rejectedValue`** is omitted (PII).

| Source | HTTP | `code` |
|--------|------|--------|
| `ApiException` subclasses | per enum | per `ErrorCode` |
| Rate limit | 429 | `RATE_LIMITED` + `Retry-After` |
| `AccessDeniedException` | 403 | `FORBIDDEN` |
| `BadCredentialsException` | 401 | `BAD_CREDENTIALS` |
| Bean validation | 400 | `VALIDATION_FAILED` |
| Malformed JSON | 400 | `MALFORMED_REQUEST` |
| Uncaught exception | 500 | `INTERNAL_ERROR` (generic message to client; stack trace server-side only) |

---

## Logging and correlation IDs

`CorrelationIdFilter` runs early (before Spring Security): reads or generates **`X-Correlation-Id`**, puts it on SLF4J MDC, echoes it on the response, clears MDC after the request.

| Profile | Format |
|---------|--------|
| `dev` / default / `test` | Colored text (`MaskingPatternLayout`) |
| `prod` | One JSON object per line (`MaskingLogstashEncoder`) |

Dev log line example:

```
2026-05-04 10:38:21.123  INFO ... [corr=8c9b7a6e,user=42,role=AGENT] c.s.api.tickets.TicketController : ...
```

---

## PII and logs

1. Error payloads reference resources **by id**, not raw PII.
2. Validation **`fieldErrors`** omit **`rejectedValue`**.
3. **`LogMasker`** scrubs passwords, tokens, emails, etc., from log output.
4. **500** responses never echo internal exception text.

---

## Rate limiting

In-process **Bucket4j** token buckets with a **size-capped Caffeine** store (`application.yml`).

- **`IpRateLimitFilter`** — before security: limits anonymous traffic by IP (skipped for exempt paths and when a non-blank `Authorization` header is present — JWT traffic is handled by the user limiter).
- **`UserRateLimitFilter`** — after security: limits by authenticated user id.

| Limiter | Key | Default |
|---------|-----|---------|
| Unauthenticated | Client IP (`X-Forwarded-For` first hop or remote addr) | 30/min |
| Authenticated | `userId` | 60/min |
| Failed login | username + IP | 5 / 15 min |
| Refresh body | hash of refresh token | 10/min |

**429** responses use the same JSON envelope as other errors plus **`Retry-After`** (seconds).

**Tests:** `surense.rate-limit.enabled=false` under `test` by default; opt-in integration tests set `surense.rate-limit.enabled=true` (see `UnauthenticatedIpRateLimitIntegrationTest`).

**Deployment note:** trusting **`X-Forwarded-For`** assumes a reverse proxy you control; otherwise IPs can be spoofed.

**Known edge:** a junk **`Authorization`** header can skip the IP bucket while still unauthenticated; tightening would mean validating the bearer earlier or re-applying IP limits — left as a future hardening item.

---

## Localization

Messages load from `src/main/resources/messages.properties`. Add **`messages_he.properties`** (or other locales) beside it; clients send **`Accept-Language`**. `MessageResolver` falls back to English.

---

## Hebrew and Unicode

- MySQL uses **`utf8mb4`** / **`utf8mb4_unicode_ci`** (via Compose command and JDBC URL).
- Validators use **`@NotBlank`** / **`@Size`** — no ASCII-only regex blocking non-Latin text.
- RTL is a client concern.

---

## Development

### Maven vs .NET (mental map)

| .NET | Maven (this repo) |
|------|-------------------|
| `dotnet restore` | Dependencies resolved automatically |
| `dotnet clean` | `.\mvnw.cmd clean` |
| `dotnet build` | `.\mvnw.cmd compile` |
| `dotnet test` | `.\mvnw.cmd test` |
| `dotnet run` | `.\mvnw.cmd spring-boot:run` |
| `dotnet publish` | `.\mvnw.cmd package` → fat JAR in `target\` |

### Smoke-test curls (errors)

With the app running:

```powershell
curl.exe http://localhost:8080/actuator/health
curl.exe -i http://localhost:8080/api/v1/customers
curl.exe -i -X POST -H "Content-Type: application/json" `
  --data-raw '{\"username\":\"\",\"password\":\"x\"}' `
  http://localhost:8080/api/v1/auth/login
```

Broader status-code coverage lives in **`src/test/java`** (`GlobalExceptionHandlerTest`, `AuthControllerIntegrationTest`, `CrmApiIntegrationTest`, `UnauthenticatedIpRateLimitIntegrationTest`).

### Tests

No Docker required — H2 in-memory.

```powershell
.\mvnw.cmd test
.\mvnw.cmd test "-Dtest=CrmApiIntegrationTest"
.\mvnw.cmd -o test
```

Reports: `target\surefire-reports\`.

### Remote debug (JDWP)

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

Attach from your IDE on port **5005**.

---

## For reviewers

| Check | |
|-------|---|
| Build & test | `.\mvnw.cmd clean package` (or `.\mvnw.cmd test`) |
| Defaults | API **8080**, Compose MySQL **3306**, credentials in `application.yml` / Compose |
| Dev admin | Flyway **`V3`** only for local MySQL — change password before sharing |
| Secrets | Override **`SURENSE_JWT_SECRET`** and DB passwords in real deployments |
| Docker | **`docker compose up -d --build`** for full stack; **`Dockerfile`** for API-only image |

---

## Assignment deliverables (mapping)

| Requirement | Where |
|-------------|--------|
| Spring Boot 3, JPA, MySQL, Validation | POM + `application*.yml` |
| REST API, roles, customers & tickets | `api/`, `service/` |
| Credential auth + bearer usage | `POST /api/v1/auth/*`, JWT filter |
| Validation & HTTP status discipline | `GlobalExceptionHandler`, `ErrorCode` |
| Human-readable / i18n messages | `messages.properties` |
| Unit & integration tests | `src/test/java` |
| Dockerfile | repo root **`Dockerfile`** |
| README | this file |

---

## License

MIT — see [`LICENSE`](LICENSE).
