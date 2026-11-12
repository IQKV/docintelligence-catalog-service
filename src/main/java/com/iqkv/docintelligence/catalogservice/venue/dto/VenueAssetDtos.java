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

package com.iqkv.docintelligence.catalogservice.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response records for the tenant venue asset surface, grouped in one place per the
 * project convention. Write records carry bean-validation annotations; read records are plain
 * projections.
 */
public final class VenueAssetDtos {

  private VenueAssetDtos() {
  }

  /**
   * Body for {@code POST /api/v1/catalog/venues/{venueId}/assets/initiate}. The client declares the
   * file it is about to upload so the service can validate the content type and size against the
   * configured upload policy before minting a presigned URL.
   */
  public record InitiateAssetUploadRequest(
      @NotBlank @Size(max = 255) String fileName,
      @NotBlank @Size(max = 100) String contentType,
      @Positive long sizeBytes) {
  }

  /**
   * Returned by {@code POST /initiate}. The client PUTs the file to {@code presignedUploadUrl},
   * then calls {@code POST /{assetId}/confirm} with {@code objectKey}.
   */
  public record InitiateAssetUploadResponse(
      UUID assetId,
      String presignedUploadUrl,
      String objectKey,
      int expiresInMinutes) {
  }

  /**
   * Body for {@code POST /api/v1/catalog/venues/{venueId}/assets/{id}/confirm}. The
   * {@code objectKey} must match the one returned by initiate for this asset id; {@code fileName}
   * is the original uploaded name (not derivable from storage). The taxonomy fields
   * ({@code assetType}, {@code photoCategory}, {@code displayOrder}, {@code label}) describe the
   * asset and are persisted with the row. Content type and size are read back from the stored
   * object at confirm time.
   */
  public record ConfirmAssetUploadRequest(
      @NotBlank String objectKey,
      @NotBlank @Size(max = 255) String fileName,
      @NotBlank @Size(max = 50) String assetType,
      @Size(max = 50) String photoCategory,
      Integer displayOrder,
      @Size(max = 255) String label) {
  }

  /**
   * Tenant-facing view of one venue asset. The stored object key is exposed so a caller can
   * resolve the object. {@code status} reports where the asset sits in the processing pipeline and
   * {@code failureReason} carries the cause when it is {@code FAILED}.
   */
  public record VenueAssetResponse(
      UUID id,
      UUID venueId,
      String assetType,
      String photoCategory,
      Integer displayOrder,
      String label,
      String fileName,
      String contentType,
      Long sizeBytes,
      String s3Key,
      UUID uploadedBy,
      String status,
      String failureReason,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Tenant-facing asset collection of a single venue.
   *
   * @param items         the assets, ordered by display order
   * @param totalElements number of assets in {@code items}
   */
  public record VenueAssetListResponse(List<VenueAssetResponse> items, long totalElements) {
  }

  /**
   * Tenant-facing read projection for a venue asset — exposes a time-limited presigned GET URL
   * ({@code sourceUrl}) instead of the raw {@code s3Key}. Used by the public photos endpoint so
   * the browser can render the image directly without resolving storage credentials.
   */
  public record VenueAssetPublicResponse(
      UUID id,
      UUID venueId,
      String assetType,
      String photoCategory,
      Integer displayOrder,
      String label,
      String fileName,
      String contentType,
      Long sizeBytes,
      String sourceUrl,
      UUID uploadedBy,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Tenant-facing photo collection — items carry {@code sourceUrl} presigned GET URLs.
   */
  public record VenueAssetPublicListResponse(List<VenueAssetPublicResponse> items, long totalElements) {
  }

  /**
   * Returned by the asset preview endpoint — a time-limited presigned GET URL for the stored
   * object plus the content type it was uploaded as, so the browser can render it inline
   * (images and PDFs alike) without resolving storage credentials.
   */
  public record VenueAssetPreviewUrlResponse(
      UUID assetId,
      String url,
      String contentType,
      String fileName,
      int expiresInMinutes) {
  }
}
