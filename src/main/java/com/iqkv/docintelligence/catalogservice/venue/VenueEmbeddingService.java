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

import com.iqkv.docintelligence.venue.Venue;
import com.iqkv.docintelligence.venue.VenueMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Refreshes the pgvector embedding of a tenant {@code Venue} after any admin write that can
 * change its searchable text. Mirrors {@code MasterVenueEmbeddingService} for the tenant-scoped
 * {@code venues} table.
 *
 * <p>Called after every create, replace and patch in {@link VenueServiceImpl} so an edited venue
 * is never left with a vector that still describes its old name, address, city or metadata. The
 * {@link VenueEmbeddingBackfillService} scheduler only recovers rows whose {@code embedding}
 * column is {@code NULL}, so without this refresh a changed venue would keep a stale vector.
 *
 * <p>Failures are logged and swallowed: a slow or unavailable Ollama instance never blocks the
 * write; the row stays searchable via keyword search and the column keeps its previous value
 * until the next write re-runs this path.
 */
@Service
class VenueEmbeddingService {

  private static final Logger log = LoggerFactory.getLogger(VenueEmbeddingService.class);

  private final EmbeddingModel embeddingModel;

  private final VenueMapper venueMapper;

  VenueEmbeddingService(final EmbeddingModel embeddingModel, final VenueMapper venueMapper) {
    this.embeddingModel = embeddingModel;
    this.venueMapper = venueMapper;
  }

  /**
   * Embeds {@code venue} and writes the vector to the database.
   * Swallows any {@link RuntimeException} so a slow or unavailable Ollama instance
   * never blocks an admin write.
   *
   * @param venue the venue to embed; must have a non-null {@code id}
   * @return {@code true} when the vector reached the database
   */
  boolean embed(final Venue venue) {
    try {
      final float[] vector = embeddingModel.embed(buildEmbeddingText(venue));
      venueMapper.updateEmbedding(venue.getId(), vector);
      log.debug("embedded venue {} ({} dims)", venue.getId(), vector.length);
      return true;
    } catch (final RuntimeException ex) {
      log.warn("embedding skipped for venue {} — Ollama unavailable or slow: {}",
          venue.getId(), ex.getMessage());
      return false;
    }
  }

  /**
   * Builds the text fed to the embedding model.
   * Includes name, address, city, and raw metadata JSON so the model captures
   * venue type, amenities, capacity, catering, and restrictions.
   */
  static String buildEmbeddingText(final Venue venue) {
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
      sb.append(venue.getMetadata());
    }
    return sb.toString().strip();
  }
}
