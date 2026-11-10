# Document Intelligence Catalog Service 🚀

GitHub template for the iQ Key Value platform. Extends the standard `foundation-microservice-project-layout` with a multi-tenant, JWT-secured catalog service backed by PostgreSQL, RabbitMQ, MinIO, and Ollama.

## About

This template provides:

- **Venue catalog** — multi-tenant venue CRUD with safe projection (curation fields hidden from tenant users); write endpoints require `TENANT_OWNER`/`ADMIN` authority; read endpoints are open to `MEMBER`
- **Venue asset uploads** — two-phase presigned MinIO upload for venue photos and attachments (initiate → direct upload → confirm) nested under the owning venue, with tenant and admin surfaces mirrored
- **Master venue catalog** — a platform-curated venue directory exposed read-only to tenants, including asset/photo reads, and with full CRUD plus provenance-aware asset reads to platform administrators
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

## API

Base path: `/api/v1/catalog`

### Ping

| Method | Path           | Auth                 | Description                                                           |
| :----- | :------------- | :------------------- | :-------------------------------------------------------------------- |
| `GET`  | `/ping`        | public               | Unauthenticated reachability probe — no JWT or tenant header required |
| `GET`  | `/tenant/ping` | JWT + `X-Tenant-ID`  | Tenant-scoped probe — echoes the resolved tenant key                  |
| `GET`  | `/admin/ping`  | JWT `PLATFORM_ADMIN` | Admin probe — echoes the caller's `user_id` claim                     |

### Master venues

Read-only tenant surface. Responses use the public projection — `confidence`, `source`, raw `metadata`, and asset provenance fields (`sourceUrl`, `provider`) are not exposed.

| Method | Path                         | Auth         | Description                                                                            |
| :----- | :--------------------------- | :----------- | :------------------------------------------------------------------------------------- |
| `GET`  | `/master-venues`             | JWT `MEMBER` | Search by keyword (`search`) and exact city (`city`), paginated via `limit` / `offset` |
| `GET`  | `/master-venues/{id}`        | JWT `MEMBER` | Fetch a single master venue by UUID                                                    |
| `GET`  | `/master-venues/{id}/assets` | JWT `MEMBER` | List all assets of a venue, ordered by `displayOrder`, without ingestion provenance    |
| `GET`  | `/master-venues/{id}/photos` | JWT `MEMBER` | List only `PHOTO` assets for gallery use, ordered by `displayOrder`                    |

### Venues

Full CRUD over the tenant's own venue records. Read endpoints require the `MEMBER` authority; write endpoints require `TENANT_OWNER` or `ADMIN`. Tenant context is resolved from the `X-Tenant-ID` header or JWT `tenant_id` claim. Responses use the safe projection — `metadata`, `confidence` and `source` are not exposed; the optional `masterVenueId` relation key is kept.

| Method   | Path           | Auth                       | Description                                                                                                 |
| :------- | :------------- | :------------------------- | :---------------------------------------------------------------------------------------------------------- |
| `GET`    | `/venues`      | JWT `MEMBER`               | Search by keyword (`search`) and exact city (`city`), with optional sort; paginated via `limit` / `offset`  |
| `GET`    | `/venues/{id}` | JWT `MEMBER`               | Fetch a single tenant venue by UUID                                                                         |
| `POST`   | `/venues`      | JWT `TENANT_OWNER`/`ADMIN` | Create a venue in the active tenant schema; curation fields initialised to safe defaults; returns `201`     |
| `PUT`    | `/venues/{id}` | JWT `TENANT_OWNER`/`ADMIN` | Full replace of tenant-visible fields; curation fields are not modified; returns `404` if not found         |
| `PATCH`  | `/venues/{id}` | JWT `TENANT_OWNER`/`ADMIN` | Partial update — only non-null fields are overwritten; curation fields are never modified                   |
| `DELETE` | `/venues/{id}` | JWT `TENANT_OWNER`/`ADMIN` | Permanently deletes the venue; returns `204`                                                                |
| `DELETE` | `/venues`      | JWT `TENANT_OWNER`/`ADMIN` | Bulk delete up to 100 venues by id list; idempotent; returns `204`                                          |

`DELETE /venues` takes a JSON body of `{"ids": [uuid, ...]}` (1–100 ids). Ids that match no venue are silently ignored; empty or oversized lists are rejected with `400`.

### Venue assets

Two-phase presigned upload for venue photos and attachments, plus list and detail reads. Requires `TENANT_OWNER` or `MEMBER` authority; tenant context is resolved from the `X-Tenant-ID` header or JWT `tenant_id` claim.

| Method | Path                                    | Auth                        | Description                                                                                                                              |
| :----- | :-------------------------------------- | :-------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/venues/{venueId}/assets/initiate`     | JWT `TENANT_OWNER`/`MEMBER` | Validate declared content type and size, mint a presigned S3 `PUT` URL, return `assetId` + `objectKey`; no row persisted until confirm  |
| `POST` | `/venues/{venueId}/assets/{id}/confirm` | JWT `TENANT_OWNER`/`MEMBER` | Validate object key and confirm object exists in storage; persists asset metadata; returns `400` on key mismatch or missing object       |
| `GET`  | `/venues/{venueId}/assets`              | JWT `TENANT_OWNER`/`MEMBER` | List all assets of the venue ordered by `displayOrder`                                                                                   |
| `GET`  | `/venues/{venueId}/assets/photos`       | JWT `TENANT_OWNER`/`MEMBER` | List only `PHOTO` assets for gallery use, ordered by `displayOrder`                                                                      |
| `GET`  | `/venues/{venueId}/assets/{id}`         | JWT `TENANT_OWNER`/`MEMBER` | Fetch a single asset; returns `404` when the venue or asset is not found or not owned by that venue                                      |

### Admin

Full CRUD over the master venue catalog, using the admin projection (all curation fields included). Admin asset reads also expose ingestion provenance such as `sourceUrl` and `provider`.

| Method   | Path                               | Auth                 | Description                                                              |
| :------- | :--------------------------------- | :------------------- | :----------------------------------------------------------------------- |
| `GET`    | `/admin/master-venues`             | JWT `PLATFORM_ADMIN` | Search across the whole catalog, paginated                               |
| `GET`    | `/admin/master-venues/{id}`        | JWT `PLATFORM_ADMIN` | Fetch a single master venue by UUID                                      |
| `GET`    | `/admin/master-venues/{id}/assets` | JWT `PLATFORM_ADMIN` | List all assets of a venue, ordered by `displayOrder`, with provenance   |
| `GET`    | `/admin/master-venues/{id}/photos` | JWT `PLATFORM_ADMIN` | List only `PHOTO` assets for gallery use                                 |
| `POST`   | `/admin/master-venues`             | JWT `PLATFORM_ADMIN` | Create a master venue; returns `201` with the created resource           |
| `PUT`    | `/admin/master-venues/{id}`        | JWT `PLATFORM_ADMIN` | Replace a master venue in full                                           |
| `PATCH`  | `/admin/master-venues/{id}`        | JWT `PLATFORM_ADMIN` | Partially update a master venue; omitted fields keep their current value |
| `DELETE` | `/admin/master-venues/{id}`        | JWT `PLATFORM_ADMIN` | Delete a master venue; returns `204`                                     |
| `DELETE` | `/admin/master-venues`             | JWT `PLATFORM_ADMIN` | Bulk delete master venues; idempotent, returns `204`                     |

`DELETE /admin/master-venues` takes a JSON body of `{"ids": [uuid, ...]}` (1–100 ids) and removes every matching venue in one transaction. Ids that match no venue are silently ignored, so the request can be retried safely; empty or oversized id lists are rejected with `400`.

> Auth legend: `public` = no token required; `JWT` = valid Bearer token required; `JWT MEMBER` = tenant member authority required; `JWT TENANT_OWNER/ADMIN` = tenant owner or admin authority required; `JWT PLATFORM_ADMIN` = platform administrator authority required.

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
│   ├── mastervenue/    # Master venue catalog — tenant read-only + admin full CRUD
│   │   ├── dto/
│   │   ├── AdminMasterVenueRestResource.java
│   │   ├── MasterVenueRestResource.java
│   │   ├── MasterVenueService.java
│   │   └── MasterVenueServiceImpl.java
│   ├── venue/          # Tenant venue catalog — tenant CRUD + admin full CRUD + semantic search
│   │   ├── dto/
│   │   ├── AdminVenueAssetRestResource.java
│   │   ├── AdminVenueRestResource.java
│   │   ├── VenueAssetRestResource.java
│   │   ├── VenueAssetService.java
│   │   ├── VenueAssetServiceImpl.java
│   │   ├── VenueRestResource.java
│   │   ├── VenueService.java
│   │   └── VenueServiceImpl.java
│   ├── venuedeck/      # Venue deck upload — two-phase presigned MinIO upload + list/detail
│   │   ├── dto/
│   │   ├── AdminVenueDeckRestResource.java
│   │   ├── VenueDeckRestResource.java
│   │   ├── VenueDeckService.java
│   │   └── VenueDeckServiceImpl.java
│   ├── ping/           # Reachability probes across the three security tiers
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
