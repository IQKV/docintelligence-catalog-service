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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI-assisted single-field extraction for the venue "fill from source" flow.
 *
 * <p>The venue metadata schema is strictly predefined, so a free-form chat is not useful. Instead
 * the tenant picks one concrete {@code metadata.items} row ({@code group}/{@code field}) and one
 * preloaded source document ({@code sourceTextId}) — the raw text extracted from the venue's PDF —
 * and the model extracts just that one value from that one source, constrained to the field's
 * declared type and allowed values.
 *
 * <p>Structured output follows the same degradation contract as {@link VenueQueryPlanner}: any
 * failure (model unreachable, malformed output, feature disabled, no source text, source not owned
 * by the venue) returns {@code found=false} rather than surfacing an error to the tenant UI.
 */
@Service
class VenueEnrichmentAssistantService {

  private static final Logger log = LoggerFactory.getLogger(VenueEnrichmentAssistantService.class);

  /** Source documents can be long; cap what is sent to the model to bound the prompt size. */
  private static final int MAX_SOURCE_CHARS = 6000;

  private static final String SYSTEM_PROMPT = """
      You are a venue catalog data extraction assistant. You are given the raw source text of a
      venue's document and must extract exactly ONE catalog field from it.

      Field to extract:
      - group: %s
      - field: %s
      - label: %s
      - expected value type: %s
      %s
      %s

      Rules:
      - Use ONLY the source text in the user message. Never invent a value that is not stated there.
      - For enumerated fields the value MUST be exactly one of the allowed values listed above.
      - For numeric fields return only the number, with no units or thousands separators.
      - For boolean fields return "true" or "false".
      - If the source text does not state a value for this field, set found to false and leave value empty.
      - "evidence" must be the exact sentence or phrase copied verbatim from the source text that the
        value came from.

      Return ONLY the JSON object described by the format instructions — no extra text.
      """;

  /**
   * Tolerant mapper — same configuration as {@link VenueQueryPlanner#QUERY_PLAN_CONVERTER} to
   * handle stray tokens small models append after the JSON object.
   */
  private static final BeanOutputConverter<ExtractionResult> EXTRACTION_CONVERTER =
      new BeanOutputConverter<>(ExtractionResult.class, JsonMapper.builder()
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
   * Extracts the single field named in {@code request} from the chosen source document.
   *
   * <p>Loads the venue (404 via {@link VenueNotFoundException} when absent) and the requested
   * text-content row, then asks the model for one value constrained to the field's type and
   * allowed values. When the chat model is unavailable or feature-flagged off, when the source
   * cannot be resolved, or when the model reports no match, returns {@code found=false} so the UI
   * degrades gracefully.
   *
   * @param venueId owning venue — the source row must belong to it
   * @param request the concrete field to fill and the chosen source document
   * @return the extracted value plus verbatim evidence, or {@code found=false}
   */
  public VenueDtos.VenueFieldExtractionResponse extract(
      final UUID venueId,
      final VenueDtos.VenueFieldExtractionRequest request) {

    final ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
    if (!properties.enabled() || builder == null) {
      log.info("Venue field extraction is disabled or chat model unavailable, venueId={}", venueId);
      return notFound(request, null);
    }

    // Resolves the venue in the active tenant schema — throws VenueNotFoundException when absent.
    venueService.getPublicVenue(venueId);

    final String sourceText = resolveSourceText(venueId, request.sourceTextId());
    if (sourceText == null || sourceText.isBlank()) {
      log.info("No source text available for field extraction, venueId={}, field={}.{}",
          venueId, request.group(), request.field());
      return notFound(request, null);
    }

    final String systemPrompt = SYSTEM_PROMPT.formatted(
        request.group(),
        request.field(),
        Objects.toString(request.fieldLabel(), request.field()),
        Objects.toString(request.valueType(), "text"),
        allowedValuesBlock(request.allowedValues()),
        currentValueBlock(request.currentValue()));

    try {
      final ExtractionResult result = builder.build().prompt()
          .system(systemPrompt + "\n" + EXTRACTION_CONVERTER.getFormat())
          .user(truncate(sourceText, MAX_SOURCE_CHARS))
          .call()
          .entity(EXTRACTION_CONVERTER);

      if (result == null
          || !Boolean.TRUE.equals(result.found())
          || result.value() == null
          || result.value().isBlank()) {
        log.info("Venue field extraction found no value, venueId={}, field={}.{}",
            venueId, request.group(), request.field());
        return notFound(request, result == null ? null : result.evidence());
      }

      log.info("Venue field extraction found a value, venueId={}, field={}.{}",
          venueId, request.group(), request.field());
      return new VenueDtos.VenueFieldExtractionResponse(
          request.group(),
          request.field(),
          result.value().strip(),
          result.evidence() == null ? "" : result.evidence().strip(),
          true);

    } catch (final RuntimeException ex) {
      log.warn("Venue field extraction failed, returning not-found: venueId={}, error={}",
          venueId, ex.getMessage());
      return notFound(request, null);
    }
  }

  /**
   * Returns the content of the chosen source row, or the earliest row when no explicit choice was
   * made. Returns {@code null} when the venue has no rows or the requested row does not belong to
   * the venue — the row is resolved from the venue's own rows so ownership is enforced.
   */
  private String resolveSourceText(final UUID venueId, final UUID sourceTextId) {
    try {
      final var rows = venueTextContentService.listByVenue(venueId).items();
      if (rows.isEmpty()) {
        return null;
      }
      if (sourceTextId == null) {
        // The list is ordered by created_at ascending — the first row is the original source.
        return rows.get(0).content();
      }
      return rows.stream()
          .filter(row -> sourceTextId.equals(row.id()))
          .map(row -> row.content())
          .findFirst()
          .orElse(null);
    } catch (final RuntimeException ex) {
      log.debug("Could not load text content for field extraction, venueId={}: {}",
          venueId, ex.getMessage());
      return null;
    }
  }

  private static String allowedValuesBlock(final List<String> allowedValues) {
    if (allowedValues == null || allowedValues.isEmpty()) {
      return "";
    }
    return "- allowed values (choose exactly one): " + String.join(", ", allowedValues);
  }

  private static String currentValueBlock(final String currentValue) {
    if (currentValue == null || currentValue.isBlank()) {
      return "";
    }
    return "- currently stored value (may be empty or outdated): " + currentValue;
  }

  private static VenueDtos.VenueFieldExtractionResponse notFound(
      final VenueDtos.VenueFieldExtractionRequest request, final String evidence) {
    return new VenueDtos.VenueFieldExtractionResponse(
        request.group(),
        request.field(),
        null,
        evidence,
        false);
  }

  private static String truncate(final String value, final int max) {
    return value.length() > max ? value.substring(0, max) : value;
  }

  /**
   * Internal model output record — deserialized from the chat model's JSON reply. Not exposed
   * outside this service; the controller-facing type is
   * {@link VenueDtos.VenueFieldExtractionResponse}.
   */
  private record ExtractionResult(String value, String evidence, Boolean found) {
  }
}
