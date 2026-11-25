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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtoMapper;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;
import com.iqkv.docintelligence.venue.Venue;
import com.iqkv.docintelligence.venue.VenueMapper;
import com.iqkv.docintelligence.venue.VenueShare;
import com.iqkv.docintelligence.venue.VenueShareMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases over tenant venue records — platform-admin CRUD plus the tenant-member read surface
 * and semantic search, backed by the tenant-scoped {@code venues} table via {@link VenueMapper}.
 * The active tenant schema is set by the controller before these methods run, so the
 * {@code MyBatisSchemaInterceptor} routes every statement to {@code t_{tenantKey}}.
 *
 * <p>Admin writes (create, replace, patch) refresh the pgvector embedding via
 * {@link VenueEmbeddingService} so an edited venue is never left with a vector that still
 * describes its old text. After an insert or update the row is re-read so
 * {@code created_at} / {@code updated_at} reflect the database-computed values.
 *
 * <p>Ownership-sensitive operations enforce that only the venue owner ({@code createdBy}) or a
 * user holding a {@link VenueShare#ROLE_ADMIN} grant may delete or manage shares.
 * {@link AccessDeniedException} is thrown on a violation so {@code GlobalExceptionHandler} maps
 * it to HTTP 403.
 */
@Service
@Transactional
public class VenueServiceImpl implements VenueService {

  private static final Logger log = LoggerFactory.getLogger(VenueServiceImpl.class);

  private final VenueMapper venueMapper;
  private final VenueShareMapper venueShareMapper;
  private final VenueAssetService venueAssetService;
  private final VenueEmbeddingService embeddingService;
  private final VenueSemanticSearchService semanticSearchService;

  public VenueServiceImpl(final VenueMapper venueMapper,
                           final VenueShareMapper venueShareMapper,
                           final VenueAssetService venueAssetService,
                           final VenueEmbeddingService embeddingService,
                           final VenueSemanticSearchService semanticSearchService) {
    this.venueMapper = venueMapper;
    this.venueShareMapper = venueShareMapper;
    this.venueAssetService = venueAssetService;
    this.embeddingService = embeddingService;
    this.semanticSearchService = semanticSearchService;
  }

  @Override
  @Transactional(readOnly = true)
  public VenueDtos.VenueListResponse searchVenues(final String search, final String city,
                                                  final String sortBy, final String sortDir,
                                                  final int limit, final int offset) {
    final var venues = venueMapper.search(search, city, sortBy, sortDir, limit, offset);
    return new VenueDtos.VenueListResponse(
        venues.stream().map(this::toSummaryView).toList(),
        venueMapper.countSearch(search, city));
  }

  @Override
  @Transactional(readOnly = true)
  public VenueDtos.VenueResponse getVenue(final UUID id) {
    return VenueDtoMapper.toResponse(requireVenue(id));
  }

  @Override
  public VenueDtos.VenueResponse createVenue(final VenueDtos.VenueWriteRequest request) {
    final Venue venue = VenueDtoMapper.toEntity(request);
    venueMapper.insert(venue);
    embeddingService.embed(venue);
    // Re-read so created_at / updated_at reflect the database-computed values.
    return VenueDtoMapper.toResponse(requireVenue(venue.getId()));
  }

  @Override
  public VenueDtos.VenueResponse replaceVenue(final UUID id, final VenueDtos.VenueWriteRequest request) {
    final Venue venue = requireVenue(id);
    VenueDtoMapper.applyWrite(venue, request);
    venueMapper.update(venue);
    embeddingService.embed(venue);
    return VenueDtoMapper.toResponse(requireVenue(id));
  }

  @Override
  public VenueDtos.VenueResponse patchVenue(final UUID id, final VenueDtos.VenuePatchRequest request) {
    final Venue venue = requireVenue(id);
    VenueDtoMapper.applyPatch(venue, request);
    venueMapper.update(venue);
    embeddingService.embed(venue);
    return VenueDtoMapper.toResponse(requireVenue(id));
  }

  @Override
  public void deleteVenue(final UUID id) {
    requireVenue(id);
    venueMapper.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public VenueDtos.VenuePublicResponse getPublicVenue(final UUID id) {
    final Venue venue = requireVenue(id);
    return toPublicResponse(venue);
  }

  @Override
  public VenueDtos.VenuePublicResponse createPublicVenue(final VenueDtos.VenuePublicWriteRequest request,
                                                         final UUID createdBy) {
    final Venue venue = VenueDtoMapper.toPublicEntity(request, createdBy, Venue.VISIBILITY_PUBLIC);
    // Apply ingestion defaults for curation fields the tenant does not supply.
    venue.setMetadata("{\"_schema_version\":1}");
    venue.setConfidence(BigDecimal.ONE);
    venue.setSource("tenant");
    venueMapper.insert(venue);
    embeddingService.embed(venue);
    return toPublicResponse(requireVenue(venue.getId()));
  }

  @Override
  public VenueDtos.VenuePublicResponse replacePublicVenue(final UUID id,
                                                           final VenueDtos.VenuePublicWriteRequest request,
                                                           final UUID requestingUserId) {
    final Venue venue = requireVenueAccessible(id, requestingUserId);
    VenueDtoMapper.applyPublicWrite(venue, request);
    venueMapper.update(venue);
    embeddingService.embed(venue);
    return toPublicResponse(requireVenue(id));
  }

  @Override
  public VenueDtos.VenuePublicResponse patchPublicVenue(final UUID id,
                                                         final VenueDtos.VenuePublicPatchRequest request,
                                                         final UUID requestingUserId) {
    final Venue venue = requireVenueAccessible(id, requestingUserId);
    VenueDtoMapper.applyPublicPatch(venue, request);
    venueMapper.update(venue);
    embeddingService.embed(venue);
    return toPublicResponse(requireVenue(id));
  }

  @Override
  public VenueDtos.VenuePublicResponse patchVenueMetadata(final UUID id, final String metadata,
                                                          final UUID requestingUserId) {
    final Venue venue = requireVenueAccessible(id, requestingUserId);
    venue.setMetadata(metadata);
    venueMapper.update(venue);
    embeddingService.embed(venue);
    return toPublicResponse(requireVenue(id));
  }

  @Override
  public void deletePublicVenue(final UUID id, final UUID requestingUserId) {
    final Venue venue = requireVenue(id);
    requireOwnerOrAdmin(venue, requestingUserId);
    venueMapper.deleteById(id);
  }

  @Override
  public void deletePublicVenues(final List<UUID> ids, final UUID requestingUserId) {
    // Fail fast: check every id before deleting any. Unknown ids pass silently (idempotent).
    for (final UUID id : ids) {
      venueMapper.findById(id).ifPresent(v -> requireOwnerOrAdmin(v, requestingUserId));
    }
    ids.forEach(venueMapper::deleteById);
  }

  @Override
  @Transactional(readOnly = true)
  public VenueDtos.VenueListResponse searchPublicVenues(final String search, final String city,
                                                         final String sortBy, final String sortDir,
                                                         final int limit, final int offset) {
    return searchVenues(search, city, sortBy, sortDir, limit, offset);
  }

  @Override
  public VenueDtos.VenueSemanticSearchResponse semanticSearch(final String query,
                                                              final String city,
                                                              final int limit) {
    log.info("Delegating tenant venue semantic search to the vector search service: q='{}', city={}, limit={}",
        query, city, limit);
    return semanticSearchService.search(query, city, limit);
  }

  @Override
  @Transactional(readOnly = true)
  public VenueDtos.VenuePublicSemanticSearchResponse semanticSearchPublic(final String query,
                                                                          final String city,
                                                                          final int limit) {
    log.info("Delegating tenant venue semantic search (safe projection): q='{}', city={}, limit={}",
        query, city, limit);
    final VenueDtos.VenueSemanticSearchResponse result = semanticSearchService.search(query, city, limit);
    return new VenueDtos.VenuePublicSemanticSearchResponse(
        result.items().stream().map(VenueDtoMapper::toPublicSemanticResult).toList(),
        result.totalElements(),
        result.appliedFilters());
  }

  private Venue requireVenue(final UUID id) {
    return venueMapper.findById(id)
        .orElseThrow(() -> new VenueNotFoundException(id));
  }

  /**
   * Loads the venue and verifies the requesting user may access it (owner or any share grantee).
   * Throws {@link VenueNotFoundException} when the venue does not exist,
   * {@link AccessDeniedException} when the user has no access.
   */
  private Venue requireVenueAccessible(final UUID id, final UUID userId) {
    return venueMapper.findByIdForUser(id, userId)
        .orElseThrow(() -> {
          // Distinguish 404 (venue gone) from 403 (venue exists but no access).
          if (venueMapper.findById(id).isPresent()) {
            return new AccessDeniedException("Access denied to venue: " + id);
          }
          return new VenueNotFoundException(id);
        });
  }

  /**
   * Throws {@link AccessDeniedException} when the requesting user is neither the owner nor holds
   * an {@link VenueShare#ROLE_ADMIN} share grant on the venue. A {@code null} requestingUserId
   * means the caller is a platform admin and bypasses the check.
   */
  private void requireOwnerOrAdmin(final Venue venue, final UUID requestingUserId) {
    if (requestingUserId == null) {
      return; // platform admin bypass
    }
    if (requestingUserId.equals(venue.getCreatedBy())) {
      return;
    }
    final boolean isAdmin = venueShareMapper.findByVenueIdAndUserId(venue.getId(), requestingUserId)
        .map(s -> VenueShare.ROLE_ADMIN.equals(s.getRole()))
        .orElse(false);
    if (!isAdmin) {
      throw new AccessDeniedException("Only the venue owner or an ADMIN grantee may perform this action");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public VenueDtos.VenueShareListResponse getShares(final UUID venueId) {
    requireVenue(venueId);
    return new VenueDtos.VenueShareListResponse(
        venueShareMapper.findByVenueId(venueId).stream()
            .map(VenueDtoMapper::toShareResponse)
            .toList());
  }

  @Override
  public VenueDtos.VenueShareResponse addShare(final UUID venueId,
                                               final VenueDtos.VenueShareRequest request,
                                               final UUID grantedBy) {
    final Venue venue = requireVenue(venueId);
    requireOwnerOrAdmin(venue, grantedBy);
    final VenueShare share = new VenueShare();
    share.setId(UUID.randomUUID());
    share.setVenueId(venueId);
    share.setSharedWithUserId(request.sharedWithUserId());
    share.setRole(request.role());
    share.setGrantedBy(grantedBy);
    venueShareMapper.insert(share);
    return VenueDtoMapper.toShareResponse(
        venueShareMapper.findByVenueIdAndUserId(venueId, request.sharedWithUserId())
            .orElseThrow(() -> new VenueShareNotFoundException(venueId, request.sharedWithUserId())));
  }

  @Override
  public VenueDtos.VenueShareResponse updateShareRole(final UUID venueId,
                                                      final UUID sharedWithUserId,
                                                      final String role,
                                                      final UUID requestingUserId) {
    final Venue venue = requireVenue(venueId);
    requireOwnerOrAdmin(venue, requestingUserId);
    if (venueShareMapper.findByVenueIdAndUserId(venueId, sharedWithUserId).isEmpty()) {
      throw new VenueShareNotFoundException(venueId, sharedWithUserId);
    }
    venueShareMapper.updateRole(venueId, sharedWithUserId, role);
    return VenueDtoMapper.toShareResponse(
        venueShareMapper.findByVenueIdAndUserId(venueId, sharedWithUserId)
            .orElseThrow(() -> new VenueShareNotFoundException(venueId, sharedWithUserId)));
  }

  @Override
  public void removeShare(final UUID venueId, final UUID sharedWithUserId,
                          final UUID requestingUserId) {
    final Venue venue = requireVenue(venueId);
    requireOwnerOrAdmin(venue, requestingUserId);
    if (venueShareMapper.findByVenueIdAndUserId(venueId, sharedWithUserId).isEmpty()) {
      throw new VenueShareNotFoundException(venueId, sharedWithUserId);
    }
    venueShareMapper.deleteByVenueIdAndUserId(venueId, sharedWithUserId);
  }

  /**
   * Projects a venue to the list-item view, resolving its time-limited primary photo URL at read
   * time.
   */
  private VenueDtos.VenueSummaryView toSummaryView(final Venue venue) {
    return VenueDtoMapper.toSummaryView(venue, venueAssetService.resolvePrimaryPhotoUrl(venue.getId()));
  }

  /**
   * Projects a venue to the tenant safe view, resolving its time-limited primary photo URL at
   * read time.
   */
  private VenueDtos.VenuePublicResponse toPublicResponse(final Venue venue) {
    return VenueDtoMapper.toPublicResponse(venue, venueAssetService.resolvePrimaryPhotoUrl(venue.getId()));
  }
}