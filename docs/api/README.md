## 📜 API Documentation

Base path: `/api/v1/catalog`

All endpoints require a valid RS256 JWT issued by the IAM service unless marked as public.
The JWT must be passed as a `Bearer` token in the `Authorization` header.

---

### Ping (Reachability Probes)

| Method | Path           | Auth                 | Description                                                           |
| ------ | -------------- | -------------------- | --------------------------------------------------------------------- |
| `GET`  | `/ping`        | public               | Unauthenticated reachability probe — no JWT or tenant header required |
| `GET`  | `/tenant/ping` | JWT + `X-Tenant-ID`  | Tenant-scoped probe — echoes resolved tenant key                      |
| `GET`  | `/admin/ping`  | JWT `PLATFORM_ADMIN` | Admin probe — echoes caller's `user_id` claim                         |

`GET /ping` is suitable for infrastructure health checks and smoke tests that run before auth is available.

`GET /tenant/ping` verifies that tenant resolution (from `X-Tenant-ID` header or JWT `tenant_id` claim) is working correctly.

`GET /admin/ping` confirms the caller's JWT is decoded correctly and carries the `PLATFORM_ADMIN` authority.

---

### Master Venues (Tenant Surface)

Read-only access for tenant members (`MEMBER` authority). Tenant context is resolved from the `X-Tenant-ID` header or the JWT `tenant_id` claim before the request is handled.

Responses use the public projection: `confidence`, `source`, the raw `metadata` document and the asset provenance fields (`sourceUrl`, `provider`) are not exposed.

| Method | Path                         | Auth         | Description                                                                                                            |
| ------ | ---------------------------- | ------------ | ---------------------------------------------------------------------------------------------------------------------- |
| `GET`  | `/master-venues`             | JWT `MEMBER` | Search by keyword (`search`) and exact city (`city`), paginated via `limit` (default 20) and `offset`, ordered by name |
| `GET`  | `/master-venues/{id}`        | JWT `MEMBER` | Fetch a single master venue by UUID                                                                                    |
| `GET`  | `/master-venues/{id}/assets` | JWT `MEMBER` | List the venue assets ordered by `displayOrder`, without ingestion provenance                                          |
| `GET`  | `/master-venues/{id}/photos` | JWT `MEMBER` | List the `PHOTO` assets only — the subset the gallery renders                                                          |

Every master venue path answers `400` when the tenant ID cannot be resolved, `401` for a missing or invalid JWT, and `403` when the caller lacks the `MEMBER` authority. The paths carrying an `{id}` additionally answer `404` when the venue does not exist.

---

### Master Venues (Admin Surface)

Full CRUD plus asset reads for platform administrators (`PLATFORM_ADMIN` authority). The `/admin/**` prefix is enforced by `SecurityConfig` and needs no tenant context, because master venues are platform-owned reference data in the `public` schema.

Responses use the admin projection, which includes `confidence`, `source`, the raw `metadata` document and the asset provenance fields (`sourceUrl`, `provider`).

| Method   | Path                               | Auth                 | Description                                                                |
| -------- | ---------------------------------- | -------------------- | -------------------------------------------------------------------------- |
| `GET`    | `/admin/master-venues`             | JWT `PLATFORM_ADMIN` | Search across the whole catalog, paginated                                 |
| `GET`    | `/admin/master-venues/{id}`        | JWT `PLATFORM_ADMIN` | Fetch a single master venue by UUID                                        |
| `GET`    | `/admin/master-venues/{id}/assets` | JWT `PLATFORM_ADMIN` | List the venue assets ordered by `displayOrder`, with ingestion provenance |
| `GET`    | `/admin/master-venues/{id}/photos` | JWT `PLATFORM_ADMIN` | List the `PHOTO` assets only                                               |
| `POST`   | `/admin/master-venues`             | JWT `PLATFORM_ADMIN` | Create a master venue; returns `201` with the created resource             |
| `PUT`    | `/admin/master-venues/{id}`        | JWT `PLATFORM_ADMIN` | Replace a master venue in full                                             |
| `PATCH`  | `/admin/master-venues/{id}`        | JWT `PLATFORM_ADMIN` | Partially update a master venue; omitted fields keep their current value   |
| `DELETE` | `/admin/master-venues/{id}`        | JWT `PLATFORM_ADMIN` | Delete a master venue; returns `204`                                       |
| `DELETE` | `/admin/master-venues`             | JWT `PLATFORM_ADMIN` | Bulk delete master venues by id; idempotent, returns `204`                 |

On creation, omitted `metadata` falls back to the canonical empty document and omitted `confidence` to `1.0`. Deleting a venue removes its aliases and external provider records through the database cascade. `DELETE /admin/master-venues` accepts a JSON body of `{"ids": [uuid, ...]}` (1–100 ids) and removes every matching venue in one transaction; ids that match no venue are silently ignored, so the request can be retried safely, and empty or oversized id lists are rejected with `400`.

---

### Venues (Tenant Surface)

Read-only access to the tenant's own venue records for tenant members (`MEMBER` authority). Tenant context is resolved from the `X-Tenant-ID` header or the JWT `tenant_id` claim; rows are isolated by tenant schema, so a member sees only their tenant's venues.

Responses use the safe projection: `metadata`, `confidence` and `source` are not exposed; the optional `masterVenueId` relation key is kept so a member can see whether a venue is linked to a master catalog entry.

| Method | Path           | Auth         | Description                                                                                                                              |
| ------ | -------------- | ------------ | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `GET`  | `/venues`      | JWT `MEMBER` | Search by keyword (`search`) and exact city (`city`), paginated via `limit` (default 20) and `offset`, ordered by name or recently added |
| `GET`  | `/venues/{id}` | JWT `MEMBER` | Fetch a single tenant venue by UUID                                                                                                      |

Every venue path answers `400` when the tenant ID cannot be resolved, `401` for a missing or invalid JWT, `403` when the caller lacks the `MEMBER` authority, and `404` when the venue does not exist in the tenant schema.

---

### Venues (Admin Surface)

Full CRUD over tenant venue records for platform administrators (`PLATFORM_ADMIN` authority). Each request is scoped to the tenant named in the `{tenantKey}` path variable — the controller sets the tenant context from it, so the query runs against that tenant's `t_{tenantKey}` schema. The path is under `/admin/`, so it needs no `X-Tenant-ID` header.

| Method   | Path                                        | Auth                 | Description                                                                                                   |
| -------- | ------------------------------------------- | -------------------- | ------------------------------------------------------------------------------------------------------------- |
| `GET`    | `/admin/{tenantKey}/venues`                 | JWT `PLATFORM_ADMIN` | Search by keyword (`search`) and exact city (`city`), paginated via `limit` (default 20) and `offset`         |
| `GET`    | `/admin/{tenantKey}/venues/search/semantic` | JWT `PLATFORM_ADMIN` | Semantic (vector) search — AI query planner extracts hard filters, ranks embedded venues by cosine similarity |
| `GET`    | `/admin/{tenantKey}/venues/{id}`            | JWT `PLATFORM_ADMIN` | Fetch a single tenant venue by UUID                                                                           |
| `POST`   | `/admin/{tenantKey}/venues`                 | JWT `PLATFORM_ADMIN` | Create a tenant venue; returns `201`                                                                          |
| `PUT`    | `/admin/{tenantKey}/venues/{id}`            | JWT `PLATFORM_ADMIN` | Replace a tenant venue in full                                                                                |
| `PATCH`  | `/admin/{tenantKey}/venues/{id}`            | JWT `PLATFORM_ADMIN` | Partially update a tenant venue; omitted fields keep their current value                                      |
| `DELETE` | `/admin/{tenantKey}/venues/{id}`            | JWT `PLATFORM_ADMIN` | Delete a tenant venue; returns `204`                                                                          |

`POST` and `PUT` take a write payload of `name`, `address`, `city`, `locationLat`, `locationLng`, `metadata`, `confidence`, `source` and the optional `masterVenueId` relation key. `PATCH` takes the same shape with every field optional — a `null` field means "leave unchanged", so a field (including `masterVenueId`) can be set but not cleared via PATCH. Omitted `metadata` defaults to the canonical empty document and omitted `confidence` to `1.0`. Paths carrying an `{id}` answer `404` when the venue does not exist in that tenant schema. `GET .../search/semantic` runs an AI query planner that extracts hard filters (city, venue type, capacity, catering, amenities, accessibility) and ranks the embedded venues by cosine similarity using the per-tenant pgvector index; only venues already carrying an embedding are considered, and a disabled or unreachable planner falls back to embedding the raw query with no filters.

---

### Venue Decks (Tenant Surface)

Two-phase presigned upload for tenant venue deck PDFs, plus list and detail reads. Requires the `MEMBER` authority. Tenant context is resolved from the `X-Tenant-ID` header or the JWT `tenant_id` claim before the request is handled; rows are isolated by tenant schema, so a tenant sees only its own decks.

**Upload flow:**

1. `POST /venue-decks/initiate` — the client declares `fileName`, `contentType` and `sizeBytes`. The service validates the content type and size against the configured upload policy (`iqkv.objectstorage.upload.allowed-mime-types`, `max-file-size-bytes`), mints a tenant-scoped object key and a presigned S3 `PUT` URL, and returns the `documentId` to use in the confirm call. No row is persisted yet.
2. The client uploads the file directly to MinIO using the presigned `PUT` URL.
3. `POST /venue-decks/{id}/confirm` — the client sends `objectKey` and `fileName`. The service validates that the object key matches the document and tenant, verifies the object exists in storage (`statObject`), reads the actual size and content type back from storage, and persists a `venue_documents` row with status `UPLOADED`.

| Method | Path                        | Auth         | Description                                                                                                                  |
| ------ | --------------------------- | ------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/venue-decks/initiate`     | JWT `MEMBER` | Validate the declared file and return a presigned `PUT` URL + `documentId` + `objectKey` + `expiresInMinutes`                |
| `POST` | `/venue-decks/{id}/confirm` | JWT `MEMBER` | Validate the object key and the uploaded object (`statObject`), persist file metadata, set status `UPLOADED`                 |
| `GET`  | `/venue-decks`              | JWT `MEMBER` | List the venue decks of the active tenant, ordered by most recently created, paginated via `limit` (default 20) and `offset` |
| `GET`  | `/venue-decks/{id}`         | JWT `MEMBER` | Fetch a single venue deck by UUID                                                                                            |

A venue document carries an upload lifecycle status of `UPLOADED`, which later transitions to `PARSING` → `EXTRACTED`, or `FAILED`, as the processing worker picks it up. The object key is tenant-scoped (`docintelligence/tenant/{tenantKey}/venue-decks/{documentId}`), so confirm validates ownership by exact match — a client cannot confirm an object it does not own.

Venue deck paths answer `400` when the tenant ID cannot be resolved, the declared content type is unsupported, the file exceeds the maximum size, the object key does not match the document, or the object was not uploaded to storage; `401` for a missing or invalid JWT; `403` when the caller lacks the `MEMBER` authority; and `404` when the document does not exist.

---

### Error Responses

All errors follow RFC 7807 `ProblemDetail` format:

```json
{
    "type": "about:blank",
    "title": "Validation Failed",
    "status": 400,
    "detail": "Request validation failed",
    "instance": "/api/v1/catalog/ping",
    "correlationId": "abc12345",
    "requestId": "req-a1b2c3d4"
}
```

| Status | When                                                       |
| ------ | ---------------------------------------------------------- |
| 400    | Validation failure, or a tenant ID that cannot be resolved |
| 401    | Missing or invalid JWT                                     |
| 403    | Insufficient authority (e.g. accessing admin endpoints)    |
| 404    | Referenced master venue not found                          |
| 500    | Unexpected internal error                                  |

---

### Interactive Documentation

Swagger UI is available at `http://localhost:8080/swagger-ui.html` when running locally.
The Gateway aggregates the Catalog Service spec at `/api/v1/catalog/api-docs`.

> Auth legend: `public` = no token required; `JWT` = valid Bearer token; `JWT MEMBER` = JWT with tenant member authority; `JWT PLATFORM_ADMIN` = JWT with platform administrator authority.
