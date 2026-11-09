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

package com.iqkv.docintelligence.catalogservice.venuedeck.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response records for the venue deck (venue document) upload flow, grouped in one
 * place per the project convention. Write records carry bean-validation annotations; read
 * records are plain projections.
 */
public final class VenueDeckDtos {

  private VenueDeckDtos() {
  }

  /**
   * Body for {@code POST /api/v1/catalog/venue-decks/initiate}. The client declares the file it is
   * about to upload so the service can validate the content type and size against the configured
   * upload policy before minting a presigned URL.
   */
  public record InitiateUploadRequest(
      @NotBlank @Size(max = 255) String fileName,
      @NotBlank @Size(max = 100) String contentType,
      @Positive long sizeBytes) {
  }

  /**
   * Returned by {@code POST /initiate}. The client PUTs the file to {@code presignedUploadUrl},
   * then calls {@code POST /{documentId}/confirm} with {@code objectKey}.
   */
  public record InitiateUploadResponse(
      UUID documentId,
      String presignedUploadUrl,
      String objectKey,
      int expiresInMinutes) {
  }

  /**
   * Body for {@code POST /api/v1/catalog/venue-decks/{id}/confirm}. The {@code objectKey} must
   * match the one returned by initiate for this document id; {@code fileName} is the original
   * uploaded name (not derivable from storage). Content type and size are read back from the
   * stored object at confirm time.
   */
  public record ConfirmUploadRequest(
      @NotBlank String objectKey,
      @NotBlank @Size(max = 255) String fileName) {
  }

  /**
   * Tenant-facing view of one venue document.
   */
  public record VenueDocumentResponse(
      UUID id,
      String fileName,
      String contentType,
      Long sizeBytes,
      String objectKey,
      String status,
      UUID uploadedBy,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Tenant-facing list page.
   *
   * @param items         the page of documents, ordered by most recently created
   * @param totalElements total number of documents in the active tenant schema
   */
  public record VenueDocumentListResponse(List<VenueDocumentResponse> items, long totalElements) {
  }
}
