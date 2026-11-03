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

package com.iqkv.docintelligence.catalogservice.mastervenue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response types for the master venue API surface.
 *
 * <p>Two projections are kept deliberately separate: the tenant-facing records carry only
 * what a member may see, while the admin records expose the operational fields
 * ({@code confidence}, {@code source}, raw {@code metadata}).
 */
public final class MasterVenueDtos {

  private MasterVenueDtos() {
  }

  /**
   * Tenant-facing view of a single master venue.
   *
   * <p>Safe projection: {@code confidence}, {@code source} and the raw {@code metadata}
   * document are internal curation fields and are deliberately absent.
   */
  public record MasterVenuePublicResponse(
      UUID id,
      String name,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String primaryPhotoUrl,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Tenant-facing list item — a narrower projection than {@link MasterVenuePublicResponse}.
   */
  public record MasterVenueSummaryView(
      UUID id,
      String name,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String primaryPhotoUrl,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Tenant-facing search result page.
   *
   * @param items         the page of venues, ordered by name
   * @param totalElements total number of venues matching the same filters
   */
  public record MasterVenueListResponse(List<MasterVenueSummaryView> items, long totalElements) {
  }

  /**
   * Tenant-facing view of one asset of a master venue.
   *
   * <p>The ingestion provenance fields ({@code sourceUrl}, {@code provider}) are internal and are
   * deliberately absent. {@code s3Key} is exposed so a caller can resolve the stored object.
   */
  public record MasterVenueAssetView(
      UUID id,
      String assetType,
      String photoCategory,
      Integer displayOrder,
      String label,
      String fileName,
      String contentType,
      Long sizeBytes,
      String s3Key,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Tenant-facing asset collection of a single master venue.
   *
   * @param items         the assets, ordered by display order
   * @param totalElements number of assets in {@code items}
   */
  public record MasterVenueAssetListResponse(List<MasterVenueAssetView> items, long totalElements) {
  }

  /**
   * Full admin view of a master venue, including curation fields.
   */
  public record MasterVenueResponse(
      UUID id,
      String name,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String metadata,
      BigDecimal confidence,
      String source,
      String primaryPhotoUrl,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Admin search result page.
   *
   * @param items         the page of venues, ordered by name
   * @param totalElements total number of venues matching the same filters
   */
  public record AdminMasterVenueListResponse(List<MasterVenueResponse> items, long totalElements) {
  }

  /**
   * Admin view of one asset of a master venue.
   *
   * <p>Extends the tenant-facing projection with the ingestion provenance
   * ({@code sourceUrl}, {@code provider}), which the tenant tier hides.
   */
  public record AdminMasterVenueAssetView(
      UUID id,
      String assetType,
      String photoCategory,
      Integer displayOrder,
      String label,
      String fileName,
      String contentType,
      Long sizeBytes,
      String s3Key,
      String sourceUrl,
      String provider,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Admin asset collection of a single master venue.
   *
   * @param items         the assets, ordered by display order
   * @param totalElements number of assets in {@code items}
   */
  public record AdminMasterVenueAssetListResponse(List<AdminMasterVenueAssetView> items, long totalElements) {
  }

  /**
   * Write payload for both {@code POST /} (create) and {@code PUT /{id}} (full replace).
   *
   * <p>{@code metadata} and {@code confidence} are optional: a create falls back to the
   * canonical empty metadata document and full confidence.
   */
  public record MasterVenueWriteRequest(
      @NotBlank String name,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String metadata,
      BigDecimal confidence,
      @NotBlank String source) {
  }

  /**
   * Partial update payload. A {@code null} field means "leave unchanged"; there is no way to
   * clear a field with this request, which is the documented limitation of a merge-style PATCH.
   */
  public record MasterVenuePatchRequest(
      String name,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String metadata,
      BigDecimal confidence,
      String source) {
  }

  /**
   * Bulk-delete payload for {@code DELETE /api/v1/catalog/admin/master-venues}.
   *
   * <p>Deletion is idempotent: identifiers that match no venue are silently ignored, so a
   * repeated request is always safe. The list is capped to keep a single request bounded.
   */
  public record MasterVenueBulkDeleteRequest(
      @NotEmpty @Size(max = 100) List<UUID> ids) {
  }

  /**
   * Single item in a semantic search result page.
   *
   * <p>Extends the admin venue projection with a {@code similarity} score in the range
   * [0, 1] (1 = identical). The score is derived from cosine distance:
   * {@code similarity = 1 − cosine_distance}.
   */
  public record SemanticSearchResult(
      UUID id,
      String name,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String metadata,
      BigDecimal confidence,
      String source,
      String primaryPhotoUrl,
      Instant createdAt,
      Instant updatedAt,
      double similarity) {
  }

  /**
   * Semantic search result page.
   *
   * @param items         ranked by similarity descending
   * @param totalElements total number of venues in the result set (before the limit)
   */
  public record SemanticSearchResponse(List<SemanticSearchResult> items, long totalElements) {
  }
}
