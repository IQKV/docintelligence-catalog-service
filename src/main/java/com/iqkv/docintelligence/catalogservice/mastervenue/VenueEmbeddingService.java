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

package com.iqkv.docintelligence.catalogservice.mastervenue;

import java.util.UUID;

import com.iqkv.docintelligence.venue.MasterVenue;
import com.iqkv.docintelligence.venue.MasterVenueMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Generates and persists pgvector embeddings for master venue rows.
 *
 * <p>The text fed to the embedding model is a concise, information-dense
 * summary of the fields an event planner searches by — name, location and
 * raw metadata. Numeric / boolean metadata is intentionally excluded: vector
 * search covers semantic intent; exact-match filters (capacity, city) are
 * applied as SQL predicates on top of the ANN result.
 *
 * <p>Embedding calls are slow on CPU Ollama (~0.5–2 s each). Callers that
 * need to process many rows should call {@link #embedById(UUID)} in a loop
 * with appropriate rate-limiting (see {@link VenueEmbeddingBackfillService}).
 */
@Service
public class VenueEmbeddingService {

  private static final Logger log = LoggerFactory.getLogger(VenueEmbeddingService.class);

  private final EmbeddingModel embeddingModel;

  private final MasterVenueMapper masterVenueMapper;

  public VenueEmbeddingService(final EmbeddingModel embeddingModel,
                               final MasterVenueMapper masterVenueMapper) {
    this.embeddingModel = embeddingModel;
    this.masterVenueMapper = masterVenueMapper;
  }

  /**
   * Generates an embedding for the given venue and writes it to the database.
   * A failure to call the embedding model is logged and swallowed — the row
   * remains with {@code embedding = NULL} and will be retried by the backfill.
   *
   * @param id UUID of an existing master venue
   */
  public void embedById(final UUID id) {
    masterVenueMapper.findById(id).ifPresentOrElse(
        venue -> embed(venue),
        () -> log.warn("embedById: venue {} not found, skipping", id)
    );
  }

  /**
   * Generates an embedding for a venue object whose fields are already in memory.
   * Skips the extra {@code findById} read — preferred when the caller already holds
   * the entity (e.g. right after an upsert in the ingestion worker).
   *
   * @param venue the venue to embed; must have a non-null {@code id}
   */
  public void embed(final MasterVenue venue) {
    try {
      final var text = buildEmbeddingText(venue);
      final var vector = embeddingModel.embed(text);
      final float[] floats = toFloatArray(vector);
      masterVenueMapper.updateEmbedding(venue.getId(), floats);
      log.debug("embedded venue {} ({} dims)", venue.getId(), floats.length);
    } catch (final RuntimeException ex) {
      log.warn("embedding failed for venue {} — will retry on next backfill cycle: {}",
          venue.getId(), ex.getMessage());
    }
  }

  /**
   * Embeds a raw query string and returns the float vector.
   * Used by the semantic search endpoint to embed the incoming query.
   *
   * @param query the natural-language search query
   * @return float[] embedding vector
   */
  public float[] embedQuery(final String query) {
    final var vector = embeddingModel.embed(query);
    return toFloatArray(vector);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /**
   * Builds the text that is fed to the embedding model for a master venue.
   *
   * <p>Fields included: name, address, city, and the raw metadata JSON.
   * The metadata document carries venue type, amenities, restrictions, catering policy,
   * and capacity — all of which are semantically relevant for event-planner queries.
   */
  static String buildEmbeddingText(final MasterVenue venue) {
    final var sb = new StringBuilder();
    if (venue.getName() != null) {
      sb.append(venue.getName()).append(". ");
    }
    if (venue.getAddress() != null) {
      sb.append(venue.getAddress()).append(". ");
    }
    if (venue.getCity() != null) {
      sb.append(venue.getCity()).append(". ");
    }
    if (venue.getMetadata() != null && !venue.getMetadata().isBlank()) {
      // Include raw JSON — the model handles structured text well and all
      // semantically valuable fields (venue_type, amenities, etc.) live here.
      sb.append(venue.getMetadata());
    }
    return sb.toString().strip();
  }

  private static float[] toFloatArray(final java.util.List<Double> vector) {
    final float[] result = new float[vector.size()];
    for (int i = 0; i < vector.size(); i++) {
      result[i] = vector.get(i).floatValue();
    }
    return result;
  }
}
