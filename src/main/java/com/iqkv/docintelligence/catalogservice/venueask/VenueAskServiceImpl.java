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

package com.iqkv.docintelligence.catalogservice.venueask;

import java.util.List;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.infrastructure.config.VenueAskProperties;
import com.iqkv.docintelligence.catalogservice.venue.VenueService;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;
import com.iqkv.docintelligence.catalogservice.venueask.dto.VenueAskDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default {@link VenueAskService}: guards the question, gathers the tenant's own venue data and
 * grounds the model's answer in it.
 *
 * <p>Retrieval is done by {@link VenueContextAssembler} and synthesis by {@link VenueAskAssistant};
 * this class only wires the two together, applies the configured bounds, and honours the
 * catalog-wide degradation contract — when no answer can be grounded, the endpoint returns
 * {@code answered=false} with whatever sources were found rather than failing.
 */
@Service
class VenueAskServiceImpl implements VenueAskService {

  private static final Logger log = LoggerFactory.getLogger(VenueAskServiceImpl.class);

  private static final int DEFAULT_MAX_SOURCES = 6;

  private static final int MAX_MAX_SOURCES = 20;

  private static final int DEFAULT_LIST_LIMIT = 10;

  private static final int MAX_LIST_LIMIT = 50;

  /** Neutral body returned when no grounded answer could be produced. */
  static final String NO_ANSWER = "No answer could be grounded in the available venue data.";

  private final VenueContextAssembler contextAssembler;

  private final VenueAskAssistant assistant;

  private final VenueService venueService;

  private final VenueAskProperties properties;

  VenueAskServiceImpl(final VenueContextAssembler contextAssembler,
                      final VenueAskAssistant assistant,
                      final VenueService venueService,
                      final VenueAskProperties properties) {
    this.contextAssembler = contextAssembler;
    this.assistant = assistant;
    this.venueService = venueService;
    this.properties = properties;
  }

  @Override
  public VenueAskDtos.VenueAskResponse askVenue(final UUID venueId,
                                                final VenueAskDtos.VenueAskRequest request) {
    final String question = sanitise(request.question());
    final int maxSources = clamp(request.maxSources(), DEFAULT_MAX_SOURCES, MAX_MAX_SOURCES);
    // Resolves the venue in the active tenant schema; a foreign or unknown id throws here.
    final VenueDtos.VenuePublicResponse venue = venueService.getPublicVenue(venueId);
    final List<VenueAskDtos.VenueAskSource> sources =
        contextAssembler.retrieveSources(venueId, question, maxSources);

    final String answer = assistant.answer(question,
        truncate(VenueContextAssembler.renderVenueBlock(venue, sources), properties.maxContextChars()));
    final boolean answered = answer != null;
    if (!answered) {
      log.info("Venue ask produced no answer: venueId={}, sources={}", venueId, sources.size());
    }
    return new VenueAskDtos.VenueAskResponse(
        venueId,
        answered ? answer : NO_ANSWER,
        sources,
        answered,
        answered ? assistant.model() : null);
  }

  @Override
  public VenueAskDtos.VenueContextResponse getVenueContext(final UUID venueId) {
    return contextAssembler.assemble(venueId);
  }

  @Override
  public VenueAskDtos.VenueListAskResponse askVenues(final VenueAskDtos.VenueListAskRequest request) {
    final String question = sanitise(request.question());
    final int limit = clamp(request.limit(), DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT);

    final VenueDtos.VenuePublicSemanticSearchResponse search =
        contextAssembler.retrieveVenues(question, request.city(), limit);
    final List<VenueAskDtos.VenueListAskVenue> venues = search.items().stream()
        .map(item -> new VenueAskDtos.VenueListAskVenue(
            item.id(), item.name(), item.city(), item.primaryPhotoUrl(), item.similarity()))
        .toList();

    final String answer = assistant.answer(question,
        truncate(VenueContextAssembler.renderVenueListBlock(search.items()), properties.maxContextChars()));
    final boolean answered = answer != null;
    if (!answered) {
      log.info("Venue list ask produced no answer: venues={}", venues.size());
    }
    return new VenueAskDtos.VenueListAskResponse(
        answered ? answer : NO_ANSWER,
        venues,
        search.appliedFilters(),
        answered,
        answered ? assistant.model() : null);
  }

  /**
   * Rejects an empty or injection-bearing question and bounds its length.
   *
   * <p>Non-English input is allowed through: the catalog is multilingual, so a question in any
   * language is answered from the venue data in that language.
   */
  private String sanitise(final String question) {
    if (PromptGuard.isEffectivelyBlank(question)) {
      throw new EmptyMessageException();
    }
    final PromptGuard.Verdict verdict = PromptGuard.inspect(question);
    if (verdict.blocked()) {
      log.warn("Venue ask question rejected by the prompt-injection guard: rule={}", verdict.rule());
      throw new PromptInjectionException(verdict.rule());
    }
    return truncate(question.strip(), properties.maxInputChars());
  }

  private static int clamp(final Integer value, final int fallback, final int max) {
    if (value == null || value <= 0) {
      return fallback;
    }
    return Math.min(value, max);
  }

  private static String truncate(final String value, final int max) {
    if (value == null || max <= 0 || value.length() <= max) {
      return value;
    }
    return value.substring(0, max);
  }
}
