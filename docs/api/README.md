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

Read access to the tenant's own venue records for tenant members (`MEMBER`, `TENANT_OWNER` or `ADMIN` authority). Tenant context is resolved from the `X-Tenant-ID` header or the JWT `tenant_id` claim; rows are isolated by tenant schema, so a caller sees only their tenant's venues.

Responses use the safe projection: `confidence` and `source` are not exposed; `metadata` is included so tenants can read and edit their own venue metadata document; `primaryPhotoUrl` carries a time-limited presigned URL for the venue's primary photo when one exists; the optional `masterVenueId` relation key is kept.

| Method | Path                      | Auth                                | Description                                                                                                                                                    |
| ------ | ------------------------- | ----------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GET`  | `/venues`                 | JWT `MEMBER`/`TENANT_OWNER`/`ADMIN` | Search by keyword (`search`) and exact city (`city`); optional sort by name via `sortBy`/`sortDir`; paginated via `limit` (default 20) and `offset`            |
| `GET`  | `/venues/{id}`            | JWT `MEMBER`/`TENANT_OWNER`/`ADMIN` | Fetch a single tenant venue by UUID                                                                                                                            |
| `GET`  | `/venues/search/semantic` | JWT `MEMBER`/`TENANT_OWNER`/`ADMIN` | AI semantic search — query planner extracts hard filters, ranks embedded venues by cosine similarity; optional explicit `city` override; `limit` capped at 100 |

Omitting `sortBy` returns venues ordered by most recently added. Every venue path answers `400` when the tenant ID cannot be resolved, `401` for a missing or invalid JWT, `403` when the caller lacks the required authority, and `404` when the venue does not exist in the tenant schema.

### Venues (Write Surface — Tenant Owner / Admin)

Write endpoints for the tenant's own venue records. Require `TENANT_OWNER` or `ADMIN` authority. Curation fields (`confidence`, `source`) are never accepted or returned; `metadata` is managed separately via the metadata PATCH endpoint.

| Method   | Path                    | Auth                       | Description                                                                                |
| -------- | ----------------------- | -------------------------- | ------------------------------------------------------------------------------------------ |
| `POST`   | `/venues`               | JWT `TENANT_OWNER`/`ADMIN` | Create a venue; curation fields initialised to safe defaults; returns `201`                |
| `PUT`    | `/venues/{id}`          | JWT `TENANT_OWNER`/`ADMIN` | Full replace of tenant-visible fields; curation fields untouched; returns `404` if missing |
| `PATCH`  | `/venues/{id}`          | JWT `TENANT_OWNER`/`ADMIN` | Partial update — only non-null fields overwritten; curation fields never modified          |
| `PATCH`  | `/venues/{id}/metadata` | JWT `TENANT_OWNER`/`ADMIN` | Replace the full metadata document string; all other fields left untouched                 |
| `DELETE` | `/venues/{id}`          | JWT `TENANT_OWNER`/`ADMIN` | Permanently delete the venue; returns `204`                                                |
| `DELETE` | `/venues`               | JWT `TENANT_OWNER`/`ADMIN` | Bulk delete up to 100 venues (JSON body `{"ids":[...]}`); idempotent; returns `204`        |

`PATCH /venues/{id}/metadata` accepts `{"metadata": "<full MetadataDocument JSON string>"}`. The client always sends the complete document — no delta merge is performed. `DELETE /venues` silently ignores ids that match no venue; empty or oversized id lists return `400`.

### Venue Assets (Tenant Surface)

Two-phase presigned upload for venue photos and attachments, plus list, detail, and delete. Upload initiation and reads require `TENANT_OWNER` or `MEMBER`; delete requires `TENANT_OWNER` or `ADMIN`. Assets are nested under their owning venue.

**Upload flow:**

1. `POST /venues/{venueId}/assets/initiate` — declare `fileName`, `contentType` and `sizeBytes`; the service validates against the upload policy, mints a tenant-scoped object key and a presigned S3 `PUT` URL, and returns `assetId` + `objectKey`. No row is persisted yet.
2. Upload the file directly to MinIO using the presigned `PUT` URL.
3. `POST /venues/{venueId}/assets/{id}/confirm` — send `objectKey`, `fileName`, and taxonomy fields (`assetType`, `photoCategory`, `displayOrder`, `label`). The service validates key ownership, calls `statObject` to confirm the object exists, reads actual size and content type from storage, and persists the asset row.

| Method   | Path                                        | Auth                        | Description                                                                                                                                                         |
| -------- | ------------------------------------------- | --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST`   | `/venues/{venueId}/assets/initiate`         | JWT `TENANT_OWNER`/`MEMBER` | Mint a presigned `PUT` URL; returns `assetId`, `objectKey`, `presignedUploadUrl`, `expiresInMinutes`                                                                |
| `POST`   | `/venues/{venueId}/assets/{id}/confirm`     | JWT `TENANT_OWNER`/`MEMBER` | Confirm object in storage; persist asset metadata; returns `400` on key mismatch or missing object                                                                  |
| `GET`    | `/venues/{venueId}/assets`                  | JWT `TENANT_OWNER`/`MEMBER` | List all assets of the venue ordered by `displayOrder`                                                                                                              |
| `GET`    | `/venues/{venueId}/assets/photos`           | JWT `TENANT_OWNER`/`MEMBER` | List only `PHOTO` assets for gallery use, ordered by `displayOrder`                                                                                                 |
| `GET`    | `/venues/{venueId}/assets/{id}`             | JWT `TENANT_OWNER`/`MEMBER` | Fetch a single asset; returns `404` when the venue or asset is not found or not owned by that venue                                                                 |
| `GET`    | `/venues/{venueId}/assets/{id}/preview-url` | JWT `TENANT_OWNER`/`MEMBER` | Mint a short-lived presigned `GET` URL for the stored object (images and PDFs alike); returns `404` when the venue or asset is not found or not owned by that venue |
| `DELETE` | `/venues/{venueId}/assets/{id}`             | JWT `TENANT_OWNER`/`ADMIN`  | Delete asset row and remove object from storage (best-effort); returns `204`; returns `404` if not found                                                            |

Storage removal on delete is best-effort: a MinIO failure is logged but does not prevent the row from being deleted.

---

### Venue Asset Import (Tenant Surface)

Venue-less asset import — upload a document (PDF/CSV/image) and let `docintelligence-catalog-processing-worker` derive the venue record from it. There is no owning venue in the path because none exists yet. Requires `TENANT_OWNER` or `MEMBER` authority; the object key is tenant-scoped.

| Method | Path                         | Auth                        | Description                                                                                           |
| ------ | ---------------------------- | --------------------------- | ----------------------------------------------------------------------------------------------------- |
| `POST` | `/venue-assets/initiate`     | JWT `TENANT_OWNER`/`MEMBER` | Mint a presigned `PUT` URL; returns `assetId`, `objectKey`, `presignedUploadUrl`, `expiresInMinutes`  |
| `POST` | `/venue-assets/{id}/confirm` | JWT `TENANT_OWNER`/`MEMBER` | Confirm object in storage; persist the asset with a `null` venue and status `UPLOADED` for the worker |
| `GET`  | `/venue-assets/{id}`         | JWT `TENANT_OWNER`/`MEMBER` | Poll the asset and its processing status — see the status lifecycle below                             |

A confirmed import carries the processing status `UPLOADED`, then `PARSING`, and finally `EXTRACTED` (the derived venue is linked — see `venueId`) or `FAILED` (see `failureReason`). `initiate` answers `400` for an unsupported content type or an oversized file; `confirm` answers `400` on an object key mismatch or a missing storage object; `{id}` answers `404` when the asset does not exist.

---

### Venue Enrichment (Tenant Surface)

AI-assisted single-field extraction — the tenant UI "fill from source" flow. Requires `TENANT_OWNER` or `MEMBER` authority; tenant context is resolved from the `X-Tenant-ID` header or the JWT `tenant_id` claim. The tenant picks one concrete `metadata.items` row plus one preloaded source document, and the endpoint returns the value found for that field. It degrades gracefully: when the chat model is unreachable, the AI feature flag is off, or the source holds no value for the field, it still returns `200` with `found=false`.

| Method | Path                                   | Auth                        | Description                                                                                                                                                                                        |
| ------ | -------------------------------------- | --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/venues/{venueId}/enrichment/extract` | JWT `TENANT_OWNER`/`MEMBER` | Extract one `group`/`field` value from the chosen `sourceTextId`, constrained to the field's `valueType`/`allowedValues`; returns the extracted `value` plus verbatim `evidence`, or `found=false` |

The chosen `sourceTextId` must belong to the venue — the row is resolved from the venue's own text-content rows. Applying an extracted value is done through `PATCH /venues/{venueId}/metadata`, not through this surface. The path answers `400` on validation failure, `401` for a missing or invalid JWT, `403` when the caller lacks the required authority, and `404` when the venue does not exist in the tenant schema.

---

### Venue Text Contents (Tenant Surface)

Raw or markdown text chunks extracted from venue source documents. Reads are open to `MEMBER`, `TENANT_OWNER` and `ADMIN`; writes require `TENANT_OWNER` or `ADMIN`. Tenant context is resolved from the `X-Tenant-ID` header or the JWT `tenant_id` claim.

| Method   | Path                                   | Auth                                | Description                                                                                                                                      |
| -------- | -------------------------------------- | ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `GET`    | `/venue-text-contents`                 | JWT `MEMBER`/`TENANT_OWNER`/`ADMIN` | Keyword search over `content`, optionally filtered by `venueId`, paginated via `limit` (default 20) and `offset`, newest first                   |
| `GET`    | `/venue-text-contents/venue/{venueId}` | JWT `MEMBER`/`TENANT_OWNER`/`ADMIN` | List all rows linked to the given venue, oldest first                                                                                            |
| `GET`    | `/venue-text-contents/{id}`            | JWT `MEMBER`/`TENANT_OWNER`/`ADMIN` | Fetch a single row; returns `404` when it does not exist                                                                                         |
| `GET`    | `/venue-text-contents/search/semantic` | JWT `MEMBER`/`TENANT_OWNER`/`ADMIN` | Semantic search — embeds the query (`q`) and ranks embedded rows by cosine similarity; optional `venueId` filter, `limit` capped at 100          |
| `POST`   | `/venue-text-contents`                 | JWT `TENANT_OWNER`/`ADMIN`          | Create a row from `{venueId?, sourceAssetId?, content}`; `venueId` is optional — a row may be created before its venue is derived; returns `201` |
| `PATCH`  | `/venue-text-contents/{id}`            | JWT `TENANT_OWNER`/`ADMIN`          | Update `content`; only non-null fields are overwritten; returns `404` when the row does not exist                                                |
| `POST`   | `/venue-text-contents/{id}/link`       | JWT `TENANT_OWNER`/`ADMIN`          | Set `venueId` on a row that was created before the venue was derived; returns `404` when the row does not exist                                  |
| `DELETE` | `/venue-text-contents/{id}`            | JWT `TENANT_OWNER`/`ADMIN`          | Permanently delete the row; returns `204`; returns `404` when the row does not exist                                                             |

Text-content paths answer `400` when the tenant ID cannot be resolved, `401` for a missing or invalid JWT, and `403` when the caller lacks the required authority.

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

`POST` and `PUT` take a write payload of `name`, `address`, `city`, `locationLat`, `locationLng`, `metadata`, `confidence`, `source` and the optional `masterVenueId` relation key. `PATCH` takes the same shape with every field optional — a `null` field means "leave unchanged", so a field (including `masterVenueId`) can be set but not cleared via PATCH. Omitted `metadata` defaults to the canonical empty document and omitted `confidence` to `1.0`. Paths carrying an `{id}` answer `404` when the venue does not exist in that tenant schema. `GET .../search/semantic` runs an AI query planner that extracts hard filters (city, venue type, capacity, catering, amenities, accessibility) and ranks the embedded venues by cosine similarity using the per-tenant pgvector index; only venues already carrying an embedding are considered, and a disabled or unreachable planner falls back to embedding the raw query with no filters. An optional explicit `city` parameter overrides the city the planner extracts; `limit` defaults to 20 and is capped at 100.

---

### Venue Assets (Admin Surface)

Mirror of the tenant venue asset surface on behalf of the tenant named in the `{tenantKey}` path variable. Requires `PLATFORM_ADMIN` authority; the controller scopes each request to that tenant's schema and records the calling administrator's `user_id` as `uploadedBy`.

| Method   | Path                                                          | Auth                 | Description                                                                                              |
| -------- | ------------------------------------------------------------- | -------------------- | -------------------------------------------------------------------------------------------------------- |
| `POST`   | `/admin/{tenantKey}/venues/{venueId}/assets/initiate`         | JWT `PLATFORM_ADMIN` | Mint a presigned `PUT` URL; returns `assetId`, `objectKey`, `presignedUploadUrl`, `expiresInMinutes`     |
| `POST`   | `/admin/{tenantKey}/venues/{venueId}/assets/{id}/confirm`     | JWT `PLATFORM_ADMIN` | Confirm object in storage; persist asset metadata; returns `400` on key mismatch or missing object       |
| `GET`    | `/admin/{tenantKey}/venues/{venueId}/assets`                  | JWT `PLATFORM_ADMIN` | List all assets of the venue ordered by `displayOrder`                                                   |
| `GET`    | `/admin/{tenantKey}/venues/{venueId}/assets/photos`           | JWT `PLATFORM_ADMIN` | List only `PHOTO` assets for gallery use, ordered by `displayOrder`                                      |
| `GET`    | `/admin/{tenantKey}/venues/{venueId}/assets/{id}`             | JWT `PLATFORM_ADMIN` | Fetch a single asset; returns `404` when the venue or asset is not found or not owned by that venue      |
| `GET`    | `/admin/{tenantKey}/venues/{venueId}/assets/{id}/preview-url` | JWT `PLATFORM_ADMIN` | Mint a short-lived presigned `GET` URL for the stored object (images and PDFs alike)                     |
| `DELETE` | `/admin/{tenantKey}/venues/{venueId}/assets/{id}`             | JWT `PLATFORM_ADMIN` | Delete asset row and remove object from storage (best-effort); returns `204`; returns `404` if not found |

---

### Venue Asset Import (Admin Surface)

Mirror of the venue-less import surface, scoped to the tenant named in the `{tenantKey}` path variable. Requires `PLATFORM_ADMIN` authority. `confirm` persists the asset with a `null` venue and status `UPLOADED` for the processing worker to derive the venue.

| Method | Path                                           | Auth                 | Description                                                                                          |
| ------ | ---------------------------------------------- | -------------------- | ---------------------------------------------------------------------------------------------------- |
| `POST` | `/admin/{tenantKey}/venue-assets/initiate`     | JWT `PLATFORM_ADMIN` | Mint a presigned `PUT` URL; returns `assetId`, `objectKey`, `presignedUploadUrl`, `expiresInMinutes` |
| `POST` | `/admin/{tenantKey}/venue-assets/{id}/confirm` | JWT `PLATFORM_ADMIN` | Confirm object in storage; persist the asset with a `null` venue and status `UPLOADED`               |
| `GET`  | `/admin/{tenantKey}/venue-assets/{id}`         | JWT `PLATFORM_ADMIN` | Poll the asset and its processing status (`UPLOADED` → `PARSING` → `EXTRACTED` / `FAILED`)           |

---

### Venue Text Contents (Admin Surface)

Mirror of the tenant text-content surface, scoped to the tenant named in the `{tenantKey}` path variable. Requires `PLATFORM_ADMIN` authority.

| Method   | Path                                                     | Auth                 | Description                                                                                     |
| -------- | -------------------------------------------------------- | -------------------- | ----------------------------------------------------------------------------------------------- |
| `GET`    | `/admin/{tenantKey}/venue-text-contents`                 | JWT `PLATFORM_ADMIN` | Keyword search over `content`, optionally filtered by `venueId`, paginated via `limit`/`offset` |
| `GET`    | `/admin/{tenantKey}/venue-text-contents/venue/{venueId}` | JWT `PLATFORM_ADMIN` | List all rows linked to the given venue, oldest first                                           |
| `GET`    | `/admin/{tenantKey}/venue-text-contents/{id}`            | JWT `PLATFORM_ADMIN` | Fetch a single row; returns `404` when it does not exist                                        |
| `GET`    | `/admin/{tenantKey}/venue-text-contents/search/semantic` | JWT `PLATFORM_ADMIN` | Semantic search — embeds the query (`q`) and ranks embedded rows by cosine similarity           |
| `POST`   | `/admin/{tenantKey}/venue-text-contents`                 | JWT `PLATFORM_ADMIN` | Create a row from `{venueId?, sourceAssetId?, content}`; returns `201`                          |
| `PATCH`  | `/admin/{tenantKey}/venue-text-contents/{id}`            | JWT `PLATFORM_ADMIN` | Update `content`; returns `404` when the row does not exist                                     |
| `POST`   | `/admin/{tenantKey}/venue-text-contents/{id}/link`       | JWT `PLATFORM_ADMIN` | Set `venueId` on a venue-less row; returns `404` when the row does not exist                    |
| `DELETE` | `/admin/{tenantKey}/venue-text-contents/{id}`            | JWT `PLATFORM_ADMIN` | Permanently delete the row; returns `204`; returns `404` when the row does not exist            |

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
