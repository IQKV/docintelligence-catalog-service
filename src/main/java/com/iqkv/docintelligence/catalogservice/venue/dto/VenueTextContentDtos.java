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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response records for the venue text-content surface, grouped in one place per the
 * project convention. Write records carry bean-validation annotations; read records are plain
 * projections.
 */
public final class VenueTextContentDtos {

  private VenueTextContentDtos() {
  }

  /**
   * Full view of one venue text-content row.
   */
  public record VenueTextContentResponse(
      UUID id,
      UUID venueId,
      UUID sourceAssetId,
      String content,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Page of text-content rows.
   *
   * @param items         the page of rows, ordered by {@code created_at} descending
   * @param totalElements total number of rows matching the same filters
   */
  public record VenueTextContentListResponse(List<VenueTextContentResponse> items, long totalElements) {
  }

  /**
   * Write payload for {@code POST /} (create). {@code venueId} is optional — the row may be
   * created before the venue exists and linked later. {@code sourceAssetId} is optional provenance.
   */
  public record VenueTextContentWriteRequest(
      UUID venueId,
      UUID sourceAssetId,
      @NotBlank String content) {
  }

  /**
   * Partial-update payload. Only non-null fields are overwritten. There is no way to clear
   * {@code venueId} or {@code sourceAssetId} to {@code null} via PATCH — use the link endpoint
   * to set {@code venueId} explicitly.
   */
  public record VenueTextContentPatchRequest(
      String content) {
  }

  /**
   * Payload for the link endpoint — sets the {@code venueId} of a venue-less row.
   */
  public record VenueTextContentLinkRequest(
      UUID venueId) {
  }

  /**
   * Single item in a semantic search result page.
   *
   * <p>Carries the full projection plus a {@code similarity} score in [0, 1]
   * (1 = identical), derived from {@code 1 - cosine_distance}.
   */
  public record VenueTextContentSemanticSearchResult(
      UUID id,
      UUID venueId,
      UUID sourceAssetId,
      String content,
      Instant createdAt,
      Instant updatedAt,
      double similarity) {
  }

  /**
   * Semantic search result page.
   *
   * @param items         ranked by similarity descending
   * @param totalElements total number of embedded rows that had an embedding at search time
   */
  public record VenueTextContentSemanticSearchResponse(
      List<VenueTextContentSemanticSearchResult> items,
      long totalElements) {
  }
}
