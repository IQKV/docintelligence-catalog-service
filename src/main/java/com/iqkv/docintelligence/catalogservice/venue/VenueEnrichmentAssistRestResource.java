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

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI-assisted single-field extraction — tenant UI "fill from source" flow.
 *
 * <p>The venue metadata schema is strictly predefined, so instead of a free-form chat the tenant
 * picks one concrete {@code metadata.items} row ({@code group}/{@code field}) and one preloaded
 * source document ({@code sourceTextId}), and the endpoint returns the value found for that field
 * in that source together with the verbatim evidence it was taken from.
 *
 * <p>The endpoint degrades gracefully: when the chat model is unreachable, the AI feature flag is
 * off, the source cannot be resolved, or the source holds no value for the field, it returns
 * {@code found=false} rather than an error, so the UI always receives a usable response.
 *
 * <p>The {@code /apply} endpoint merges the accepted patches into the venue's metadata document
 * and persists them. It performs no AI inference — it is a pure metadata write that takes
 * the same {@code {group, field, value}} triples the extraction endpoint produces.
 *
 * <p>Tenant context is resolved by {@code TenantExtractionFilter} from the {@code X-Tenant-ID}
 * header or the JWT {@code tenant_id} claim, routing the venue lookup to the correct tenant schema.
 */
@RestController
@RequestMapping("/api/v1/catalog/venues/{venueId}/enrichment")
@Tag(name = "Venue Enrichment", description = "AI-assisted venue profile field extraction for the tenant UI fill-from-source flow")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'MEMBER')")
public class VenueEnrichmentAssistRestResource {

  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  private final VenueEnrichmentAssistantService enrichmentAssistantService;
  private final VenueService venueService;

  public VenueEnrichmentAssistRestResource(final VenueEnrichmentAssistantService enrichmentAssistantService,
                                           final VenueService venueService) {
    this.enrichmentAssistantService = enrichmentAssistantService;
    this.venueService = venueService;
  }

  @PostMapping("/extract")
  @Operation(
      summary = "Extract one venue metadata field from a source document",
      description = "Extracts the concrete field identified by group/field from the chosen source "
                    + "document (sourceTextId), constrained to the field's declared valueType and "
                    + "allowedValues. Returns the extracted value plus the verbatim evidence it was "
                    + "taken from, or found=false when no value could be found. "
                    + "When the AI model is unavailable the endpoint still returns 200 with "
                    + "found=false rather than an error. Responds 404 when the venue does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Extraction attempted — inspect found",
                   content = @Content(schema = @Schema(implementation = VenueDtos.VenueFieldExtractionResponse.class))),
      @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueDtos.VenueFieldExtractionResponse> extract(
      @Parameter(description = "Owning venue UUID") @PathVariable final UUID venueId,
      @Valid @RequestBody final VenueDtos.VenueFieldExtractionRequest request) {
    return ResponseEntity.ok(enrichmentAssistantService.extract(venueId, request));
  }

  @PostMapping("/apply")
  @Operation(
      summary = "Apply accepted field patches to the venue metadata",
      description = "Merges the supplied {group, field, value} patches into the venue's metadata "
                    + "document and persists the result. Performs no AI inference — this is a pure "
                    + "metadata write. Each patch upserts one items row identified by group+field. "
                    + "Responds 404 when the venue does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Patches applied — returns updated venue",
                   content = @Content(schema = @Schema(implementation = VenueDtos.VenuePublicResponse.class))),
      @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueDtos.VenuePublicResponse> apply(
      @Parameter(description = "Owning venue UUID") @PathVariable final UUID venueId,
      @Valid @RequestBody final VenueDtos.VenueEnrichmentApplyRequest request) {
    final VenueDtos.VenuePublicResponse venue = venueService.getPublicVenue(venueId);
    final String merged = mergePatches(venue.metadata(), request.patches());
    return ResponseEntity.ok(venueService.patchVenueMetadata(venueId, merged));
  }

  /**
   * Upserts each patch into the flat {@code items} array of the metadata JSON document.
   * An existing row with the same group+field is replaced; a new row is appended.
   * When the current metadata is null or unparseable an empty document is used as the base.
   */
  private static String mergePatches(
      final String currentMetadata,
      final List<VenueDtos.VenueEnrichmentSuggestion> patches) {
    // Parse current document, or start from an empty one.
    List<java.util.Map<String, Object>> items = new ArrayList<>();
    int schemaVersion = 1;
    if (currentMetadata != null && !currentMetadata.isBlank()) {
      try {
        final var doc = JSON_MAPPER.readValue(currentMetadata,
            new TypeReference<java.util.Map<String, Object>>() {});
        if (doc.get("items") instanceof List<?> raw) {
          for (final Object entry : raw) {
            if (entry instanceof java.util.Map<?, ?> m) {
              @SuppressWarnings("unchecked")
              final java.util.Map<String, Object> row = (java.util.Map<String, Object>) m;
              items.add(new java.util.LinkedHashMap<>(row));
            }
          }
        }
        if (doc.get("_schema_version") instanceof Number n) {
          schemaVersion = n.intValue();
        }
      } catch (final Exception ignored) {
        // Unparseable metadata — start fresh.
        items = new ArrayList<>();
      }
    }

    // Upsert each patch: replace existing row or append.
    for (final VenueDtos.VenueEnrichmentSuggestion patch : patches) {
      final String group = patch.group();
      final String field = patch.field();
      boolean replaced = false;
      for (final java.util.Map<String, Object> row : items) {
        if (group.equals(row.get("group")) && field.equals(row.get("field"))) {
          row.put("value", patch.value());
          replaced = true;
          break;
        }
      }
      if (!replaced) {
        final java.util.Map<String, Object> newRow = new java.util.LinkedHashMap<>();
        newRow.put("group", group);
        newRow.put("field", field);
        newRow.put("value", patch.value());
        items.add(newRow);
      }
    }

    final java.util.Map<String, Object> doc = new java.util.LinkedHashMap<>();
    doc.put("_schema_version", schemaVersion);
    doc.put("items", items);
    try {
      return JSON_MAPPER.writeValueAsString(doc);
    } catch (final Exception ex) {
      throw new IllegalStateException("Failed to serialise merged metadata", ex);
    }
  }
}
