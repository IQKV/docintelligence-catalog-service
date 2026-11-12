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

import java.util.UUID;

import com.iqkv.docintelligence.venue.VenueTextContent;

/**
 * Static translation between {@link VenueTextContent} rows and the
 * {@link VenueTextContentDtos} records. No Spring bean — stateless conversion only.
 */
public final class VenueTextContentDtoMapper {

  private VenueTextContentDtoMapper() {
  }

  /**
   * Full projection of one row.
   */
  public static VenueTextContentDtos.VenueTextContentResponse toResponse(final VenueTextContent tc) {
    return new VenueTextContentDtos.VenueTextContentResponse(
        tc.getId(),
        tc.getVenueId(),
        tc.getSourceAssetId(),
        tc.getContent(),
        tc.getCreatedAt(),
        tc.getUpdatedAt());
  }

  /**
   * Builds a new row from a write request, applying a generated id.
   */
  public static VenueTextContent toEntity(final VenueTextContentDtos.VenueTextContentWriteRequest request) {
    final VenueTextContent tc = new VenueTextContent();
    tc.setId(UUID.randomUUID());
    applyWrite(tc, request);
    return tc;
  }

  /**
   * Applies a full write: sets all fields from the request.
   */
  public static void applyWrite(final VenueTextContent tc,
                                final VenueTextContentDtos.VenueTextContentWriteRequest request) {
    tc.setVenueId(request.venueId());
    tc.setSourceAssetId(request.sourceAssetId());
    tc.setContent(request.content());
  }

  /**
   * Applies a partial update: only non-null fields are overwritten.
   */
  public static void applyPatch(final VenueTextContent tc,
                                final VenueTextContentDtos.VenueTextContentPatchRequest request) {
    if (request.content() != null) {
      tc.setContent(request.content());
    }
  }
}
