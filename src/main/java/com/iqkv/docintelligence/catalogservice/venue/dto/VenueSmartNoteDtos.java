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

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response records for the venue smart-note surface, grouped in one place per the
 * project convention. Write records carry bean-validation annotations; read records are plain
 * projections.
 */
public final class VenueSmartNoteDtos {

  private VenueSmartNoteDtos() {
  }

  /**
   * Full view of one venue smart-note row.
   */
  public record VenueSmartNoteResponse(
      UUID id,
      UUID venueId,
      String title,
      String content,
      String color,
      List<String> tags,
      UUID authorId,
      UUID linkedAssetId,
      int sortOrder,
      boolean pinned,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Page of smart-note rows.
   *
   * @param items         the rows, ordered by {@code sort_order} then {@code created_at}
   * @param totalElements total number of rows returned
   */
  public record VenueSmartNoteListResponse(List<VenueSmartNoteResponse> items, long totalElements) {
  }

  /**
   * Write payload for {@code POST /} (create). {@code venueId} is required. {@code tags} is an
   * optional list; {@code null} and empty mean no tags. {@code sortOrder} defaults to {@code 0}
   * when {@code null}. {@code pinned} defaults to {@code false} when {@code null}.
   * {@code linkedAssetId} optionally links the note to a venue asset.
   */
  public record VenueSmartNoteCreateRequest(
      @NotNull UUID venueId,
      String title,
      String content,
      String color,
      List<String> tags,
      UUID linkedAssetId,
      Integer sortOrder,
      Boolean pinned) {
  }

  /**
   * Partial-update payload. Only non-null fields are overwritten. {@code pinned} and
   * {@code sortOrder} use their wrapper types so {@code null} means "do not change".
   */
  public record VenueSmartNotePatchRequest(
      String title,
      String content,
      String color,
      List<String> tags,
      UUID linkedAssetId,
      Integer sortOrder,
      Boolean pinned) {
  }

  /**
   * Body for {@code PUT /{id}/pinned} — toggles the pin flag in a single call.
   */
  public record VenueSmartNoteSetPinnedRequest(boolean pinned) {
  }

  /**
   * Smart-note count for one venue, computed by a {@code COUNT(*)} query.
   */
  public record VenueSmartNoteCountResponse(UUID venueId, long total) {
  }
}
