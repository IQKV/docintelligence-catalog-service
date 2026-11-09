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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * describes its old text. The {@link VenueEmbeddingBackfillService} scheduler recovers rows whose
 * embedding is still {@code NULL}. After an insert or update the row is re-read so
 * {@code created_at} / {@code updated_at} reflect the database-computed values, mirroring the
 * master venue service.
 */
@Service
@Transactional
public class VenueServiceImpl implements VenueService {

  private static final Logger log = LoggerFactory.getLogger(VenueServiceImpl.class);

  private final VenueMapper venueMapper;
  private final VenueEmbeddingService embeddingService;
  private final VenueSemanticSearchService semanticSearchService;

  public VenueServiceImpl(final VenueMapper venueMapper,
                           final VenueEmbeddingService embeddingService,
                           final VenueSemanticSearchService semanticSearchService) {
    this.venueMapper = venueMapper;
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
        venues.stream().map(VenueDtoMapper::toSummaryView).toList(),
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
    return VenueDtoMapper.toPublicResponse(requireVenue(id));
  }

  @Override
  public VenueDtos.VenuePublicResponse createPublicVenue(final VenueDtos.VenuePublicWriteRequest request) {
    final Venue venue = VenueDtoMapper.toPublicEntity(request);
    // Apply ingestion defaults for curation fields the tenant does not supply.
    venue.setMetadata("{\"_schema_version\":1}");
    venue.setConfidence(BigDecimal.ONE);
    venue.setSource("tenant");
    venueMapper.insert(venue);
    embeddingService.embed(venue);
    return VenueDtoMapper.toPublicResponse(requireVenue(venue.getId()));
  }

  @Override
  public VenueDtos.VenuePublicResponse replacePublicVenue(final UUID id,
                                                           final VenueDtos.VenuePublicWriteRequest request) {
    final Venue venue = requireVenue(id);
    VenueDtoMapper.applyPublicWrite(venue, request);
    venueMapper.update(venue);
    embeddingService.embed(venue);
    return VenueDtoMapper.toPublicResponse(requireVenue(id));
  }

  @Override
  public VenueDtos.VenuePublicResponse patchPublicVenue(final UUID id,
                                                         final VenueDtos.VenuePublicPatchRequest request) {
    final Venue venue = requireVenue(id);
    VenueDtoMapper.applyPublicPatch(venue, request);
    venueMapper.update(venue);
    embeddingService.embed(venue);
    return VenueDtoMapper.toPublicResponse(requireVenue(id));
  }

  @Override
  public void deletePublicVenue(final UUID id) {
    requireVenue(id);
    venueMapper.deleteById(id);
  }

  @Override
  public void deletePublicVenues(final List<UUID> ids) {
    // No existence check: DELETE of an unknown id is a no-op, which is what makes the batch idempotent.
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

  private Venue requireVenue(final UUID id) {
    return venueMapper.findById(id)
        .orElseThrow(() -> new VenueNotFoundException(id));
  }
}
