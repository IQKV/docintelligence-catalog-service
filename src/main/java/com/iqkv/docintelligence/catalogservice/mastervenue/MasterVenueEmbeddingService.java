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

import com.iqkv.docintelligence.venue.MasterVenue;
import com.iqkv.docintelligence.venue.MasterVenueMapper;
import com.iqkv.docintelligence.venue.VenueEmbeddingText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Refreshes the pgvector embedding of a {@code MasterVenue} after any admin write that can
 * change its searchable text. A faithful replica of the embedding step
 * {@code docintelligence-master-venue-worker} runs at ingest time.
 *
 * <p>Called after every create, replace and patch in {@code MasterVenueServiceImpl} so an
 * edited venue is never left with a vector that still describes its old name, address, city
 * or metadata. The worker's backfill only recovers rows whose {@code embedding} column is
 * {@code NULL}, so without this refresh a changed venue would keep a stale vector forever.
 *
 * <p>Failures are logged and swallowed: a slow or unavailable Ollama instance never blocks the
 * write; the row stays searchable via keyword search and the column keeps its previous value
 * until the next write re-runs this path.
 */
@Service
class MasterVenueEmbeddingService {

  private static final Logger log = LoggerFactory.getLogger(MasterVenueEmbeddingService.class);

  private final EmbeddingModel embeddingModel;

  private final MasterVenueMapper masterVenueMapper;

  MasterVenueEmbeddingService(final EmbeddingModel embeddingModel,
                              final MasterVenueMapper masterVenueMapper) {
    this.embeddingModel = embeddingModel;
    this.masterVenueMapper = masterVenueMapper;
  }

  /**
   * Embeds {@code venue} and writes the vector to the database.
   * Swallows any {@link RuntimeException} so a slow or unavailable Ollama instance
   * never blocks an admin write.
   *
   * @param venue the venue to embed; must have a non-null {@code id}
   * @return {@code true} when the vector reached the database
   */
  boolean embed(final MasterVenue venue) {
    try {
      final float[] vector = embeddingModel.embed(VenueEmbeddingText.build(venue));
      masterVenueMapper.updateEmbedding(venue.getId(), vector);
      log.debug("embedded venue {} ({} dims)", venue.getId(), vector.length);
      return true;
    } catch (final RuntimeException ex) {
      log.warn("embedding skipped for venue {} — Ollama unavailable or slow: {}",
          venue.getId(), ex.getMessage());
      return false;
    }
  }
}
