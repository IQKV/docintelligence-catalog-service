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
import java.util.Objects;
import java.util.Set;

import com.iqkv.docintelligence.catalogservice.infrastructure.config.MasterVenueAiSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Translates a free-text venue query into a structured {@link QueryPlan} with the Ollama chat
 * model, so {@link MasterVenueSemanticSearchService} can apply hard JSONB filters and embed only
 * the residual free text.
 *
 * <p>Model output is never trusted verbatim: enum-like values are matched against the canonical
 * metadata vocabulary and dropped when unknown, capacities are clamped, and lengths are bounded.
 * Every failure — planner disabled, no chat model on the context, malformed model output — degrades
 * to {@link QueryPlan#empty(String)}, so semantic search never depends on the LLM being reachable.
 */
@Service
public class MasterVenueQueryPlanner {

  private static final Logger log = LoggerFactory.getLogger(MasterVenueQueryPlanner.class);

  /** Upper bound on the query length sent to the model and echoed back in the plan. */
  private static final int MAX_QUERY_CHARS = 500;

  private static final int MAX_AMENITIES = 8;

  /** Canonical venue-type values; mirrors the metadata vocabulary written by the ingest worker. */
  private static final Set<String> VENUE_TYPES = Set.of(
      "hotel_ballroom", "conference_center", "restaurant", "rooftop", "outdoor_garden",
      "historic_building", "warehouse_loft", "theatre_auditorium", "gallery",
      "sports_facility", "boat_cruise", "private_estate", "other");

  /** Canonical catering policies; anything else the model returns is discarded. */
  private static final Set<String> CATERING_POLICIES = Set.of(
      "in_house_exclusive", "in_house_preferred", "outside_allowed", "no_catering");

  private static final String SYSTEM_PROMPT = """
      You convert a natural-language venue search query into structured filters.
      Return ONLY the JSON object described by the format instructions.

      Rules:
      - city: the city name when the query names one, else null.
      - venueTypes: zero or more of [hotel_ballroom, conference_center, restaurant, rooftop,
        outdoor_garden, historic_building, warehouse_loft, theatre_auditorium, gallery,
        sports_facility, boat_cruise, private_estate, other]. Empty when unspecified.
      - minCapacity: the smallest maximum capacity a venue must have to host the described event,
        as an integer, else null.
      - maxCapacity: the largest capacity the venue should have, as an integer, else null.
      - cateringPolicy: one of [in_house_exclusive, in_house_preferred, outside_allowed,
        no_catering] when the query implies a catering requirement, else null.
      - amenities: short amenity keywords the venue must offer, else an empty list.
      - accessible: true only when the query requires wheelchair access, else null.
      - semanticQuery: the part of the query describing the venue's vibe, style or purpose with the
        extracted constraints removed. Never empty — repeat the query when nothing remains.
      """;

  private final ObjectProvider<ChatClient.Builder> chatClientBuilder;

  private final MasterVenueAiSearchProperties properties;

  public MasterVenueQueryPlanner(final ObjectProvider<ChatClient.Builder> chatClientBuilder,
                                 final MasterVenueAiSearchProperties properties) {
    this.chatClientBuilder = chatClientBuilder;
    this.properties = properties;
  }

  /**
   * Extracts a validated {@link QueryPlan} from {@code query}.
   *
   * @param query the raw natural-language query
   * @return the plan; an empty plan (embedding {@code query} unchanged) when planning is off or
   *     fails for any reason
   */
  public QueryPlan plan(final String query) {
    final ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
    if (!properties.enabled() || builder == null || query == null || query.isBlank()) {
      return QueryPlan.empty(query);
    }
    try {
      final QueryPlan raw = builder.build().prompt()
          .system(SYSTEM_PROMPT)
          .user("<user_input>\n" + truncate(query, MAX_QUERY_CHARS) + "\n</user_input>")
          .call()
          .entity(QueryPlan.class);
      return sanitize(raw, query);
    } catch (final RuntimeException ex) {
      log.warn("AI query planning failed, falling back to plain vector search: {}", ex.getMessage());
      return QueryPlan.empty(query);
    }
  }

  /** Drops values outside the canonical vocabulary and normalises the rest. */
  static QueryPlan sanitize(final QueryPlan raw, final String query) {
    if (raw == null) {
      return QueryPlan.empty(query);
    }
    final List<String> venueTypes = raw.venueTypes() == null ? List.of()
        : raw.venueTypes().stream()
            .map(MasterVenueQueryPlanner::lower)
            .filter(VENUE_TYPES::contains)
            .distinct()
            .toList();
    final List<String> amenities = raw.amenities() == null ? List.of()
        : raw.amenities().stream()
            .map(MasterVenueQueryPlanner::trimToNull)
            .filter(Objects::nonNull)
            .limit(MAX_AMENITIES)
            .toList();
    final String catering = lower(raw.cateringPolicy());
    final String semantic = raw.semanticQuery() == null || raw.semanticQuery().isBlank()
        ? query : raw.semanticQuery().strip();
    return new QueryPlan(
        trimToNull(raw.city()),
        venueTypes,
        positive(raw.minCapacity()),
        positive(raw.maxCapacity()),
        catering != null && CATERING_POLICIES.contains(catering) ? catering : null,
        amenities,
        raw.accessible(),
        truncate(semantic, MAX_QUERY_CHARS));
  }

  private static String lower(final String value) {
    final String trimmed = trimToNull(value);
    return trimmed == null ? null : trimmed.toLowerCase();
  }

  private static String trimToNull(final String value) {
    if (value == null) {
      return null;
    }
    final String trimmed = value.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static Integer positive(final Integer value) {
    return value == null || value <= 0 ? null : value;
  }

  private static String truncate(final String value, final int max) {
    return value.length() > max ? value.substring(0, max) : value;
  }

  /**
   * Structured filter set extracted from a query.
   *
   * <p>{@code semanticQuery} is the residual text embedded for the vector search; every other
   * field is an optional hard filter where {@code null} or an empty collection means "not applied".
   *
   * @param city           city name to match exactly, case-insensitively
   * @param venueTypes     canonical venue-type values; a venue matches when it carries any of them
   * @param minCapacity    smallest acceptable {@code capacity.max_total}
   * @param maxCapacity    largest acceptable {@code capacity.max_total}
   * @param cateringPolicy canonical {@code catering.policy} value
   * @param amenities      amenity keywords that must appear in the venue's amenity list
   * @param accessible     when {@code true}, only ADA-compliant venues
   * @param semanticQuery  free text embedded for the vector search
   */
  public record QueryPlan(
      String city,
      List<String> venueTypes,
      Integer minCapacity,
      Integer maxCapacity,
      String cateringPolicy,
      List<String> amenities,
      Boolean accessible,
      String semanticQuery) {

    /** The no-filter plan used whenever planning is unavailable: embed the query unchanged. */
    public static QueryPlan empty(final String query) {
      return new QueryPlan(null, List.of(), null, null, null, List.of(), null, query);
    }
  }
}
