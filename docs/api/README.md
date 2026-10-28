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

On creation, omitted `metadata` falls back to the canonical empty document and omitted `confidence` to `1.0`. Deleting a venue removes its aliases and external provider records through the database cascade.

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
