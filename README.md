# Surense — Customer Support Hub

A backend service for a small CRM-style customer-support system. Three roles
(`ADMIN`, `AGENT`, `CUSTOMER`), JWT-based authentication with refresh-token
rotation, and a clean role-aware REST API.

This is a home-assignment project. The README is grown step by step alongside
the codebase; this version reflects the work completed through **Step 5 — DB
schema, Flyway migrations, and JPA domain skeleton** (plus a local-only seeded
`admin` user in `V3`).

---

## Tech stack

- **Java 21** (Eclipse Temurin)
- **Spring Boot 3.5.14** — Web, Validation, Data JPA, Security, Actuator
- **MySQL 8** with `utf8mb4` (full Unicode, including Hebrew)
- **Flyway** for schema migrations
- **Maven** via the bundled wrapper (`mvnw` / `mvnw.cmd`)
- **JUnit 5**, **Mockito**, **Spring Security Test** for tests
- **H2** in-memory (test scope only) for fast context-load tests
- **Lombok** for boilerplate reduction
- **Logback** + **logstash-logback-encoder** for structured logging (JSON in prod)

## Prerequisites

- Java 21 JDK on the `PATH` (`java -version` should report `21.x`)
- Docker Desktop (or any Docker Engine)
- ~3306/tcp free on `localhost` for the MySQL container

> Verified on Windows 10/11 (PowerShell), Java 21 Temurin, Docker 29.x.

## Quick start

```bash
# 1. Start MySQL in the background
docker compose up -d

# 2. Wait for it to be healthy (10–30s on first run)
docker compose ps           # mysql STATUS should read "healthy"

# 3. Run the service (uses the dev profile by default → text logs)
./mvnw spring-boot:run      # on Windows: .\mvnw.cmd spring-boot:run

# 4. Verify the service is up
curl http://localhost:8080/actuator/health
# expected: {"status":"UP","groups":["liveness","readiness"]}
```

To stop everything:

```bash
# Ctrl+C the spring-boot:run shell, then:
docker compose down            # keeps MySQL data volume
docker compose down -v         # also wipes the data volume
```

To see the **production-shape JSON logs** locally:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Developer guide

### Build / run / test commands

The `mvnw` / `mvnw.cmd` script in the repo root is the **Maven wrapper** —
analogous to `dotnet` itself: no system Maven needed, the script downloads the
right Maven version on first run. `.NET → Maven` mental map:

| .NET                       | Maven (this project)                  | What it does                                    |
| -------------------------- | ------------------------------------- | ----------------------------------------------- |
| `dotnet restore`           | (auto, on demand)                     | Deps are resolved automatically before any goal |
| `dotnet clean`             | `.\mvnw.cmd clean`                    | Deletes the `target\` folder                    |
| `dotnet build`             | `.\mvnw.cmd compile`                  | Compiles `src\main\java` only                   |
| `dotnet test`              | `.\mvnw.cmd test`                     | Compile + run all unit / integration tests      |
| `dotnet run`               | `.\mvnw.cmd spring-boot:run`          | Run on `http://localhost:8080`                  |
| `dotnet publish`           | `.\mvnw.cmd package`                  | Produces a runnable fat-JAR in `target\`        |
| `dotnet clean && build`    | `.\mvnw.cmd clean package`            | Wipe + compile + test + JAR in one shot         |

> On macOS/Linux substitute `./mvnw` for `.\mvnw.cmd`.

If a fresh PowerShell tab still picks up Java 8, override `JAVA_HOME` for that
session:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

### Trying the API

With the app running (`.\mvnw.cmd spring-boot:run`), in a second PowerShell tab:

```powershell
# Health
curl.exe http://localhost:8080/actuator/health

# Happy path on the dev-only boom endpoint
curl.exe -i "http://localhost:8080/__test__/boom?type=ok"

# Every implemented error branch
curl.exe -i "http://localhost:8080/__test__/boom?type=notfound"
curl.exe -i "http://localhost:8080/__test__/boom?type=conflict"
curl.exe -i "http://localhost:8080/__test__/boom?type=badrequest"
curl.exe -i "http://localhost:8080/__test__/boom?type=notimplemented"
curl.exe -i "http://localhost:8080/__test__/boom?type=forbidden"
curl.exe -i "http://localhost:8080/__test__/boom?type=unauthenticated"
curl.exe -i "http://localhost:8080/__test__/boom?type=unhandled"

# Validation failure (returns 400 with fieldErrors)
curl.exe -i -X POST -H "Content-Type: application/json" `
    --data-raw '{\"name\":\"\",\"email\":\"not-an-email\"}' `
    http://localhost:8080/__test__/boom/validate

# Malformed JSON (returns 400 MALFORMED_REQUEST)
curl.exe -i -X POST -H "Content-Type: application/json" `
    --data-raw '{ this is not json' `
    http://localhost:8080/__test__/boom/validate

# Send your own correlation id and watch it echoed back in the response
# header AND in the body's correlationId field
curl.exe -i -H "X-Correlation-Id: my-trace-id-123" `
    "http://localhost:8080/__test__/boom?type=notfound"
```

> Use **`curl.exe`** (with `.exe`) explicitly in PowerShell — bare `curl` is a
> PowerShell alias for `Invoke-WebRequest`, which has different flags. The
> `-i` flag includes response headers; drop it for body-only.

### Watching the logs

Logs go to **stdout** of the `spring-boot:run` window — no log files written.
The format depends on the active profile:

- **`dev`** (default): human-readable text. The `[corr=...,user=...,role=...]`
  block is MDC; paste a `corr=` value into a search to follow a single
  request end-to-end across all the lines it produced.
- **`prod`**: structured JSON, one event per line. Run locally with
  `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=prod"` to see it.

See [Architecture → Logging & correlation IDs](#logging--correlation-ids) for
the exact field shape in each format.

To raise verbosity, either edit `src/main/resources/application-dev.yml`
(e.g. add `org.springframework: DEBUG` under `logging.level`) and restart, or
set an env var before running:

```powershell
$env:LOGGING_LEVEL_COM_SURENSE = 'TRACE'
```

### Running the test suite

Tests use **H2 in MySQL-compatibility mode** in-memory, so no Docker / MySQL
container is needed to run them.

```powershell
# All tests
.\mvnw.cmd test

# A single test class
.\mvnw.cmd test "-Dtest=LogMaskerTest"

# A single test method
.\mvnw.cmd test "-Dtest=LogMaskerTest#masksJsonStylePassword"

# Skip the dep-resolve check (faster on a warm cache)
.\mvnw.cmd -o test
```

Surefire writes per-class text + XML reports to `target\surefire-reports\`.

### Debugging in Cursor

Run the app with the JDWP debug agent listening on port 5005:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

In Cursor (with the **Extension Pack for Java** installed):

1. Open the Run & Debug panel (`Ctrl+Shift+D`).
2. Click "Run and Debug" → choose **Attach to Java Process** → host `localhost`,
   port `5005`. Cursor will create a `.vscode/launch.json` entry the first time.
3. Drop a breakpoint in any `.java` file (e.g. `BoomController.java`) and
   `curl` the endpoint that hits it — execution pauses on the breakpoint.

`suspend=n` means the app starts immediately without waiting for the
debugger. Switch to `suspend=y` if you need to debug startup itself — the JVM
will block until the debugger attaches.

## Architecture

### Package layout

```
com.surense
├── CustomerSupportHubApplication.java
├── auth/                           ← Step 5: users + refresh tokens (Step 6: JWT API)
│   ├── entity/                   ← User, RefreshToken, UserRole
│   └── repository/
├── customers/                    ← Step 5: customer records (Step 7+: REST)
│   ├── entity/
│   └── repository/
├── tickets/                      ← Step 5: support tickets (Step 7+: REST)
│   ├── entity/
│   └── repository/
├── _dev/                           ← TEMPORARY dev-only code (removed Step 7+)
│   └── BoomController              ← exercises every error branch
└── common/                         ← cross-cutting concerns
    ├── config/
    │   └── SecurityConfig          ← permissive (replaced Step 6)
    ├── error/
    │   ├── ErrorCode               ← enum: HTTP status + message key
    │   ├── ErrorResponse           ← unified JSON shape
    │   ├── ErrorResponseFactory    ← shared 429 + advice body builder
    │   ├── GlobalExceptionHandler  ← @RestControllerAdvice
    │   └── exception/
    │       ├── ApiException        ← base
    │       ├── ResourceNotFoundException
    │       ├── ConflictException
    │       ├── BadRequestException
    │       ├── NotImplementedException
    │       └── RateLimitedException  ← 429 + Retry-After (Step 6 in-controller)
    ├── i18n/
    │   └── MessageResolver         ← MessageSource facade
    ├── logging/
    │   ├── CorrelationIdConstants
    │   ├── CorrelationIdFilter     ← first in servlet filter chain
    │   ├── LogMasker               ← PII regex rules
    │   ├── MaskingPatternLayout    ← dev/text encoder
    │   └── MaskingLogstashEncoder  ← prod/JSON encoder
    └── ratelimit/                  ← Step 4b: in-process token buckets
        ├── IpRateLimitFilter       ← before Spring Security (per-IP)
        ├── UserRateLimitFilter     ← after Spring Security (per-userId)
        ├── LoginRateLimiter        ← Step 6: failed logins only
        └── RefreshTokenRateLimiter ← Step 6: per hashed refresh token
```

### Database schema (Step 5)

[Flyway](https://flywaydb.org/) scripts are under `src/main/resources/db/migration/`:

| Version | What it does |
| ------- | -------------- |
| `V1` | `users`, `refresh_tokens` — auth persistence (Step 6 wires JWT + refresh rotation) |
| `V2` | `customers`, `tickets` — CRM core (role-aware APIs in Step 7+) |
| `V3` | Idempotent seed: **`admin`** / **`ADMIN`** row for local docker MySQL only |

**Seeded dev admin (`V3`):** plain-text password **`AdminChangeMe1!`** (BCrypt-stored in the migration file). Intended only for **local** `docker compose` MySQL — rotate before any shared or non-local deployment. If username `admin` already exists, `ON DUPLICATE KEY UPDATE` leaves it untouched.

The **`test` profile** disables Flyway and builds an H2 schema via Hibernate — **`V3` never runs in automated tests**, so suites stay deterministic without relying on SQL seed data.

Real REST controllers (`/api/v1/...`) arrive from **Step 6** (auth) and **Step 7+** (customers, tickets).

### Error handling

All errors return the same JSON shape:

```json
{
  "timestamp": "2026-05-04T08:30:01.234Z",
  "status": 404,
  "error": "Not Found",
  "code": "USER_NOT_FOUND",
  "message": "User with id 42 was not found",
  "path": "/api/v1/customers/42",
  "correlationId": "8c9b7a6e-1234-4567-89ab-cdef01234567",
  "fieldErrors": [
    { "field": "email", "message": "must be a well-formed email address" }
  ]
}
```

- `code` is the **stable machine-readable identifier** — clients should branch
  on it, not on `message`.
- `message` is human-readable and **localized** via `messages.properties`.
- `correlationId` is the same value echoed in the `X-Correlation-Id` response
  header and stamped on every log line for the request — paste it into a log
  search to reconstruct the full request flow.
- `fieldErrors` appears only on validation failures (400). The
  `rejectedValue` is deliberately omitted because user-supplied values may be
  PII.

The `GlobalExceptionHandler` provides:

| Source                           | Status | `code`                       |
| -------------------------------- | :----: | ---------------------------- |
| `ApiException` (any subclass)    | per `ErrorCode` | per `ErrorCode`     |
| `RateLimitedException`           | 429    | `RATE_LIMITED` (+ `Retry-After` header) |
| `AccessDeniedException`          | 403    | `FORBIDDEN`                  |
| `BadCredentialsException`        | 401    | `BAD_CREDENTIALS`            |
| `MethodArgumentNotValidException`| 400    | `VALIDATION_FAILED` (+fields)|
| `HttpMessageNotReadableException`| 400    | `MALFORMED_REQUEST`          |
| any uncaught `Exception`         | 500    | `INTERNAL_ERROR` (sanitized) |

Uncaught exceptions are logged with full stack trace **server-side** (with the
correlationId) but the response body is always the generic
`"An unexpected error occurred"` — internal details never leak.

### Logging & correlation IDs

A `CorrelationIdFilter` runs at `Ordered.HIGHEST_PRECEDENCE` (before Spring
Security). For every request it:

1. Reads the `X-Correlation-Id` request header, or generates a UUID v4 if
   absent / blank.
2. Sets the value on SLF4J's MDC under key `correlationId`.
3. Echoes the same value on the `X-Correlation-Id` response header.
4. Clears MDC in `finally` to prevent leakage between reused worker threads.

Two output formats, selected by Spring profile:

| Profile             | Format            | Encoder                  |
| ------------------- | ----------------- | ------------------------ |
| `dev` / `default` / `test` | colored text | `MaskingPatternLayout`   |
| `prod`              | structured JSON   | `MaskingLogstashEncoder` |

**Dev text format** (one line):

```
2026-05-04 10:38:21.123  INFO http-nio-8080-1 [corr=8c9b7a6e,user=42,role=AGENT] c.c.c.t.TicketController : request.end status=200
```

**Prod JSON shape**:

```json
{
  "timestamp": "2026-05-04T08:38:21.123Z",
  "level": "INFO",
  "thread": "http-nio-8080-exec-3",
  "logger": "com.surense.tickets.TicketController",
  "service": "customer-support-hub",
  "correlationId": "8c9b7a6e-1234-4567-89ab-cdef01234567",
  "userId": "42",
  "userRole": "AGENT",
  "message": "request.end status=200"
}
```

### PII protection

Defence-in-depth, layered:

1. **Error messages reference resources by id only** — never echo emails,
   names, phone numbers, or other PII.
2. **Validation `fieldErrors` omit `rejectedValue`** so failed inputs aren't
   echoed back.
3. **Server-side log scrubber** (`LogMasker`) applies these regex rules to
   every log line in both formats (rules applied in order):
   1. JSON-style key/value pairs for `username`, `password`, `email`,
      `phoneNumber`, `policyNumber` → value replaced with `***` (e.g.
      `"password":"hunter2"` → `"password":"***"`).
   2. Bare `key=value` for the same keys → value replaced with `***`
      (e.g. `password=hunter2` → `password=***`). Word-bounded so
      `passwordHash=...` is left intact.
   3. Bare email addresses anywhere → fully replaced as `***@***`
      (the domain is **not** preserved, to avoid leaks like `.gov.il`).
   4. `Bearer xxxxx` tokens → `Bearer ***`.
4. The catch-all 500 returns a generic message — uncaught exception text is
   never returned to the client.

### Rate limiting (Step 4b)

In-process **token-bucket** limits via [Bucket4j](https://bucket4j.com/)
(`com.bucket4j:bucket4j_jdk17-core` — bytecode floor Java 17, runs on this
project's **Java 21** runtime) with buckets stored in a **size-bounded Caffeine**
cache (`cache-max-entries` + `cache-expire-after-access` in `application.yml`).

**Why two filters (Option B)?** A servlet `IpRateLimitFilter` ordered just
after `CorrelationIdFilter` rejects anonymous floods **before** Spring
Security runs (cheap fail-fast). A `UserRateLimitFilter` registered **after**
`AnonymousAuthenticationFilter` applies the per-`userId` budget once the
security context is definitive. Rationale is documented inline on both
filters and summarized here for interview notes.

| Limiter | Key | Default (YAML) | Where enforced |
| ------- | --- | -------------- | -------------- |
| Unauthenticated traffic | client IP (`X-Forwarded-For` first hop, else `RemoteAddr`) | 30 / minute | `IpRateLimitFilter` |
| Authenticated traffic | `userId` from `Authentication` | 60 / minute | `UserRateLimitFilter` |
| Failed `POST /auth/login` | `username` + client IP | 5 / 15 min | `LoginRateLimiter` (Step 6) |
| `POST /auth/refresh` body | SHA-256 of refresh token | 10 / minute | `RefreshTokenRateLimiter` (Step 6) |

**429 contract:** same `ErrorResponse` JSON as every other error (`code`:
`RATE_LIMITED`, message from `messages.properties`) plus a `Retry-After` header
(seconds, always ≥ 1). Servlet filters write the body directly; in-controller
code (Step 6) may throw `RateLimitedException` which `GlobalExceptionHandler`
maps the same way.

**`X-Forwarded-For` trust:** the first comma-separated hop is treated as the
client IP. This is correct behind a trusted reverse proxy that **strips or
appends** the header; without such a proxy, clients can spoof IPs — document
this assumption in deployment docs.

**Test profile:** `surense.rate-limit.enabled=false` in `application-test.yml`
so unrelated `@SpringBootTest` classes do not inherit an exhausted bucket from
the rate-limit integration suite. Opt back in with
`@TestPropertySource(properties = "surense.rate-limit.enabled=true")` (see
`BoomControllerRateLimitIntegrationTest`).

**Caveat:** `IpRateLimitFilter` skips the per-IP bucket when **any** non-blank
`Authorization` header is present so JWT traffic is governed by
`UserRateLimitFilter` instead. A client could send a junk `Authorization` value
while still being anonymous and **skip** the IP bucket until Step 6 hardens
this edge (e.g. validate the bearer early or re-apply IP limits for anonymous
+bogus-bearer — out of scope for Step 4b).

## API reference

### Endpoints currently implemented

| Method & Path             | Status | Notes                                   |
| ------------------------- | :----: | --------------------------------------- |
| `GET /actuator/health`    | 200    | Spring Boot health probe                |
| `GET /__test__/boom`      | varies | **dev-only**, exercises error pipeline  |
| `POST /__test__/boom/validate` | varies | **dev-only**, demos validation/malformed |

The `/__test__/boom` endpoint is **temporary** and gated by
`surense.dev.boom-endpoint.enabled=true` (set only in dev profile). It will be
removed in Step 7+ once real business endpoints exist. Supported `?type=`
values: `ok`, `notfound`, `ticketnotfound`, `conflict`, `badrequest`,
`notimplemented`, `forbidden`, `unauthenticated`, `unhandled`.

## Configuration

`application.yml` (base) is dev-friendly out of the box and assumes the local
docker-compose MySQL. Profile-specific files override it:

| Profile | Where it lives                         | Purpose                                       |
| ------- | -------------------------------------- | --------------------------------------------- |
| `dev`   | `src/main/resources/application-dev.yml` | local development (default), text logs      |
| `test`  | `src/test/resources/application-test.yml`| unit/integration tests on H2, text logs     |
| `prod`  | (env-driven only)                      | production-shape JSON logs                    |

Settings can be overridden by environment variables using Spring's standard
mapping (e.g. `SPRING_DATASOURCE_PASSWORD`). Application-specific flags:

| Property | Default | Purpose |
| -------- | :-----: | ------- |
| `surense.dev.boom-endpoint.enabled` | `false` | Enables the temporary `BoomController` (set to `true` only in dev profile) |
| `surense.rate-limit.enabled` | `true` (base YAML) / `false` (`test` profile) | Master switch for `IpRateLimitFilter` + `UserRateLimitFilter` |
| `surense.rate-limit.cache-max-entries` | `100000` | Max distinct bucket keys held in Caffeine |
| `surense.rate-limit.cache-expire-after-access` | `PT1H` | Idle bucket eviction (second memory floor beside the size cap) |
| `surense.rate-limit.exempt-paths` | `/actuator/health`, `/actuator/health/**` | Ant patterns skipped by the servlet limiters (health / probes) |
| `surense.rate-limit.unauth-ip.{capacity,refill}` | `30` + `PT1M` | Anonymous per-IP budget |
| `surense.rate-limit.auth-user.{capacity,refill}` | `60` + `PT1M` | Authenticated per-userId budget |
| `surense.rate-limit.login.{capacity,refill}` | `5` + `PT15M` | Failed-login budget (Step 6) |
| `surense.rate-limit.refresh.{capacity,refill}` | `10` + `PT1M` | Refresh budget per hashed token (Step 6) |

Further auth-related settings (JWT signing key, token TTLs, etc.) land in **Step 6**.
The baseline **`admin`** user credentials come from Flyway **`V3`** (see [Database schema](#database-schema-step-5)); replace them via SQL or a password-change API once Step 6 ships.

## Localization

Error messages are loaded from `src/main/resources/messages.properties` via
Spring's `MessageSource`. The default and only bundled language is **English**.

To add a language (e.g. Hebrew), drop a sibling file:

```
src/main/resources/messages_he.properties
```

…containing the same keys with translated values. Clients select it via:

```
Accept-Language: he
```

The application's `MessageResolver` falls back to English (and ultimately to
the raw message key) if a locale or key is missing.

## Hebrew / Unicode support

- MySQL is started with `--character-set-server=utf8mb4
  --collation-server=utf8mb4_unicode_ci` so all text columns hold any Unicode
  scalar including Hebrew, Arabic, and emoji.
- The JDBC URL pins `useUnicode=true&characterEncoding=UTF-8`.
- Validators on user-supplied text fields are Unicode-correct
  (`@NotBlank`, `@Size`) — no `[A-Za-z]`-style regex that would silently
  reject non-ASCII input.
- RTL display is a client-side concern; the backend is direction-agnostic.

## Status

| Step | Title                                            | Status       |
| ---- | ------------------------------------------------ | ------------ |
| 3    | Project skeleton & infrastructure-up             | ✅ complete  |
| 4a   | Errors + i18n + correlation-id logs + PII masking | ✅ complete |
| 4b   | Rate limiting                                    | ✅ complete  |
| 5    | DB schema & domain skeleton                      | ✅ complete  |
| 6    | Auth: login / refresh / logout                   | ⏳ planned   |
| 7+   | Features (customers, tickets, …)                 | ⏳ planned   |

## License

MIT — see `LICENSE` (added at Step 13).
