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

package com.iqkv.docintelligence.catalogservice.venue;

import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueTextContentDtos;

/**
 * Use cases over venue text-content rows in the active tenant schema.
 *
 * <p>Rows may be created without a venue (the import pipeline uploads text before the venue is
 * derived) and linked later via {@link #linkVenue}. The final relationship is 1 Venue to many
 * VenueTextContent.
 *
 * <p>Every method that takes an id throws {@link VenueTextContentNotFoundException} when no such
 * row exists.
 */
public interface VenueTextContentService {

  /**
   * Creates a new text-content row. {@code venueId} in the request may be {@code null} when the
   * row is created before the venue is known. Triggers an embedding on the new content.
   */
  VenueTextContentDtos.VenueTextContentResponse create(VenueTextContentDtos.VenueTextContentWriteRequest request);

  /**
   * Returns one text-content row by id.
   *
   * @throws VenueTextContentNotFoundException when the row does not exist
   */
  VenueTextContentDtos.VenueTextContentResponse getById(UUID id);

  /**
   * All text-content rows linked to one venue, ordered by {@code created_at} ascending.
   */
  VenueTextContentDtos.VenueTextContentListResponse listByVenue(UUID venueId);

  /**
   * Keyword search over content, optionally scoped to one venue, with offset pagination.
   *
   * @param venueId optional venue filter; {@code null} searches across all venues
   * @param search  free-text keyword; {@code null} matches all rows
   * @param limit   maximum number of rows to return
   * @param offset  zero-based row offset
   */
  VenueTextContentDtos.VenueTextContentListResponse search(UUID venueId, String search, int limit, int offset);

  /**
   * Partially updates the content of one row. Only non-null request fields are overwritten.
   * Triggers a re-embedding on change. Throws {@link VenueTextContentNotFoundException} when the
   * row does not exist.
   */
  VenueTextContentDtos.VenueTextContentResponse patch(UUID id, VenueTextContentDtos.VenueTextContentPatchRequest request);

  /**
   * Links a venue-less row to a venue. Idempotent if already linked to the same venue. Throws
   * {@link VenueTextContentNotFoundException} when the row does not exist.
   */
  VenueTextContentDtos.VenueTextContentResponse linkVenue(UUID id, UUID venueId);

  /**
   * Deletes one text-content row. Throws {@link VenueTextContentNotFoundException} when the row
   * does not exist.
   */
  void delete(UUID id);

  /**
   * Semantic search over text-content rows that have an embedding, optionally scoped to one venue.
   *
   * @param query   natural-language search query
   * @param venueId optional venue filter; {@code null} searches across all venues
   * @param limit   maximum number of results to return
   */
  VenueTextContentDtos.VenueTextContentSemanticSearchResponse semanticSearch(String query, UUID venueId, int limit);
}
