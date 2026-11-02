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

import java.util.List;
import java.util.UUID;

import com.iqkv.docintelligence.venue.MasterVenueMapper;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Backfills the {@code embedding} column for master venue rows that were ingested before
 * the vector search feature was introduced, or that failed to embed on first attempt.
 *
 * <p>Runs on a fixed-rate schedule. Each cycle fetches a configurable batch of
 * un-embedded IDs and calls {@link VenueEmbeddingService#embedById(UUID)} for each one.
 * Ollama on CPU is slow, so the batch size is deliberately small (default: 20) to keep
 * cycle time predictable and leave headroom for foreground requests.
 *
 * <p>ShedLock prevents concurrent execution across replicas. The lock is held for at
 * most {@code PT10M} so a stuck cycle eventually releases.
 *
 * <p>Disabled when {@code iqkv.semantic-search.embedding.backfill.enabled=false}
 * (the default), so existing deployments are not surprised by Ollama calls on startup.
 * Set to {@code true} once {@code nomic-embed-text} is pulled in the Ollama instance.
 */
@Service
@ConditionalOnProperty(name = "iqkv.semantic-search.embedding.backfill.enabled",
    havingValue = "true", matchIfMissing = false)
public class VenueEmbeddingBackfillService {

  private static final Logger log = LoggerFactory.getLogger(VenueEmbeddingBackfillService.class);

  /** Number of venues embedded per scheduler cycle. */
  private static final int BATCH_SIZE = 20;

  private final MasterVenueMapper masterVenueMapper;

  private final VenueEmbeddingService embeddingService;

  public VenueEmbeddingBackfillService(final MasterVenueMapper masterVenueMapper,
                                       final VenueEmbeddingService embeddingService) {
    this.masterVenueMapper = masterVenueMapper;
    this.embeddingService = embeddingService;
  }

  /**
   * Runs every 5 minutes. Fetches up to {@value #BATCH_SIZE} un-embedded venue IDs and
   * generates their embeddings. Stops early if no un-embedded rows remain.
   */
  @Scheduled(fixedDelayString = "${iqkv.semantic-search.embedding.backfill.interval:PT5M}")
  @SchedulerLock(name = "venueEmbeddingBackfill", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
  public void backfill() {
    final List<UUID> ids = masterVenueMapper.findUnembeddedIds(BATCH_SIZE);
    if (ids.isEmpty()) {
      return;
    }
    log.info("embedding backfill: processing {} un-embedded venue(s)", ids.size());
    int succeeded = 0;
    for (final UUID id : ids) {
      embeddingService.embedById(id);
      succeeded++;
    }
    log.info("embedding backfill: completed {}/{}", succeeded, ids.size());
  }
}
