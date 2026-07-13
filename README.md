# Aegis Bank Core REST API Backend

This is the core banking REST API backend for the Aegis Secure Banking Platform. Built using **Spring Boot 3.4**, Hibernate/JPA, and PostgreSQL/H2, this service simulates security vulnerabilities (offense) and implements corresponding security controls (defense) in real-time.

---

## Prerequisites
Ensure you have the following installed locally:
- **Java Development Kit (JDK) 17+** (Recommended: Eclipse Temurin or OpenJDK)
- **Apache Maven 3.8+**
- **Docker Desktop** (Required only for containerized deployment or starting local PostgreSQL & Kafka)

---

## Configuration Properties

Configuration settings are located in:
- Main: [src/main/resources/application.properties](file:///d:/hackathon/BE/src/main/resources/application.properties)
- Testing: [src/test/resources/application.properties](file:///d:/hackathon/BE/src/test/resources/application.properties)

### Key Environment Variables (Overrides)
You can set these in your terminal or `.env` file when running via Docker:
- `SPRING_DATASOURCE_URL`: PostgreSQL connection string (Defaults to local database).
- `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`: Database credentials.
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka broker addresses (Defaults to `localhost:9094`).
- `JWT_SECRET`: Secret key for signature authorization.
- `AEGIS_SECURITY_SYNC_TOKEN`: Sync key for authentication between microservices.

---

## Running the Application (Direct / Host Mode)

### 1. Start Infrastructure (PostgreSQL & Kafka)
Before running the backend on your host machine, start the required database and event stream containers:
```bash
# From the root workspace, navigate to the deployment directory and start infra
cd ../aegis-bank-deployment
docker compose up postgres kafka kafka-ui -d
```

### 2. Run the Spring Boot App
Navigate to the `BE` folder and execute:
```bash
cd ../BE
mvn spring-boot:run
```
- The backend API will start on **`http://localhost:8080`**.
- An in-memory H2 Console is fallback-ready at **`http://localhost:8080/h2-console`**
  - **JDBC URL**: `jdbc:h2:mem:bankdb`
  - **User**: `sa` / **Password**: `password`

### 3. Run Automated Tests & Security Audits
Execute unit and integration tests:
```bash
mvn clean test
```

### 4. Build Production Package (JAR)
Compile and package the application into a standalone runnable JAR file:
```bash
mvn clean package -DskipTests
```
The compiled artifact will be generated at `target/bank-demo-0.0.1-SNAPSHOT.jar`. To run it directly:
```bash
java -jar target/bank-demo-0.0.1-SNAPSHOT.jar
```

---

## Containerized Deployment (Docker)

To build and run this component standalone in Docker:

### 1. Build the Docker Image
```bash
docker build -t aegis-bank-backend .
```

### 2. Run the Container
Ensure it can reach your database and Kafka services (preferably run it within the same docker network):
```bash
docker run -d -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/aegis \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9094 \
  --name aegis-backend-service \
  aegis-bank-backend
```

---

## Security Hardening and Defensive Controls

The backend includes several defensive controls to mitigate common vulnerabilities and infrastructure bugs:

* **JWT Collision Mitigation**: Enforces unique UUID-based `jti` (JWT ID) claims in JWT tokens. This eliminates collision-induced 401 Unauthorized exceptions during high-frequency integration test executions in CI pipelines.
* **Transfer Parameter Validation**: Restricts transfer transactions to block NaN (Not a Number) and Infinite Double values, ensuring mathematical safety against balance tampering.
* **Concurrency Protection**: Implements backend synchronization locks on sensitive routes to prevent race conditions (such as double-spending attempts during funds transfer).
* **Rate Limiting**: Defends API endpoints against brute force attempts and denial-of-service vectors.

---

## Tech Stack

| Component | Version |
|---|---|
| Java | 17 (Eclipse Temurin) |
| Spring Boot | 3.4.11 |
| Spring Security | 6.5.9 |
| Hibernate/JPA | PostgreSQL 16 + H2 fallback |
| Apache Kafka | 3.9.2 (kafka-clients) |
| JWT | jjwt 0.11.5 |
| Build | Maven, multi-stage Docker |

---

## Deployment Info

In the full ecosystem, this service runs as `be-backend` container. Nginx routes `/api-bank/*` requests to this backend on port 8080. See [aegis-bank-deployment](https://github.com/Little-Boy-s-Aegis/aegis-bank-deployment) for the full Docker Compose setup.

---

## Related Repositories

| Repository | Description |
|---|---|
| [aegis-bank-deployment](https://github.com/Little-Boy-s-Aegis/aegis-bank-deployment) | Docker Compose orchestration, Nginx, Kafka, OPA, Vault |
| [aegis-bank-web-client](https://github.com/Little-Boy-s-Aegis/aegis-bank-web-client) | Next.js banking portal |
| [aegis-bank-mobile-app](https://github.com/Little-Boy-s-Aegis/aegis-bank-mobile-app) | Flutter mobile banking app |
| [dashboard](https://github.com/Little-Boy-s-Aegis/dashboard) | SOC Dashboard (Go + React) |
| [agent-layer-1](https://github.com/Little-Boy-s-Aegis/agent-layer-1) | AI Sensor Agents |
| [agent-layer-2](https://github.com/Little-Boy-s-Aegis/agent-layer-2) | Meta Analyzer and SOAR orchestrator prompts |
| [aegis-soar-engine](https://github.com/Little-Boy-s-Aegis/aegis-soar-engine) | SOAR Decision Engine |
| [aegis-staging-sandbox](https://github.com/Little-Boy-s-Aegis/aegis-staging-sandbox) | Staging simulation APIs |
| [aegis-bank-terraform](https://github.com/Little-Boy-s-Aegis/aegis-bank-terraform) | AWS Terraform infrastructure |
