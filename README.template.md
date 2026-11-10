# Document Intelligence Catalog Service 📇

A multi-tenant, JWT-secured microservice on the iQ Key Value platform, backed by PostgreSQL, RabbitMQ, MinIO, and Ollama.

## About

The Catalog Service provides:

- **Master venue catalog** — a platform-curated venue directory exposed read-only to tenants, including asset/photo reads, and with full CRUD plus provenance-aware asset reads to platform administrators
- **Venue deck uploads** — two-phase presigned MinIO upload for tenant venue deck PDFs (initiate → direct upload → confirm), with an upload lifecycle status of `UPLOADED` → `PARSING` → `EXTRACTED` / `FAILED`
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

Read-only tenant surface. Responses use the public projection — `confidence`, `source`, raw `metadata`, and asset provenance fields (`sourceUrl`, `provider`) are not exposed.

| Method | Path                         | Auth         | Description                                                                            |
| :----- | :--------------------------- | :----------- | :------------------------------------------------------------------------------------- |
| `GET`  | `/master-venues`             | JWT `MEMBER` | Search by keyword (`search`) and exact city (`city`), paginated via `limit` / `offset` |
| `GET`  | `/master-venues/{id}`        | JWT `MEMBER` | Fetch a single master venue by UUID                                                    |
| `GET`  | `/master-venues/{id}/assets` | JWT `MEMBER` | List all assets of a venue, ordered by `displayOrder`, without ingestion provenance    |
| `GET`  | `/master-venues/{id}/photos` | JWT `MEMBER` | List only `PHOTO` assets for gallery use, ordered by `displayOrder`                    |

### Venues

Full CRUD over the tenant's own venue records. Read endpoints require the `MEMBER`, `TENANT_OWNER`, or `ADMIN` authority; write endpoints require `TENANT_OWNER` or `ADMIN`. Tenant context is resolved from the `X-Tenant-ID` header or JWT `tenant_id` claim. Responses use the safe projection — `metadata`, `confidence` and `source` are not exposed; the optional `masterVenueId` relation key is kept.

| Method   | Path           | Auth                                    | Description                                                                                            |
| :------- | :------------- | :-------------------------------------- | :----------------------------------------------------------------------------------------------------- |
| `GET`    | `/venues`      | JWT `MEMBER`/`TENANT_OWNER`/`ADMIN`     | Search by keyword (`search`) and exact city (`city`), with optional sort; paginated via `limit` / `offset` |
| `GET`    | `/venues/{id}` | JWT `MEMBER`/`TENANT_OWNER`/`ADMIN`     | Fetch a single tenant venue by UUID                                                                    |
| `POST`   | `/venues`      | JWT `TENANT_OWNER`/`ADMIN`              | Create a venue in the active tenant schema; curation fields initialised to safe defaults; returns `201` |
| `PUT`    | `/venues/{id}` | JWT `TENANT_OWNER`/`ADMIN`              | Full replace of tenant-visible fields; curation fields are not modified; returns `404` if not found   |
| `PATCH`  | `/venues/{id}` | JWT `TENANT_OWNER`/`ADMIN`              | Partial update — only non-null fields are overwritten; curation fields are never modified              |
| `DELETE` | `/venues/{id}` | JWT `TENANT_OWNER`/`ADMIN`              | Permanently deletes the venue; returns `204`                                                           |
| `DELETE` | `/venues`      | JWT `TENANT_OWNER`/`ADMIN`              | Bulk delete up to 100 venues by id list; idempotent; returns `204`                                     |

`DELETE /venues` takes a JSON body of `{"ids": [uuid, ...]}` (1–100 ids). Ids that match no venue are silently ignored; empty or oversized lists are rejected with `400`.

### Venue assets

Two-phase presigned upload for venue photos and attachments, plus list and detail reads. Requires `TENANT_OWNER` or `MEMBER` authority; tenant context is resolved from the `X-Tenant-ID` header or JWT `tenant_id` claim. Assets are nested under their owning venue.

| Method | Path                                   | Auth                        | Description                                                                                                                             |
| :----- | :------------------------------------- | :-------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/venues/{venueId}/assets/initiate`     | JWT `TENANT_OWNER`/`MEMBER` | Validate declared content type and size, mint a presigned S3 `PUT` URL, return `assetId` + `objectKey`; no row persisted until confirm |
| `POST` | `/venues/{venueId}/assets/{id}/confirm` | JWT `TENANT_OWNER`/`MEMBER` | Validate object key and confirm object exists in storage; persists asset metadata; returns `400` on key mismatch or missing object      |
| `GET`  | `/venues/{venueId}/assets`              | JWT `TENANT_OWNER`/`MEMBER` | List all assets of the venue ordered by `displayOrder`                                                                                  |
| `GET`  | `/venues/{venueId}/assets/photos`       | JWT `TENANT_OWNER`/`MEMBER` | List only `PHOTO` assets for gallery use, ordered by `displayOrder`                                                                     |
| `GET`  | `/venues/{venueId}/assets/{id}`         | JWT `TENANT_OWNER`/`MEMBER` | Fetch a single asset; returns `404` when the venue or asset is not found or not owned by that venue                                     |

The client uploads directly to MinIO using the presigned `PUT` URL returned by `initiate`, then calls `confirm` with the `objectKey`. The object key is tenant-scoped, so `confirm` validates ownership by exact key match.

### Venue decks

Two-phase presigned upload for tenant venue deck PDFs, plus list and detail reads. Requires the `TENANT_OWNER` or `MEMBER` authority; tenant rows are isolated by tenant schema.

| Method | Path                        | Auth                        | Description                                                                                                                       |
| :----- | :-------------------------- | :-------------------------- | :-------------------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/venue-decks/initiate`     | JWT `TENANT_OWNER`/`MEMBER` | Validate the declared file (`fileName`, `contentType`, `sizeBytes`) and return a presigned `PUT` URL + `documentId` + `objectKey` |
| `POST` | `/venue-decks/{id}/confirm` | JWT `TENANT_OWNER`/`MEMBER` | Validate the object key and the uploaded object (`statObject`), persist file metadata with status `UPLOADED`                      |
| `GET`  | `/venue-decks`              | JWT `TENANT_OWNER`/`MEMBER` | List venue decks of the active tenant, ordered by most recently created, paginated via `limit` / `offset`                         |
| `GET`  | `/venue-decks/{id}`         | JWT `TENANT_OWNER`/`MEMBER` | Fetch a single venue deck by UUID                                                                                                 |

The client uploads directly to MinIO using the presigned `PUT` URL returned by `initiate`, then calls `confirm` with the `objectKey` and `fileName`. The object key is tenant-scoped (`docintelligence/tenant/{tenantKey}/venue-decks/{documentId}`), so confirm validates ownership by exact match. A document carries the upload lifecycle status `UPLOADED`, later transitioning to `PARSING` → `EXTRACTED`, or `FAILED`. `initiate` and `confirm` answer `400` for an unsupported content type, an oversized file, an object key mismatch, or a missing storage object; `{id}` paths answer `404` when the deck does not exist.

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

`DELETE /admin/master-venues` takes a JSON body of `{"ids": [uuid, ...]}` (1–100 ids) and removes every matching venue in one transaction. Deleting a venue removes its aliases and external provider records through the database cascade; ids that match no venue are silently ignored, so the request can be retried safely. Empty or oversized id lists are rejected with `400`.

### Venues (admin)

Full CRUD over tenant venue records. Requires the `PLATFORM_ADMIN` authority; each request is scoped to the tenant in the `{tenantKey}` path variable (`/api/v1/catalog/admin/{tenantKey}/venues`).

| Method   | Path                                        | Auth                 | Description                                                                            |
| :------- | :------------------------------------------ | :------------------- | :------------------------------------------------------------------------------------- |
| `GET`    | `/admin/{tenantKey}/venues`                 | JWT `PLATFORM_ADMIN` | Search by keyword (`search`) and exact city (`city`), paginated via `limit` / `offset` |
| `GET`    | `/admin/{tenantKey}/venues/search/semantic` | JWT `PLATFORM_ADMIN` | Semantic (vector) search over tenant venues — AI query planner + cosine similarity     |
| `GET`    | `/admin/{tenantKey}/venues/{id}`            | JWT `PLATFORM_ADMIN` | Fetch a single tenant venue by UUID                                                    |
| `POST`   | `/admin/{tenantKey}/venues`                 | JWT `PLATFORM_ADMIN` | Create a tenant venue; returns `201`                                                   |
| `PUT`    | `/admin/{tenantKey}/venues/{id}`            | JWT `PLATFORM_ADMIN` | Replace a tenant venue in full                                                         |
| `PATCH`  | `/admin/{tenantKey}/venues/{id}`            | JWT `PLATFORM_ADMIN` | Partially update a tenant venue; omitted fields keep their current value               |
| `DELETE` | `/admin/{tenantKey}/venues/{id}`            | JWT `PLATFORM_ADMIN` | Delete a tenant venue; returns `204`                                                   |

A venue carries an optional `masterVenueId` relation key to a master catalog record. `POST` and `PUT` take a write payload (`name`, `address`, `city`, `locationLat`, `locationLng`, `metadata`, `confidence`, `source`, `masterVenueId`); `PATCH` takes the same shape with every field optional. Omitted `metadata` defaults to the canonical empty document and omitted `confidence` to `1.0`. `{id}` paths answer `404` when the venue does not exist in that tenant schema. `GET .../search/semantic` runs an AI query planner that extracts hard filters (city, venue type, capacity, catering, amenities, accessibility) and ranks the embedded venues by cosine similarity using the per-tenant pgvector index; only venues already carrying an embedding are considered, and a disabled or unreachable planner falls back to embedding the raw query with no filters.

### Venue assets (admin)

Two-phase presigned upload for tenant venue photos and attachments, plus list and detail reads, on behalf of the tenant named in the `{tenantKey}` path variable (`/api/v1/catalog/admin/{tenantKey}/venues/{venueId}/assets`). Requires the `PLATFORM_ADMIN` authority; each request is scoped to that tenant's schema and `uploadedBy` is the calling administrator's `user_id`.

| Method | Path                                                        | Auth                 | Description                                                                                                                             |
| :----- | :---------------------------------------------------------- | :------------------- | :-------------------------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/admin/{tenantKey}/venues/{venueId}/assets/initiate`       | JWT `PLATFORM_ADMIN` | Validate declared content type and size, mint a presigned S3 `PUT` URL, return `assetId` + `objectKey`; no row persisted until confirm |
| `POST` | `/admin/{tenantKey}/venues/{venueId}/assets/{id}/confirm`   | JWT `PLATFORM_ADMIN` | Validate object key and confirm object exists in storage; persists asset metadata; returns `400` on key mismatch or missing object      |
| `GET`  | `/admin/{tenantKey}/venues/{venueId}/assets`                | JWT `PLATFORM_ADMIN` | List all assets of the venue ordered by `displayOrder`                                                                                  |
| `GET`  | `/admin/{tenantKey}/venues/{venueId}/assets/photos`         | JWT `PLATFORM_ADMIN` | List only `PHOTO` assets for gallery use, ordered by `displayOrder`                                                                     |
| `GET`  | `/admin/{tenantKey}/venues/{venueId}/assets/{id}`           | JWT `PLATFORM_ADMIN` | Fetch a single asset; returns `404` when the venue or asset is not found or not owned by that venue                                     |

The upload flow and error semantics match the tenant `Venue assets` section above; tenant resolution comes from the `{tenantKey}` path variable instead of the request tenant context.

### Venue decks (admin)

Two-phase presigned upload for tenant venue deck PDFs, plus list and detail reads, on behalf of the tenant named in the `{tenantKey}` path variable (`/api/v1/catalog/admin/{tenantKey}/venue-decks`). Requires the `PLATFORM_ADMIN` authority; each request is scoped to that tenant's schema and `uploadedBy` is the calling administrator's `user_id`.

| Method | Path                                          | Auth                 | Description                                                                                                                       |
| :----- | :-------------------------------------------- | :------------------- | :-------------------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/admin/{tenantKey}/venue-decks/initiate`     | JWT `PLATFORM_ADMIN` | Validate the declared file (`fileName`, `contentType`, `sizeBytes`) and return a presigned `PUT` URL + `documentId` + `objectKey` |
| `POST` | `/admin/{tenantKey}/venue-decks/{id}/confirm` | JWT `PLATFORM_ADMIN` | Validate the object key and the uploaded object (`statObject`), persist file metadata with status `UPLOADED`                      |
| `GET`  | `/admin/{tenantKey}/venue-decks`              | JWT `PLATFORM_ADMIN` | List venue decks of the tenant, ordered by most recently created, paginated via `limit` / `offset`                                |
| `GET`  | `/admin/{tenantKey}/venue-decks/{id}`         | JWT `PLATFORM_ADMIN` | Fetch a single venue deck by UUID                                                                                                 |

The upload flow and error semantics match the tenant `Venue decks` section above; the difference is tenant resolution — the tenant comes from the `{tenantKey}` path variable instead of the request tenant context, because admin paths are not intercepted by `TenantExtractionFilter`.

> Auth legend: `public` = no token required; `JWT` = valid Bearer token required; `JWT TENANT_OWNER/MEMBER` = tenant owner or member authority required; `JWT PLATFORM_ADMIN` = platform administrator authority required.

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
├── mastervenue/      # Master venue catalog — tenant read-only + admin full CRUD
│   ├── dto/          # Request/response records and the DTO mapper
│   ├── AdminMasterVenueRestResource.java
│   ├── MasterVenueRestResource.java
│   ├── MasterVenueService.java
│   └── MasterVenueServiceImpl.java
├── venue/            # Tenant venue catalog — tenant CRUD + admin full CRUD + semantic search
│   ├── dto/          # Request/response records and the DTO mapper
│   ├── AdminVenueAssetRestResource.java
│   ├── AdminVenueRestResource.java
│   ├── VenueAssetRestResource.java
│   ├── VenueAssetService.java
│   ├── VenueAssetServiceImpl.java
│   ├── VenueRestResource.java
│   ├── VenueService.java
│   └── VenueServiceImpl.java
├── venuedeck/        # Venue deck upload feature — two-phase presigned MinIO upload + list/detail
│   ├── dto/          # Request/response records and the DTO mapper
│   ├── AdminVenueDeckRestResource.java
│   ├── VenueDeckRestResource.java
│   ├── VenueDeckService.java
│   └── VenueDeckServiceImpl.java
├── ping/             # Reachability probes across the three security tiers
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
