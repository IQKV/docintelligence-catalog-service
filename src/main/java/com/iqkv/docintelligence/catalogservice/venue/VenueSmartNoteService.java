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

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueAssetDtos;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueSmartNoteDtos;

/**
 * Use cases over venue smart-note rows in the active tenant schema.
 *
 * <p>Every method that takes an id throws {@link VenueSmartNoteNotFoundException} when no such
 * row exists. Any tenant member may create a note; {@code patch} and {@code setPinned} require the
 * caller to be the note's author unless {@code canEditAnyNote} is set (tenant-owner / admin
 * override). Deletes stay restricted to tenant owners (or a platform admin on the admin surface)
 * at the HTTP layer.
 */
public interface VenueSmartNoteService {

  /**
   * Creates a new smart note. {@code authorId} is the caller's user id, resolved from the JWT by
   * the REST resource and never trusted from the request body.
   */
  VenueSmartNoteDtos.VenueSmartNoteResponse create(VenueSmartNoteDtos.VenueSmartNoteCreateRequest request,
                                                    UUID authorId);

  /**
   * Returns one smart note by id.
   *
   * @throws VenueSmartNoteNotFoundException when the row does not exist
   */
  VenueSmartNoteDtos.VenueSmartNoteResponse getById(UUID id);

  /**
   * All smart notes attached to one venue, ordered by {@code sort_order} ascending then
   * {@code created_at} in the requested direction.
   *
   * @param venueId   the owning venue
   * @param direction {@code "ASC"} or {@code "DESC"} — applied to {@code created_at};
   *                  {@code null} defaults to ascending
   * @param pinned    {@code true} for only pinned notes, {@code false} for only unpinned,
   *                  {@code null} for all notes regardless of pin state
   */
  VenueSmartNoteDtos.VenueSmartNoteListResponse listByVenue(UUID venueId, String direction, Boolean pinned);

  /**
   * Partially updates a smart note. Only non-null request fields are overwritten.
   *
   * <p>{@code actorId} is the caller's user id, resolved from the JWT by the REST resource. The
   * caller must be the note's author unless {@code canEditAnyNote} is {@code true}
   * (TENANT_OWNER / ADMIN override).
   *
   * @throws VenueSmartNoteNotFoundException when the row does not exist
   * @throws org.springframework.security.access.AccessDeniedException when the caller is neither the
   *     author nor privileged
   */
  VenueSmartNoteDtos.VenueSmartNoteResponse patch(UUID id,
                                                  VenueSmartNoteDtos.VenueSmartNotePatchRequest request,
                                                  UUID actorId,
                                                  boolean canEditAnyNote);

  /**
   * Deletes one smart note.
   *
   * @throws VenueSmartNoteNotFoundException when the row does not exist
   */
  void delete(UUID id);

  /**
   * Sets the pin flag on one note in a single call. Returns the updated row.
   *
   * <p>{@code actorId} is the caller's user id, resolved from the JWT by the REST resource. The
   * caller must be the note's author unless {@code canEditAnyNote} is {@code true}
   * (TENANT_OWNER / ADMIN override).
   *
   * @throws VenueSmartNoteNotFoundException when the row does not exist
   * @throws org.springframework.security.access.AccessDeniedException when the caller is neither the
   *     author nor privileged
   */
  VenueSmartNoteDtos.VenueSmartNoteResponse setPinned(UUID id, boolean pinned, UUID actorId,
                                                      boolean canEditAnyNote);

  /**
   * Loads the venue asset linked to a note, so the UI can render it inline.
   *
   * @return the linked asset
   * @throws VenueSmartNoteNotFoundException when the note does not exist
   * @throws VenueAssetNotFoundException    when the note has no linked asset or the asset row was
   *     deleted
   */
  VenueAssetDtos.VenueAssetResponse getLinkedAsset(UUID noteId);

  /**
   * Returns the number of smart notes attached to one venue using a {@code COUNT(*)} query.
   */
  VenueSmartNoteDtos.VenueSmartNoteCountResponse countByVenue(UUID venueId);
}
