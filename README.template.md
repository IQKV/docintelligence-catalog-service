# Document Intelligence Catalog Service 📇

A multi-tenant, JWT-secured microservice on the iQ Key Value platform, backed by PostgreSQL, RabbitMQ, MinIO, and Ollama.

## About

The Catalog Service provides:

- **Master venue catalog** — a platform-curated venue directory exposed read-only to tenants and with full CRUD to platform administrators
- **Ollama connectivity** — LLM client pre-wired to a local Ollama instance via `spring.ai.ollama.*` (base URL and model configurable per environment)
- **Multi-tenant isolation** — tenant context resolved from the `X-Tenant-ID` header or the JWT `tenant_id` claim before every request
- **Persistence** — data stored in PostgreSQL via MyBatis; Liquibase manages system and per-tenant schema migrations
- **Object storage** — MinIO (S3-compatible) client bound to a private `catalog-assets` bucket; clients receive presigned URLs instead of direct object access
- **Security** — stateless JWT resource server (RS256); public endpoints can be exposed while admin endpoints require `PLATFORM_ADMIN` authority
- **Messaging** — RabbitMQ consumer for IAM tenant provisioning events (`tenant.created` / `tenant.updated`) on `docintelligence.catalog.tenant.provisioning`, gated by `iqkv.messaging.rabbitmq.enabled`

## Quick Links

- [API Documentation](./docs/api/README.md)
- [Architecture Overview](./docs/architecture/README.md)
- [Deployment Guide](./docs/deployment/README.md)
- [Contributing Guidelines](.github/CONTRIBUTING.md)

## API

Base path: `/api/v1/catalog`

### Ping

| Method | Path           | Auth                 | Description                                                           |
| :----- | :------------- | :------------------- | :-------------------------------------------------------------------- |
| `GET`  | `/ping`        | public               | Unauthenticated reachability probe — no JWT or tenant header required |
| `GET`  | `/tenant/ping` | JWT + `X-Tenant-ID`  | Tenant-scoped probe — echoes the resolved tenant key                  |
| `GET`  | `/admin/ping`  | JWT `PLATFORM_ADMIN` | Admin probe — echoes the caller's `user_id` claim                     |

### Master venues

Read-only tenant surface. Responses use the public projection — `confidence`, `source`, and the raw `metadata` document are not exposed.

| Method | Path                  | Auth         | Description                                                                            |
| :----- | :-------------------- | :----------- | :------------------------------------------------------------------------------------- |
| `GET`  | `/master-venues`      | JWT `MEMBER` | Search by keyword (`search`) and exact city (`city`), paginated via `limit` / `offset` |
| `GET`  | `/master-venues/{id}` | JWT `MEMBER` | Fetch a single master venue by UUID                                                    |

### Admin

Full CRUD over the master venue catalog, using the admin projection (all curation fields included).

| Method   | Path                        | Auth                 | Description                                                              |
| :------- | :-------------------------- | :------------------- | :----------------------------------------------------------------------- |
| `GET`    | `/admin/master-venues`      | JWT `PLATFORM_ADMIN` | Search across the whole catalog, paginated                               |
| `GET`    | `/admin/master-venues/{id}` | JWT `PLATFORM_ADMIN` | Fetch a single master venue by UUID                                      |
| `POST`   | `/admin/master-venues`      | JWT `PLATFORM_ADMIN` | Create a master venue; returns `201` with the created resource           |
| `PUT`    | `/admin/master-venues/{id}` | JWT `PLATFORM_ADMIN` | Replace a master venue in full                                           |
| `PATCH`  | `/admin/master-venues/{id}` | JWT `PLATFORM_ADMIN` | Partially update a master venue; omitted fields keep their current value |
| `DELETE` | `/admin/master-venues/{id}` | JWT `PLATFORM_ADMIN` | Delete a master venue; returns `204`                                     |

> Auth legend: `public` = no token required; `JWT` = valid Bearer token required; `JWT MEMBER` = tenant member authority required; `JWT PLATFORM_ADMIN` = platform administrator authority required.

## Tech Stack

- Java 25 / Spring Boot (latest via parent POM)
- Spring AI Ollama starter (local LLM connectivity)
- MyBatis 3.x (no JPA) + PostgreSQL 17
- Liquibase for schema migrations
- RabbitMQ for async messaging
- Spring Security + OAuth2 Resource Server (RS256 JWT)
- MinIO client for S3-compatible object storage
- Micrometer + Prometheus

## Observability

- **Actuator endpoints** — `health` (liveness + readiness probes), `info`, `metrics`, and `prometheus` exposed on the management port (`8081`)
- **Structured logging** — JSON log output via the Logstash encoder; every request carries a correlation ID set by `CorrelationIdFilter`
- **Metrics** — no service-specific custom meters yet; the Micrometer/Prometheus registry exposes the standard JVM, HTTP, and Spring Boot meters

## Prerequisites

- JDK 25 (Eclipse Temurin)
- Maven 3.9+
- Node.js >= 22.15.0 & pnpm >= 10.33.2 (git hooks)
- Docker & Docker Compose
- [Ollama](https://ollama.com) running locally with at least one model pulled (e.g. `ollama pull llama3.2`)

## Quick Start

```bash
# Clone the repository
git clone https://github.com/IQKV/docintelligence-catalog-service.git
cd docintelligence-catalog-service

# Install git hooks
pnpm install

# Pull a model into Ollama (one-time)
ollama pull llama3.2

# Copy environment variables
cp .env.example .env.local
# Defaults point to Ollama on localhost:11434 — no edits needed for first-run

# Start infrastructure dependencies (PostgreSQL, RabbitMQ, MinIO)
docker compose up -d

# Run the service
./mvnw spring-boot:run -Pdev
# → API:          http://localhost:8080
# → Actuator:     http://localhost:8081/actuator/health
# → Swagger:      http://localhost:8080/swagger-ui.html
# → MinIO:        http://localhost:9003 (console)
```

## Environment Variables

| Variable                              | Default                  | Description                                   |
| :------------------------------------ | :----------------------- | :-------------------------------------------- |
| `DB_HOST`                             | `localhost`              | PostgreSQL host                               |
| `DB_PORT`                             | `5432`                   | PostgreSQL port                               |
| `DB_NAME`                             | `catalogservice`         | Database name                                 |
| `DB_USERNAME`                         | `svc_catalog_dba`        | Database user                                 |
| `DB_PASSWORD`                         | `svc_catalog_dba`        | Database password                             |
| `RABBITMQ_HOST`                       | `localhost`              | RabbitMQ host                                 |
| `RABBITMQ_PORT`                       | `5672`                   | RabbitMQ AMQP port                            |
| `RABBITMQ_USERNAME`                   | `svc_catalog_rmq`        | RabbitMQ user                                 |
| `RABBITMQ_PASSWORD`                   | `svc_catalog_rmq`        | RabbitMQ password                             |
| `OBJECTSTORAGE_ENDPOINT`              | `http://localhost:9000`  | S3-compatible endpoint (MinIO in SIT/UAT)     |
| `OBJECTSTORAGE_PUBLIC_ENDPOINT`       | _(empty)_                | Public URL used to rewrite presigned URLs     |
| `OBJECTSTORAGE_ACCESS_KEY`            | `iqkv`                   | S3 access key                                 |
| `OBJECTSTORAGE_SECRET_KEY`            | `iqkv_password`          | S3 secret key                                 |
| `OBJECTSTORAGE_BUCKET_NAME`           | `catalog-assets`         | Private bucket holding catalog assets         |
| `OBJECTSTORAGE_REGION`                | `us-east-1`              | S3 region                                     |
| `OBJECTSTORAGE_SSL`                   | `false`                  | Use HTTPS for the S3 endpoint                 |
| `SPRING_PROFILES_ACTIVE`              | `local`                  | Active Spring profile                         |
| `MESSAGING_ENABLED`                   | `false`                  | Maps to `iqkv.messaging.rabbitmq.enabled`     |
| `SPRING_AI_OLLAMA_BASE_URL`           | `http://localhost:11434` | Ollama server URL                             |
| `SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL` | `llama3.2`               | Model to use (must be pulled in Ollama first) |
| `ROLLOUT_MODE`                        | `MULTI_TENANT`           | Platform rollout mode                         |

> Copy `.env.example` to `.env.local` / `.env.uat` / `.env.prd` and fill in values per environment. The defaults point to a local Ollama instance — no API key needed for local development.
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

# Run with full platform stack
docker compose -f compose.container.yaml up -d
```

## Monitoring

| Endpoint                   | Description                      |
| :------------------------- | :------------------------------- |
| `GET /actuator/health`     | Liveness + readiness probes      |
| `GET /actuator/info`       | Build info and platform metadata |
| `GET /actuator/metrics`    | Application metrics              |
| `GET /actuator/prometheus` | Prometheus scrape endpoint       |
| `GET /swagger-ui.html`     | API documentation                |

## Project Structure

```
src/main/java/com/iqkv/docintelligence/catalogservice/
├── mastervenue/      # Core bounded context — master venue catalog (tenant read + admin CRUD)
│   ├── dto/          # Request/response records and the DTO mapper
│   ├── AdminMasterVenueRestResource.java
│   ├── MasterVenue.java
│   ├── MasterVenueMapper.java
│   ├── MasterVenueRestResource.java
│   ├── MasterVenueService.java
│   └── MasterVenueServiceImpl.java
├── ping/             # Example feature module — reachability probes across the three security tiers
├── infrastructure/   # Technical concerns: Spring config, RabbitMQ, Security, MyBatis, Object storage
├── shared/           # Common exceptions, utilities, value objects
└── CatalogServiceApplication.java
```

## License

This project is licensed under the Apache License. See the [LICENSE](LICENSE) file for details.

## Contributing

Please read our [Contributing Guidelines](.github/CONTRIBUTING.md) and [Code of Conduct](.github/CODE_OF_CONDUCT.md).

---

## 🧩 Architecture

- **Ollama**: LLM connectivity configured via `spring.ai.ollama.*` (`SPRING_AI_OLLAMA_BASE_URL`, `SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL`)
- **Persistence**: MyBatis with XML mappers + PostgreSQL; Liquibase manages system and per-tenant schema migrations
- **Object storage**: MinIO S3-compatible client (`OBJECTSTORAGE_*`); the `catalog-assets` bucket stays private and clients receive presigned URLs
- **Messaging**: RabbitMQ consumer for IAM tenant provisioning events (`tenant.created` / `tenant.updated`) bound to the shared `iqkv.events` exchange; `iqkv.messaging.rabbitmq.enabled` toggle — disabled in base profile, enabled per environment
- **Security**: Spring Security + OAuth2 Resource Server; RS256 JWT validated via public key or JWKS URI; `@PreAuthorize` on protected endpoints; tenant context resolved before request handling
- **API tiers**: public (`/ping`), tenant (`MEMBER` authority, read-only), and admin (`PLATFORM_ADMIN` authority, full CRUD) — mirrored per bounded context
- **Multi-tenancy**: `ROLLOUT_MODE` (`MULTI_TENANT` | `SINGLE_TENANT`) — must be identical across all platform services
- **Observability**: Micrometer + Prometheus; structured JSON logging with Logstash encoder; health probes for Kubernetes readiness/liveness
- **Quality Tools**: Checkstyle, JaCoCo (60% gate), ArchUnit, commit convention enforcement

> See [AGENTS.md](AGENTS.md) for repository structure, DDD patterns, and agent guidelines.
