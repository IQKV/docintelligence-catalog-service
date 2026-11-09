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
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.iqkv.docintelligence.catalogservice.infrastructure.config.MasterVenueAiSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

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

  /**
   * Canonical venue-type values; mirrors the plain-English vocabulary written by the ingest
   * worker ({@code metadata.items} rows with {@code group=venue_type}).
   */
  private static final List<String> VENUE_TYPES = List.of(
      "Restaurant", "Hotel", "Bar or lounge", "Rooftop", "Gallery",
      "Warehouse or industrial space", "Studio", "Outdoor garden or terrace",
      "Town hall or community centre", "Conference centre", "Stadium or arena", "Museum",
      "Theatre or cinema", "Boat or yacht", "Marquee, tipi or barn", "Park, zoo or gardens",
      "Place of worship", "Private members club", "Nightclub", "Historic house or estate",
      "Beach or coastal venue", "Unclassified");

  /** Lowercase-to-canonical lookup so model output is matched regardless of casing. */
  private static final Map<String, String> VENUE_TYPE_BY_LOWER = VENUE_TYPES.stream()
      .collect(Collectors.toUnmodifiableMap(String::toLowerCase, Function.identity()));

  /** Canonical catering policies; anything else the model returns is discarded. */
  private static final List<String> CATERING_POLICIES = List.of(
      "In-house catering only", "External catering only",
      "In-house and external catering both allowed", "Catering not specified");

  /** Lowercase-to-canonical lookup so model output is matched regardless of casing. */
  private static final Map<String, String> CATERING_POLICY_BY_LOWER = CATERING_POLICIES.stream()
      .collect(Collectors.toUnmodifiableMap(String::toLowerCase, Function.identity()));

  private static final String SYSTEM_PROMPT = """
      You convert a natural-language venue search query into structured filters.
      Return ONLY the JSON object described by the format instructions.

      Rules:
      - city: the city name when the query names one, else null.
      - venueTypes: zero or more of [Restaurant, Hotel, Bar or lounge, Rooftop, Gallery,
        Warehouse or industrial space, Studio, Outdoor garden or terrace,
        Town hall or community centre, Conference centre, Stadium or arena, Museum,
        Theatre or cinema, Boat or yacht, Marquee, tipi or barn, Park, zoo or gardens,
        Place of worship, Private members club, Nightclub, Historic house or estate,
        Beach or coastal venue, Unclassified]. Empty when unspecified. Copy the value exactly,
        including its capitalisation.
      - minCapacity: the smallest maximum capacity a venue must have to host the described event,
        as an integer, else null.
      - maxCapacity: the largest capacity the venue should have, as an integer, else null.
      - cateringPolicy: one of [In-house catering only, External catering only,
        In-house and external catering both allowed, Catering not specified] when the query
        implies a catering requirement, else null.
      - amenities: short amenity keywords the venue must offer, else an empty list.
      - accessible: true only when the query requires wheelchair access, else null.
      - semanticQuery: the part of the query describing the venue's vibe, style or purpose with the
        extracted constraints removed. Never empty — repeat the query when nothing remains.
      """;

  /**
   * Converter for the model's JSON reply, built on a mapper that tolerates the stray tokens small
   * models append after the object. Jackson 3 enables {@code FAIL_ON_TRAILING_TOKENS} by default,
   * so a reply such as {@code {...}{...}} used to abort deserialisation and silently drop every
   * filter back to plain vector search.
   */
  static final BeanOutputConverter<QueryPlan> QUERY_PLAN_CONVERTER = new BeanOutputConverter<>(
      QueryPlan.class, JsonMapper.builder()
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
              DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build());

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
          .entity(QUERY_PLAN_CONVERTER);
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
            .map(MasterVenueQueryPlanner::trimToNull)
            .map(MasterVenueQueryPlanner::canonicalVenueType)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    final List<String> amenities = raw.amenities() == null ? List.of()
        : raw.amenities().stream()
            .map(MasterVenueQueryPlanner::trimToNull)
            .filter(Objects::nonNull)
            .limit(MAX_AMENITIES)
            .toList();
    final String catering = canonicalCateringPolicy(raw.cateringPolicy());
    final String semantic = raw.semanticQuery() == null || raw.semanticQuery().isBlank()
        ? query : raw.semanticQuery().strip();
    return new QueryPlan(
        trimToNull(raw.city()),
        venueTypes,
        positive(raw.minCapacity()),
        positive(raw.maxCapacity()),
        catering,
        amenities,
        raw.accessible(),
        truncate(semantic, MAX_QUERY_CHARS));
  }

  private static String canonicalVenueType(final String value) {
    return value == null ? null : VENUE_TYPE_BY_LOWER.get(value.toLowerCase());
  }

  private static String canonicalCateringPolicy(final String value) {
    final String trimmed = trimToNull(value);
    return trimmed == null ? null : CATERING_POLICY_BY_LOWER.get(trimmed.toLowerCase());
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
