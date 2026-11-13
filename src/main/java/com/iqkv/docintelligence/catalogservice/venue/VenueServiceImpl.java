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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtoMapper;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;
import com.iqkv.docintelligence.venue.Venue;
import com.iqkv.docintelligence.venue.VenueMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

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
 *
 * <p>The list, detail and semantic-search projections resolve {@code primaryPhotoUrl} at read
 * time through {@link VenueAssetService#resolvePrimaryPhotoUrl(java.util.UUID)} — a presigned URL
 * is time-limited and cannot be stored on the row.
 */
@Service
@Transactional
public class VenueServiceImpl implements VenueService {

  private static final Logger log = LoggerFactory.getLogger(VenueServiceImpl.class);

  private final VenueMapper venueMapper;
  private final VenueAssetService venueAssetService;
  private final VenueEmbeddingService embeddingService;
  private final VenueSemanticSearchService semanticSearchService;
  private final ObjectMapper objectMapper;

  public VenueServiceImpl(final VenueMapper venueMapper,
                           final VenueAssetService venueAssetService,
                           final VenueEmbeddingService embeddingService,
                           final VenueSemanticSearchService semanticSearchService,
                           final ObjectMapper objectMapper) {
    this.venueMapper = venueMapper;
    this.venueAssetService = venueAssetService;
    this.embeddingService = embeddingService;
    this.semanticSearchService = semanticSearchService;
    this.objectMapper = objectMapper;
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
  public VenueDtos.VenuePublicResponse createPublicVenue(final VenueDtos.VenuePublicWriteRequest request) {
    final Venue venue = VenueDtoMapper.toPublicEntity(request);
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
                                                           final VenueDtos.VenuePublicWriteRequest request) {
    final Venue venue = requireVenue(id);
    VenueDtoMapper.applyPublicWrite(venue, request);
    venueMapper.update(venue);
    embeddingService.embed(venue);
    return toPublicResponse(requireVenue(id));
  }

  @Override
  public VenueDtos.VenuePublicResponse patchPublicVenue(final UUID id,
                                                         final VenueDtos.VenuePublicPatchRequest request) {
    final Venue venue = requireVenue(id);
    VenueDtoMapper.applyPublicPatch(venue, request);
    venueMapper.update(venue);
    embeddingService.embed(venue);
    return toPublicResponse(requireVenue(id));
  }

  @Override
  public VenueDtos.VenuePublicResponse patchVenueMetadata(final UUID id, final String metadata) {
    final Venue venue = requireVenue(id);
    venue.setMetadata(metadata);
    venueMapper.update(venue);
    embeddingService.embed(venue);
    return toPublicResponse(requireVenue(id));
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

  @Override
  public VenueDtos.VenuePublicResponse applyEnrichmentPatches(
      final UUID id, final List<VenueDtos.VenueEnrichmentSuggestion> patches) {
    final Venue venue = requireVenue(id);
    final String merged = mergePatches(venue.getMetadata(), patches);
    venue.setMetadata(merged);
    venueMapper.update(venue);
    embeddingService.embed(venue);
    return toPublicResponse(requireVenue(id));
  }

  private Venue requireVenue(final UUID id) {
    return venueMapper.findById(id)
        .orElseThrow(() -> new VenueNotFoundException(id));
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

  /**
   * Merges {@code patches} into the current metadata JSON string.
   *
   * <p>The document schema is {@code {_schema_version: int, items: [{group, field, value}]}}.
   * For each patch, every existing item matching the same {@code group}/{@code field} pair is
   * removed and the new item is appended — identical to the client-side {@code setFieldValue}
   * merge but executed atomically in the same transaction as the row update.
   *
   * <p>When the current metadata is {@code null}, empty, or not a parseable document, an empty
   * document is used as the base so a bad metadata string never blocks enrichment from landing.
   */
  @SuppressWarnings("unchecked")
  private String mergePatches(final String currentMetadata,
                               final List<VenueDtos.VenueEnrichmentSuggestion> patches) {
    try {
      Map<String, Object> doc;
      if (currentMetadata == null || currentMetadata.isBlank()) {
        doc = new java.util.LinkedHashMap<>();
        doc.put("_schema_version", 1);
        doc.put("items", new ArrayList<>());
      } else {
        doc = objectMapper.readerForMapOf(Object.class).readValue(currentMetadata);
        if (!(doc.get("items") instanceof List)) {
          doc.put("items", new ArrayList<>());
        }
      }
      final List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get("items");
      for (final VenueDtos.VenueEnrichmentSuggestion patch : patches) {
        items.removeIf(item ->
            patch.group().equals(item.get("group")) && patch.field().equals(item.get("field")));
        final Map<String, Object> newItem = new java.util.LinkedHashMap<>();
        newItem.put("group", patch.group());
        newItem.put("field", patch.field());
        // Coerce numeric strings to numbers so the stored type matches the ingest worker output.
        newItem.put("value", coerceValue(patch.value()));
        items.add(newItem);
      }
      return objectMapper.writeValueAsString(doc);
    } catch (final Exception ex) {
      log.warn("Could not merge enrichment patches into metadata, applying as raw replacement: {}", ex.getMessage());
      // Last-resort: build a minimal document from the patches alone.
      final List<Map<String, Object>> items = new ArrayList<>();
      for (final VenueDtos.VenueEnrichmentSuggestion patch : patches) {
        final Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("group", patch.group());
        item.put("field", patch.field());
        item.put("value", coerceValue(patch.value()));
        items.add(item);
      }
      try {
        return objectMapper.writeValueAsString(Map.of("_schema_version", 1, "items", items));
      } catch (final Exception inner) {
        throw new IllegalStateException("Failed to serialise enrichment patches", inner);
      }
    }
  }

  /**
   * Coerces a string value that looks like an integer or boolean to its native JSON type, so the
   * stored item matches the shape the ingest worker produces. Strings that are not numeric or
   * boolean are returned unchanged.
   */
  private static Object coerceValue(final String value) {
    if (value == null) return null;
    if ("true".equalsIgnoreCase(value)) return Boolean.TRUE;
    if ("false".equalsIgnoreCase(value)) return Boolean.FALSE;
    try {
      return Long.parseLong(value);
    } catch (final NumberFormatException ignored) {
    }
    try {
      return new java.math.BigDecimal(value);
    } catch (final NumberFormatException ignored) {
    }
    return value;
  }
}