# Surense — Customer Support Hub

A backend service for a small CRM-style customer-support system. Three roles
(`ADMIN`, `AGENT`, `CUSTOMER`), JWT-based authentication with refresh-token
rotation, and a clean role-aware REST API.

This is a home-assignment project. The README is grown step by step alongside
the codebase; this version reflects the work completed through **Step 3 —
Project skeleton & infrastructure-up**.

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

# 3. Run the service (uses the dev profile by default)
./mvnw spring-boot:run      # on Windows: .\mvnw.cmd spring-boot:run

# 4. Verify the service is up
curl http://localhost:8080/actuator/health
# expected: {"status":"UP"}
```

To stop everything:

```bash
# Ctrl+C the spring-boot:run shell, then:
docker compose down            # keeps MySQL data volume
docker compose down -v         # also wipes the data volume
```

## Running tests

```bash
./mvnw test
```

Tests use **H2 in MySQL-compatibility mode** in-memory, so no Docker / MySQL
container is needed to run them.

## Project layout

```
.
├── docker-compose.yml          # MySQL 8 with utf8mb4 charset
├── pom.xml                     # Maven build, Spring Boot 3.5.14, Java 21
├── mvnw, mvnw.cmd, .mvn/       # Maven wrapper (no system Maven needed)
├── src/
│   ├── main/
│   │   ├── java/com/cookai/csh/
│   │   │   └── CustomerSupportHubApplication.java
│   │   └── resources/
│   │       ├── application.yml         # base config
│   │       └── application-dev.yml     # dev profile overrides
│   └── test/
│       ├── java/com/cookai/csh/
│       │   └── CustomerSupportHubApplicationTests.java
│       └── resources/
│           └── application-test.yml    # H2 + Flyway disabled
└── README.md
```

The Java packages will grow into a **package-by-feature** layout (`auth/`,
`users/`, `customers/`, `tickets/`, `common/`) as features are added.

## Configuration

`application.yml` (base) is dev-friendly out of the box and assumes the local
docker-compose MySQL. Profile-specific files override it:

| Profile | Where it lives                         | Purpose                          |
| ------- | -------------------------------------- | -------------------------------- |
| `dev`   | `src/main/resources/application-dev.yml` | local development (default)      |
| `test`  | `src/test/resources/application-test.yml`| unit/integration tests on H2     |

Most settings can be overridden by environment variables using Spring's standard
`SPRING_*` mapping (e.g. `SPRING_DATASOURCE_PASSWORD`). More configuration
points (JWT secret, ADMIN seed credentials, rate-limit defaults) will be
documented as they are introduced.

## Status

| Step | Title                                 | Status       |
| ---- | ------------------------------------- | ------------ |
| 3    | Project skeleton & infrastructure-up  | ✅ complete  |
| 4a   | Errors + i18n + correlation-id logs   | ⏳ next      |
| 4b   | Rate limiting                         | ⏳ planned   |
| 5    | DB schema & domain skeleton           | ⏳ planned   |
| 6    | Auth: login / refresh / logout        | ⏳ planned   |
| 7+   | Features (customers, tickets, …)      | ⏳ planned   |

## License

MIT — see `LICENSE` (added at Step 13).
