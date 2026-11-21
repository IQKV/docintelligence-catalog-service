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

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueAssetDtoMapper;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueAssetDtos;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueSmartNoteDtoMapper;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueSmartNoteDtos;
import com.iqkv.docintelligence.venue.VenueAsset;
import com.iqkv.docintelligence.venue.VenueAssetMapper;
import com.iqkv.docintelligence.venue.VenueSmartNote;
import com.iqkv.docintelligence.venue.VenueSmartNoteMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases over tenant venue smart-note rows, backed by {@link VenueSmartNoteMapper}.
 *
 * <p>Patch is field-granular: each non-null request field maps to one mapper update method, so a
 * partial edit touches only the columns that changed. The {@code authorId} is set at create time
 * from the caller's JWT and is never updatable.
 */
@Service
@Transactional
public class VenueSmartNoteServiceImpl implements VenueSmartNoteService {

  private final VenueSmartNoteMapper mapper;
  private final VenueAssetMapper assetMapper;

  public VenueSmartNoteServiceImpl(final VenueSmartNoteMapper mapper,
                                    final VenueAssetMapper assetMapper) {
    this.mapper = mapper;
    this.assetMapper = assetMapper;
  }

  @Override
  public VenueSmartNoteDtos.VenueSmartNoteResponse create(
      final VenueSmartNoteDtos.VenueSmartNoteCreateRequest request, final UUID authorId) {
    final VenueSmartNote note = VenueSmartNoteDtoMapper.toEntity(request, authorId);
    mapper.insert(note);
    return VenueSmartNoteDtoMapper.toResponse(require(note.getId()));
  }

  @Override
  @Transactional(readOnly = true)
  public VenueSmartNoteDtos.VenueSmartNoteResponse getById(final UUID id) {
    return VenueSmartNoteDtoMapper.toResponse(require(id));
  }

  @Override
  @Transactional(readOnly = true)
  public VenueSmartNoteDtos.VenueSmartNoteListResponse listByVenue(final UUID venueId,
                                                                    final String direction,
                                                                    final Boolean pinned) {
    final var items = mapper.findByVenueId(venueId, direction, pinned);
    return new VenueSmartNoteDtos.VenueSmartNoteListResponse(
        items.stream().map(VenueSmartNoteDtoMapper::toResponse).toList(),
        items.size());
  }

  @Override
  public VenueSmartNoteDtos.VenueSmartNoteResponse patch(
      final UUID id,
      final VenueSmartNoteDtos.VenueSmartNotePatchRequest request,
      final UUID actorId,
      final boolean canEditAnyNote) {
    requireCanEdit(require(id), actorId, canEditAnyNote);
    if (request.title() != null) {
      mapper.updateTitle(id, request.title());
    }
    if (request.content() != null) {
      mapper.updateContent(id, request.content());
    }
    if (request.color() != null) {
      mapper.updateColor(id, request.color());
    }
    if (request.tags() != null) {
      mapper.updateTags(id, request.tags());
    }
    if (request.linkedAssetId() != null) {
      mapper.updateLinkedAssetId(id, request.linkedAssetId());
    }
    if (request.sortOrder() != null) {
      mapper.updateSortOrder(id, request.sortOrder());
    }
    if (request.pinned() != null) {
      mapper.updatePinned(id, request.pinned());
    }
    return VenueSmartNoteDtoMapper.toResponse(require(id));
  }

  @Override
  public void delete(final UUID id) {
    require(id);
    mapper.deleteById(id);
  }

  @Override
  public VenueSmartNoteDtos.VenueSmartNoteResponse setPinned(final UUID id, final boolean pinned,
                                                             final UUID actorId,
                                                             final boolean canEditAnyNote) {
    requireCanEdit(require(id), actorId, canEditAnyNote);
    mapper.updatePinned(id, pinned);
    return VenueSmartNoteDtoMapper.toResponse(require(id));
  }

  @Override
  @Transactional(readOnly = true)
  public VenueAssetDtos.VenueAssetResponse getLinkedAsset(final UUID noteId) {
    final VenueSmartNote note = require(noteId);
    final UUID assetId = note.getLinkedAssetId();
    if (assetId == null) {
      throw new VenueAssetNotFoundException(noteId);
    }
    final VenueAsset asset = assetMapper.findById(assetId)
        .orElseThrow(() -> new VenueAssetNotFoundException(assetId));
    return VenueAssetDtoMapper.toResponse(asset);
  }

  @Override
  @Transactional(readOnly = true)
  public VenueSmartNoteDtos.VenueSmartNoteCountResponse countByVenue(final UUID venueId) {
    return new VenueSmartNoteDtos.VenueSmartNoteCountResponse(venueId, mapper.countByVenueId(venueId));
  }

  private VenueSmartNote require(final UUID id) {
    return mapper.findById(id)
        .orElseThrow(() -> new VenueSmartNoteNotFoundException(id));
  }

  /**
   * Ownership gate for note mutations: only the author may change a note, unless the caller
   * holds the TENANT_OWNER / ADMIN override.
   */
  private void requireCanEdit(final VenueSmartNote note, final UUID actorId,
                              final boolean canEditAnyNote) {
    if (!canEditAnyNote && !note.getAuthorId().equals(actorId)) {
      throw new AccessDeniedException("Only the note author may modify this note.");
    }
  }
}
