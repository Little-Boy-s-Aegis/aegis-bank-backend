# Aegis Bank Backend

Spring Boot REST API for the Little Boy's Aegis banking and cyber-defense
demonstration. It provides customer authentication, accounts, transfers,
security-control APIs, audit/security logs, JWT enforcement, IP blocking, and
Kafka security-event publication.

> This is a controlled hackathon target. It includes configurable
> attack-and-defense behavior and seeded demo credentials. Do not expose it as a
> real banking service or reuse its defaults in production.

## Capabilities

- User registration, login, logout, and JWT revocation
- Account detail lookup
- Transaction execution and history search
- Concurrency and idempotency protections around transfers
- Validation against invalid, non-finite, negative, and unsafe amounts
- Runtime security-control toggles for demo scenarios
- Security log and banned-IP management
- Kafka publication of security telemetry
- Request-path normalization, IP block, JWT, and Spring Security filters
- PostgreSQL runtime persistence and H2-backed tests

## Architecture

```text
web/mobile client
      |
      v
Spring Security filter chain
  |-- API path normalization
  |-- IP block enforcement
  `-- JWT authentication
      |
      v
REST controllers --> services/repositories --> PostgreSQL
      |
      `--> security event publisher --> Kafka --> SOC pipeline
```

## Technology

| Component | Version / implementation |
|---|---|
| Java | 17 |
| Spring Boot | 3.4.11 |
| Spring Security | 6.5.9 |
| Persistence | Spring Data JPA, PostgreSQL runtime, H2 tests |
| Messaging | Spring Kafka / Kafka clients 3.9.2 |
| JWT | JJWT 0.11.5 |
| Build | Maven |

## Prerequisites

- JDK 17+
- Maven 3.8+
- PostgreSQL 16 or a compatible PostgreSQL server
- Optional Kafka broker; the publisher is configured for short, non-blocking
  failure behavior when Kafka is unavailable
- Docker for container builds or the complete local stack

## Configuration

The runtime defaults to port `8080` and PostgreSQL database `aegis` on
`localhost:5432`. The main settings can be overridden with Spring environment
variables:

| Variable | Required | Purpose |
|---|---:|---|
| `SPRING_DATASOURCE_URL` | no | JDBC URL; default `jdbc:postgresql://localhost:5432/aegis` |
| `SPRING_DATASOURCE_USERNAME` | no | Database user; default `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | yes | Database password |
| `JWT_SECRET` | yes | High-entropy JWT signing secret |
| `AEGIS_SECURITY_SYNC_TOKEN` | yes | Internal service authentication token |
| `KAFKA_BOOTSTRAP_SERVERS` | no | Broker list; default `localhost:9094` |
| `SERVER_PORT` | no | HTTP port; default `8080` |

Generate independent local secrets, for example:

```bash
export SPRING_DATASOURCE_PASSWORD='<database-password>'
export JWT_SECRET="$(openssl rand -hex 32)"
export AEGIS_SECURITY_SYNC_TOKEN="$(openssl rand -hex 32)"
```

Do not place real values in `application.properties` or commit them in shell
files.

## Run Locally

Create the database and start the application:

```bash
createdb -h localhost -U postgres aegis
mvn spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/health
```

The application seeds demo users only when the user table is empty:

| Role | Username | Password | Account |
|---|---|---|---|
| Administrator | `admin` | `admin123` | none |
| Customer | `alice` | `password123` | `ACC-123456` |
| Customer | `bob` | `password123` | `ACC-987654` |

Change or disable these seed values before any shared deployment.

## API Summary

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | Service health |
| `POST` | `/api/auth/register` | Register a demo customer |
| `POST` | `/api/auth/login` | Authenticate and receive JWT/user details |
| `POST` | `/api/auth/logout` | Revoke the presented token |
| `GET` | `/api/accounts/{accountNumber}/details` | Load account details |
| `POST` | `/api/transactions/transfer` | Transfer funds |
| `GET` | `/api/transactions/history` | Query account history |
| `GET` | `/api/admin/security/status` | Read demo security controls |
| `POST` | `/api/admin/security/toggle` | Change an allowed control |
| `GET` | `/api/admin/security/logs` | Read security events |
| `POST` | `/api/admin/security/logs/clear` | Clear demo security logs |
| `GET` | `/api/admin/security/banned-ips` | List blocked IPs |
| `POST` | `/api/admin/security/banned-ips` | Add a block |
| `POST` | `/api/admin/security/banned-ips/clear` | Clear blocks |

Protected endpoints require `Authorization: Bearer <jwt>`. Administrative
operations also require the appropriate role or internal synchronization token
as enforced by the controller and filter chain.

### Login example

```bash
curl -sS http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123"}'
```

## Tests and Build

Tests use an in-memory H2 database in PostgreSQL compatibility mode and disable
Kafka auto-configuration, so PostgreSQL and Kafka are not required:

```bash
mvn test
mvn clean verify
mvn clean package
```

The packaged application is written to
`target/bank-demo-0.0.1-SNAPSHOT.jar`:

```bash
java -jar target/bank-demo-0.0.1-SNAPSHOT.jar
```

Additional scripts under `scripts/` exercise end-to-end and SQL-injection demo
flows on Windows/PowerShell hosts.

## Docker

```bash
docker build -t aegis-bank-backend .
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/aegis \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD='<database-password>' \
  -e JWT_SECRET='<jwt-secret>' \
  -e AEGIS_SECURITY_SYNC_TOKEN='<internal-token>' \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9094 \
  aegis-bank-backend
```

The full deployment repository supplies the internal network, PostgreSQL,
Kafka, gateway, and synchronized secrets.

## Repository Layout

```text
src/main/java/com/example/bank/
├── config/          # Security filters, JWT, path rules, and demo seeding
├── controller/      # REST endpoints and exception mapping
├── model/           # JPA entities and request models
├── repository/      # Spring Data repositories
└── service/         # Security event publication
src/main/resources/  # Runtime and logging configuration
src/test/             # Controller and security tests
scripts/              # Manual/e2e security demo scripts
```

## Security Boundaries

- Never use the seeded users, default database name, or demo control settings
  for production.
- Use TLS at the gateway and rotate JWT/internal tokens independently.
- Keep PostgreSQL and Kafka on private networks.
- Treat logs as sensitive: they may contain masked attack payload context and
  customer identifiers.
- Security toggles exist for demonstration; scope and gate administrator access
  when running a shared environment.

## Related Repositories

- [`aegis-bank-web-client`](https://github.com/Little-Boy-s-Aegis/aegis-bank-web-client) — web client
- [`aegis-bank-mobile-app`](https://github.com/Little-Boy-s-Aegis/aegis-bank-mobile-app) — Flutter client
- [`aegis-bank-deployment`](https://github.com/Little-Boy-s-Aegis/aegis-bank-deployment) — full stack
- [`dashboard`](https://github.com/Little-Boy-s-Aegis/dashboard) — SOC consumer and UI
