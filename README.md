# Document Intelligence Catalog Service 🚀

GitHub template for the iQ Key Value platform. Extends the standard `foundation-microservice-project-layout` with a multi-tenant, JWT-secured catalog service backed by PostgreSQL, RabbitMQ, MinIO, and Ollama.

## About

This template provides:

- **Maven single-module layout** — standard `src/main/java` structure with MyBatis, PostgreSQL, Liquibase, and RabbitMQ wired up
- **Ollama connectivity** — LLM client wired to a local Ollama instance via `spring.ai.ollama.*` (base URL and model configurable per environment)
- **Object storage** — MinIO (S3-compatible) client bound to a private `catalog-assets` bucket, served to clients through presigned URLs
- **Multi-profile Spring config** — `local`, `sit`, `uat`, `prd` profiles with correct defaults per environment
- **Docker Compose** — `compose.base.yaml` with PostgreSQL, RabbitMQ, MinIO, DbGate, SonarQube, Ollama, Prometheus, and Grafana; `compose.container.yaml` for the full runtime stack including the service container
- **Dockerfile** — multi-stage build with layered JAR extraction, non-root `appuser`, and JVM tuning
- **Database Initialization** — `docker/postgres/init-catalog.sql` for automated schema and extension setup
- **Security** — Spring Security + OAuth2 Resource Server (RS256 JWT) pre-configured
- **Messaging** — RabbitMQ consumer for IAM tenant provisioning events (`tenant.created` / `tenant.updated`) with bounded retries and a dead-letter queue
- **Observability** — Micrometer + Prometheus + structured JSON logging (Logstash encoder)
- **Quality tools** — Checkstyle, JaCoCo, ArchUnit, Husky git hooks, commitlint

## Template Usage

1. Click **[Use this template](https://github.com/IQKV/docintelligence-catalog-service/generate)** to create your repository
2. Replace all `catalog` / `catalogservice` / `CatalogService` tokens with your service name (see the token map in `foundation-microservice-project-layout`)
3. Update `pom.xml` — `artifactId`, `name`, `description`, `start-class`
4. Rename the Java package from `com.iqkv.docintelligence.catalogservice` to your package
5. Rename `docker/postgres/init-catalog.sql` and update the `catalogservice` database and `svc_catalog_*` credentials to match
6. Adjust or extend the feature modules to fit your use case
7. Remove unused dependencies from `pom.xml` (e.g. `spring-ai-starter-model-ollama` if no LLM integration is needed)

## Quick Links

- [API Documentation](./docs/api/README.md)
- [Architecture Overview](./docs/architecture/README.md)
- [Deployment Guide](./docs/deployment/README.md)
- [Contributing Guidelines](.github/CONTRIBUTING.md)

## Tech Stack

- Java 25 / Spring Boot (latest via parent POM)
- Spring AI Ollama starter (local LLM connectivity)
- MyBatis 3.x (no JPA) + PostgreSQL 17
- Liquibase for schema migrations
- RabbitMQ for async messaging
- Spring Security + OAuth2 Resource Server (RS256 JWT)
- MinIO client for S3-compatible object storage
- ShedLock for distributed scheduled jobs
- Micrometer + Prometheus
- springdoc-openapi (Swagger UI)

## Prerequisites

- JDK 25 (Eclipse Temurin)
- Maven 3.9+
- Node.js >= 22.15.0 & pnpm >= 10.33.2 (git hooks)
- Docker & Docker Compose
- [Ollama](https://ollama.com) running locally with at least one model pulled (e.g. `ollama pull llama3.2`)

## Quick Start

```bash
# Clone or use as template
git clone https://github.com/IQKV/docintelligence-catalog-service.git my-service
cd my-service

# Install git hooks
pnpm install

# Pull a model into Ollama (one-time)
ollama pull llama3.2

# Copy environment variables
cp .env.example .env.local
# Defaults point to Ollama on localhost:11434 — no API key needed for local dev

# Start dependencies (PostgreSQL, RabbitMQ, MinIO, etc.)
docker compose up -d

# Run the service
./mvnw spring-boot:run -Pdev
# → API:          http://localhost:8080
# → Actuator:     http://localhost:8081/actuator/health
# → Swagger:      http://localhost:8080/swagger-ui.html
# → MinIO:        http://localhost:9003 (console)
# → DbGate:       http://localhost:3100
```

## Environment Variables

| Variable                              | Default                  | Description                               |
| ------------------------------------- | ------------------------ | ----------------------------------------- |
| `DB_HOST`                             | `localhost`              | PostgreSQL host                           |
| `DB_PORT`                             | `5432`                   | PostgreSQL port                           |
| `DB_NAME`                             | `catalogservice`         | Database name                             |
| `DB_USERNAME`                         | `svc_catalog_dba`        | Database user                             |
| `DB_PASSWORD`                         | `svc_catalog_dba`        | Database password                         |
| `RABBITMQ_HOST`                       | `localhost`              | RabbitMQ host                             |
| `RABBITMQ_PORT`                       | `5672`                   | RabbitMQ AMQP port                        |
| `RABBITMQ_USERNAME`                   | `svc_catalog_rmq`        | RabbitMQ user                             |
| `RABBITMQ_PASSWORD`                   | `svc_catalog_rmq`        | RabbitMQ password                         |
| `OBJECTSTORAGE_ENDPOINT`              | `http://localhost:9000`  | S3-compatible endpoint (MinIO in SIT/UAT) |
| `OBJECTSTORAGE_PUBLIC_ENDPOINT`       | _(empty)_                | Public URL used to rewrite presigned URLs |
| `OBJECTSTORAGE_ACCESS_KEY`            | `iqkv`                   | S3 access key                             |
| `OBJECTSTORAGE_SECRET_KEY`            | `iqkv_password`          | S3 secret key                             |
| `OBJECTSTORAGE_BUCKET_NAME`           | `catalog-assets`         | Private bucket holding catalog assets     |
| `OBJECTSTORAGE_REGION`                | `us-east-1`              | S3 region                                 |
| `OBJECTSTORAGE_SSL`                   | `false`                  | Use HTTPS for the S3 endpoint             |
| `MESSAGING_ENABLED`                   | `false`                  | Maps to `iqkv.messaging.rabbitmq.enabled` |
| `SPRING_AI_OLLAMA_BASE_URL`           | `http://localhost:11434` | Ollama server URL                         |
| `SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL` | `llama3.2`               | Model name (must be pulled in Ollama)     |
| `ROLLOUT_MODE`                        | `MULTI_TENANT`           | Platform rollout mode                     |

> Copy `.env.example` to `.env.local` / `.env.uat` / `.env.prd` and fill in values per environment.
> The local Compose stack publishes MinIO on host port `9002` (console on `9003`) — set `OBJECTSTORAGE_ENDPOINT=http://localhost:9002` when running the service outside Docker.

## Maven Commands

```bash
# Build and test (skip Checkstyle during development)
./mvnw clean verify -Dcheckstyle.skip=true

# Run tests only
./mvnw test -Dcheckstyle.skip=true

# Explicit Checkstyle check
./mvnw checkstyle:check

# Coverage report → target/site/jacoco/index.html
./mvnw jacoco:report

# Production build
./mvnw clean package -Pproduction
```

## Docker

```bash
# Build image
docker build -t iqkv/docintelligence-catalog-service:latest .

# Run full stack (service + dependencies)
docker compose -f compose.container.yaml up -d
```

## Monitoring

| Endpoint                   | Description                 |
| -------------------------- | --------------------------- |
| `GET /actuator/health`     | Liveness + readiness probes |
| `GET /actuator/metrics`    | Application metrics         |
| `GET /actuator/prometheus` | Prometheus scrape endpoint  |
| `GET /swagger-ui.html`     | API documentation           |
| `GET /` (on port 9003)     | MinIO Console               |
| `GET /` (on port 3000)     | Grafana dashboards          |
| `GET /` (on port 9090)     | Prometheus UI               |

## Project Structure

```
.
├── docker/
│   ├── postgres/       # DB initialization scripts (init-catalog.sql)
│   ├── prometheus/     # Prometheus configuration
│   └── grafana/        # Grafana dashboards and datasources
├── src/main/java/com/iqkv/docintelligence/catalogservice/
│   ├── {domain}/       # Feature module (vertical slice)
│   │   ├── {Entity}.java
│   │   ├── {Entity}Mapper.java
│   │   ├── {Entity}Service.java
│   │   ├── {Entity}RestResource.java
│   │   ├── Admin{Entity}RestResource.java
│   │   └── dto/
│   ├── infrastructure/ # Spring config, security, MyBatis, RabbitMQ setup
│   └── shared/         # Common exceptions, utilities, value objects
```

## License

This project is licensed under the Apache License. See the [LICENSE](LICENSE) file for details.

## Branch Strategy

> **Template note:** The strategy below is the default shipped with this template. Replace it with whatever model fits your team — update branch names, CI triggers, and protection rules to match.

This template uses **scaled trunk-based development**:

- `dev` is the trunk — always stable and deployable
- Short-lived feature/bugfix branches merge into `dev` via pull request
- Releases are cut from `dev` as annotated tags (`v1.2.0`) — no separate release branch

```
dev  ← trunk
├── feature/*
├── bugfix/*
├── improvement/*
├── hotfix/*
└── rfc/*
```

## Contributing

Please read our [Contributing Guidelines](.github/CONTRIBUTING.md) and [Code of Conduct](.github/CODE_OF_CONDUCT.md).

---

## 🧩 Architecture

- **Ollama**: LLM connectivity configured via `spring.ai.ollama.*` (`SPRING_AI_OLLAMA_BASE_URL`, `SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL`)
- **Persistence**: MyBatis with XML mappers + PostgreSQL; Liquibase manages schema migrations
- **Object storage**: MinIO S3-compatible client (`OBJECTSTORAGE_*`); the `catalog-assets` bucket stays private and clients receive presigned URLs
- **Messaging**: RabbitMQ consumer for IAM tenant provisioning events (`tenant.created` / `tenant.updated`) bound to the shared `iqkv.events` exchange; `iqkv.messaging.rabbitmq.enabled` toggle — disabled in base, enabled per profile
- **Security**: Spring Security + OAuth2 Resource Server; RS256 JWT validated via public key; `@PreAuthorize` on every endpoint
- **Multi-tenancy**: `ROLLOUT_MODE` (`MULTI_TENANT` | `SINGLE_TENANT`) — must be identical across all platform services
- **Credential convention**: DB users follow `svc_{service}_dba`, RabbitMQ users follow `svc_{service}_rmq`
- **Observability**: Micrometer + Prometheus; structured JSON logging with Logstash encoder; health probes for Kubernetes
- **GitHub Integration**: Issue templates, labels, Dependabot, and CI workflows
- **Quality Tools**: Checkstyle, JaCoCo (60% gate), ArchUnit, commit convention enforcement

> See [AGENTS.md](AGENTS.md) for repository structure, DDD patterns, and agent guidelines.
