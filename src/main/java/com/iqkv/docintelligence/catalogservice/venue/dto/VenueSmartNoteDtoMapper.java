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

import com.iqkv.docintelligence.venue.VenueSmartNote;

/**
 * Static translation between {@link VenueSmartNote} rows and the {@link VenueSmartNoteDtos}
 * records. No Spring bean — stateless conversion only.
 */
public final class VenueSmartNoteDtoMapper {

  private VenueSmartNoteDtoMapper() {
  }

  /**
   * Full projection of one row.
   */
  public static VenueSmartNoteDtos.VenueSmartNoteResponse toResponse(final VenueSmartNote note) {
    return new VenueSmartNoteDtos.VenueSmartNoteResponse(
        note.getId(),
        note.getVenueId(),
        note.getTitle(),
        note.getContent(),
        note.getColor(),
        note.getTags(),
        note.getAuthorId(),
        note.getLinkedAssetId(),
        note.getSortOrder(),
        note.isPinned(),
        note.getCreatedAt(),
        note.getUpdatedAt());
  }

  /**
   * Builds a new row from a create request, applying a generated id and the given author.
   */
  public static VenueSmartNote toEntity(final VenueSmartNoteDtos.VenueSmartNoteCreateRequest request,
                                         final UUID authorId) {
    final VenueSmartNote note = new VenueSmartNote();
    note.setId(UUID.randomUUID());
    note.setVenueId(request.venueId());
    note.setTitle(request.title());
    note.setContent(request.content());
    note.setColor(request.color());
    note.setTags(request.tags());
    note.setAuthorId(authorId);
    note.setLinkedAssetId(request.linkedAssetId());
    note.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
    note.setPinned(request.pinned() != null && request.pinned());
    return note;
  }
}
