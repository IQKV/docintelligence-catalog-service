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

import java.util.List;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;

/**
 * Platform-admin use cases over tenant venue records.
 *
 * <p>Every method that takes an id throws {@link VenueNotFoundException} when the venue does not
 * exist in the active tenant schema. The active tenant schema is set by the controller from the
 * {@code {tenantKey}} path variable before these methods are called.
 *
 * <p>Ownership-sensitive operations ({@link #deletePublicVenue}, {@link #deletePublicVenues},
 * {@link #addShare}, {@link #updateShareRole}, {@link #removeShare}) require the requesting user's
 * id so the service can enforce that only the owner or an ADMIN grantee may act. Admin-surface
 * methods bypass these checks.
 */
public interface VenueService {

  VenueDtos.VenueListResponse searchVenues(String search, String city, String sortBy, String sortDir,
                                           int limit, int offset);

  VenueDtos.VenueResponse getVenue(UUID id);

  VenueDtos.VenueResponse createVenue(VenueDtos.VenueWriteRequest request);

  VenueDtos.VenueResponse replaceVenue(UUID id, VenueDtos.VenueWriteRequest request);

  VenueDtos.VenueResponse patchVenue(UUID id, VenueDtos.VenuePatchRequest request);

  void deleteVenue(UUID id);

  /**
   * Lists tenant venues. In a personal workspace the result is scoped to the calling user's own
   * venues and the ones shared with them, so records from different personal users never mix.
   */
  VenueDtos.VenueListResponse searchPublicVenues(String search, String city, String sortBy, String sortDir,
                                                 int limit, int offset, UUID userId);

  /**
   * Returns one tenant venue. In a personal workspace the caller must own the venue or hold a
   * share grant on it.
   */
  VenueDtos.VenuePublicResponse getPublicVenue(UUID id, UUID userId);

  VenueDtos.VenuePublicResponse createPublicVenue(VenueDtos.VenuePublicWriteRequest request, UUID createdBy);

  VenueDtos.VenuePublicResponse replacePublicVenue(UUID id, VenueDtos.VenuePublicWriteRequest request,
                                                   UUID requestingUserId);

  VenueDtos.VenuePublicResponse patchPublicVenue(UUID id, VenueDtos.VenuePublicPatchRequest request,
                                                 UUID requestingUserId);

  VenueDtos.VenuePublicResponse patchVenueMetadata(UUID id, String metadata, UUID requestingUserId);

  void deletePublicVenue(UUID id, UUID requestingUserId);

  void deletePublicVenues(List<UUID> ids, UUID requestingUserId);

  VenueDtos.VenueSemanticSearchResponse semanticSearch(String query, String city, int limit);

  /**
   * Semantic search over tenant venues. In a personal workspace the candidate set is scoped to
   * the calling user's own venues and the ones shared with them.
   */
  VenueDtos.VenuePublicSemanticSearchResponse semanticSearchPublic(String query, String city, int limit,
                                                                   UUID userId);

  // ─── Sharing ──────────────────────────────────────────────────────────────

  /**
   * Returns all share grants on the given venue. Throws {@link VenueNotFoundException} when the
   * venue does not exist. In a personal workspace the caller must own the venue or hold a share
   * grant on it.
   */
  VenueDtos.VenueShareListResponse getShares(UUID venueId, UUID userId);

  /**
   * Grants access to a venue. The caller ({@code grantedBy}) must own the venue or hold an
   * {@code ADMIN} share grant; otherwise throws {@link org.springframework.security.access.AccessDeniedException}.
   * Throws {@link VenueNotFoundException} when the venue does not exist.
   */
  VenueDtos.VenueShareResponse addShare(UUID venueId, VenueDtos.VenueShareRequest request, UUID grantedBy);

  /**
   * Changes the role of an existing share grant. Same ownership rules as {@link #addShare}.
   * Throws {@link VenueShareNotFoundException} when no grant exists for that user.
   */
  VenueDtos.VenueShareResponse updateShareRole(UUID venueId, UUID sharedWithUserId, String role,
                                               UUID requestingUserId);

  /**
   * Revokes a share grant. Same ownership rules as {@link #addShare}.
   * Throws {@link VenueShareNotFoundException} when no grant exists for that user.
   */
  void removeShare(UUID venueId, UUID sharedWithUserId, UUID requestingUserId);
}
