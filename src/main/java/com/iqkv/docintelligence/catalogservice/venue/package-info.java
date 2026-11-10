/*
 * Copyright 2026 iQKV Foundation Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Tenant venue admin feature slice.
 *
 * <p>Platform-admin ({@code PLATFORM_ADMIN}) full CRUD over tenant venue records. The
 * {@link com.iqkv.docintelligence.catalogservice.venue.AdminVenueRestResource} scopes each
 * request to a tenant via the {@code {tenantKey}} path variable, setting
 * {@code TenantContext} manually so the {@code MyBatisSchemaInterceptor} routes queries to the
 * {@code t_{tenantKey}} schema. The {@code Venue} domain model and {@code VenueMapper} live in
 * the shared {@code com.iqkv:docintelligence-venue} artifact; this slice holds the HTTP surface,
 * the service contract and its implementation, and the DTOs.
 *
 * <p>Venue assets — a venue's photos and attachments — are a sub-resource of the venue, served by
 * {@link com.iqkv.docintelligence.catalogservice.venue.VenueAssetRestResource} nested under
 * {@code /api/v1/catalog/venues/{venueId}/assets} (and its platform-admin mirror
 * {@link com.iqkv.docintelligence.catalogservice.venue.AdminVenueAssetRestResource}). They use the
 * same tenant scoping and the two-phase presigned upload flow. The {@code VenueAsset} domain model
 * and {@code VenueAssetMapper} live in the shared artifact too.
 */
package com.iqkv.docintelligence.catalogservice.venue;
