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

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.infrastructure.config.MasterVenueAiSearchProperties;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueTextContentDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI-assisted venue profile enrichment — translates a tenant's free-text description of their
 * venue into a human-readable reply and a list of structured {@code metadata.items} patches the
 * UI can apply directly via {@code PATCH /{venueId}/metadata}.
 *
 * <p>The model is asked to return both a conversational answer and a structured patch list in a
 * single call. Structured output follows the same degradation contract as {@link VenueQueryPlanner}:
 * any failure (model unreachable, malformed output, feature disabled) falls back to a plain-text
 * reply with an empty patch list rather than surfacing an error to the tenant UI.
 *
 * <p>The current venue name, city, and the earliest linked text-content row (the original
 * document from which the venue was created, when available) are loaded from {@link VenueService}
 * and {@link VenueTextContentService} and injected into the system prompt so the model has rich
 * source context even when the caller omits {@code currentMetadata}.
 */
@Service
class VenueEnrichmentAssistantService {

  private static final Logger log = LoggerFactory.getLogger(VenueEnrichmentAssistantService.class);

  private static final int MAX_MESSAGE_CHARS = 1000;

  private static final String SYSTEM_PROMPT = """
      You are a venue profile assistant helping a venue manager fill in their venue's catalog data.
      The venue is named "%s" and is located in "%s".

      %s

      The tenant has described their venue or asked a question. Your job is to:
      1. Reply conversationally to help them understand what profile fields to fill in.
      2. Extract structured field suggestions from their description that map to the metadata schema.

      The metadata schema uses a flat items array. Each item has:
      - group: one of [venue_type, capacity, catering, amenities, accessibility]
      - field: the specific field within the group
      - value: the suggested value as a string

      Known fields per group:
      - venue_type / venue_type: one of [Restaurant, Hotel, Bar or lounge, Rooftop, Gallery,
        Warehouse or industrial space, Studio, Outdoor garden or terrace,
        Town hall or community centre, Conference centre, Stadium or arena, Museum,
        Theatre or cinema, Boat or yacht, Marquee, tipi or barn, Park, zoo or gardens,
        Place of worship, Private members club, Nightclub, Historic house or estate,
        Beach or coastal venue, Unclassified]
      - capacity / max_total: maximum total guest capacity as a number
      - capacity / min_total: minimum total guest capacity as a number
      - catering / policy: one of [In-house catering only, External catering only,
        In-house and external catering both allowed, Catering not specified]
      - amenities / amenities: comma-separated list of amenity keywords
      - accessibility / ada_compliant: true or false

      %s

      Only suggest fields you can derive with reasonable confidence from the tenant's message.
      Return ONLY the JSON object described by the format instructions — no extra text.
      """;

  /**
   * Tolerant mapper — same configuration as {@link VenueQueryPlanner#QUERY_PLAN_CONVERTER} to
   * handle stray tokens small models append after the JSON object.
   */
  private static final BeanOutputConverter<EnrichmentPlan> ENRICHMENT_PLAN_CONVERTER =
      new BeanOutputConverter<>(EnrichmentPlan.class, JsonMapper.builder()
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
              DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build());

  private final VenueService venueService;
  private final VenueTextContentService venueTextContentService;
  private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
  private final MasterVenueAiSearchProperties properties;

  VenueEnrichmentAssistantService(final VenueService venueService,
                                  final VenueTextContentService venueTextContentService,
                                  final ObjectProvider<ChatClient.Builder> chatClientBuilder,
                                  final MasterVenueAiSearchProperties properties) {
    this.venueService = venueService;
    this.venueTextContentService = venueTextContentService;
    this.chatClientBuilder = chatClientBuilder;
    this.properties = properties;
  }

  /**
   * Produces enrichment suggestions for {@code venueId} based on the tenant's {@code request}.
   *
   * <p>Loads the venue and the earliest linked text-content row (the original source document
   * when available) to give the model richer context. When the chat model is unavailable or
   * feature-flagged off, returns a fixed fallback reply and an empty patch list so the UI
   * degrades gracefully.
   *
   * @param venueId owning venue — used to load name, city and source text for the system prompt
   * @param request the tenant's message, optional field hint, and optional current metadata
   * @return reply text and structured patch suggestions (may be empty)
   */
  public VenueDtos.VenueEnrichmentSuggestionResponse suggest(
      final UUID venueId,
      final VenueDtos.VenueEnrichmentSuggestionRequest request) {

    final ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
    if (!properties.enabled() || builder == null) {
      log.info("Venue enrichment assistant is disabled or chat model unavailable, venueId={}", venueId);
      return fallback();
    }

    final VenueDtos.VenuePublicResponse venue = venueService.getPublicVenue(venueId);
    final String name = Objects.toString(venue.name(), "this venue");
    final String city = Objects.toString(venue.city(), "an unspecified city");

    final String sourceContext = resolveSourceContext(venueId);
    final String fieldContext = request.fieldHint() != null
        ? "The tenant is specifically asking about the '" + request.fieldHint() + "' field group."
        : "";
    final String metadataContext = request.currentMetadata() != null
        ? "Current metadata (already filled): " + truncate(request.currentMetadata(), 2000)
        : "";
    final String contextBlock = (fieldContext + "\n" + metadataContext).strip();

    final String systemPrompt = SYSTEM_PROMPT.formatted(name, city, sourceContext, contextBlock);
    final String userMessage = truncate(request.message(), MAX_MESSAGE_CHARS);

    try {
      final EnrichmentPlan plan = builder.build().prompt()
          .system(systemPrompt + "\n" + ENRICHMENT_PLAN_CONVERTER.getFormat())
          .user(userMessage)
          .call()
          .entity(ENRICHMENT_PLAN_CONVERTER);

      if (plan == null || plan.reply() == null || plan.reply().isBlank()) {
        log.warn("Venue enrichment model returned empty plan, venueId={}", venueId);
        return fallback();
      }

      final List<VenueDtos.VenueEnrichmentSuggestion> patches = plan.suggestedPatches() == null
          ? List.of()
          : plan.suggestedPatches().stream()
              .filter(p -> p.group() != null && p.field() != null && p.value() != null)
              .toList();

      log.info("Venue enrichment produced {} suggestion(s), venueId={}", patches.size(), venueId);
      return new VenueDtos.VenueEnrichmentSuggestionResponse(plan.reply(), patches);

    } catch (final RuntimeException ex) {
      log.warn("Venue enrichment assistant failed, returning fallback: venueId={}, error={}",
          venueId, ex.getMessage());
      return fallback();
    }
  }

  /**
   * Loads the earliest text-content row linked to {@code venueId} and returns a prompt snippet
   * containing the original source text. Returns an empty string when no rows exist, so the
   * prompt slot is simply blank and the model proceeds without it.
   */
  private String resolveSourceContext(final UUID venueId) {
    try {
      final var rows = venueTextContentService.listByVenue(venueId).items();
      if (rows.isEmpty()) {
        return "";
      }
      // The list is ordered by created_at ascending — the first row is the original source.
      final String sourceText = rows.get(0).content();
      return "Original source document (extracted at venue creation):\n"
             + truncate(sourceText, 3000);
    } catch (final RuntimeException ex) {
      log.debug("Could not load text content for venue enrichment context, venueId={}: {}",
          venueId, ex.getMessage());
      return "";
    }
  }

  private static VenueDtos.VenueEnrichmentSuggestionResponse fallback() {
    return new VenueDtos.VenueEnrichmentSuggestionResponse(
        "AI enrichment assistance is currently unavailable. "
        + "Please fill in the venue profile fields manually.",
        List.of());
  }

  private static String truncate(final String value, final int max) {
    return value.length() > max ? value.substring(0, max) : value;
  }

  /**
   * Internal model output record — deserialized from the chat model's JSON reply.
   * Not exposed outside this service; the controller-facing type is
   * {@link VenueDtos.VenueEnrichmentSuggestionResponse}.
   */
  private record EnrichmentPlan(
      String reply,
      List<VenueDtos.VenueEnrichmentSuggestion> suggestedPatches) {
  }
}
