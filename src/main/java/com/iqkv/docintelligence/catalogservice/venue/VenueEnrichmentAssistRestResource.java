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

/**
 * AI-assisted venue profile enrichment — tenant UI "Fill with AI" flow.
 *
 * <p>Accepts a free-text message from the tenant (and optionally a field hint and the current
 * metadata document) and returns a conversational reply together with a structured list of
 * {@code metadata.items} patches the UI can apply directly via
 * {@code PATCH /api/v1/catalog/venues/{id}/metadata}.
 *
 * <p>The endpoint degrades gracefully: when the chat model is unreachable or the AI feature flag
 * is off, it returns a plain-text fallback reply and an empty patch list rather than an error,
 * so the UI always receives a usable response.
 *
 * <p>Tenant context is resolved by {@code TenantExtractionFilter} from the {@code X-Tenant-ID}
 * header or the JWT {@code tenant_id} claim, routing the venue lookup to the correct tenant schema.
 */
@RestController
@RequestMapping("/api/v1/catalog/venues/{venueId}/enrichment")
@Tag(name = "Venue Enrichment", description = "AI-assisted venue profile enrichment for the tenant UI Fill-with-AI flow")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'MEMBER')")
public class VenueEnrichmentAssistRestResource {

  private final VenueEnrichmentAssistantService enrichmentAssistantService;

  public VenueEnrichmentAssistRestResource(final VenueEnrichmentAssistantService enrichmentAssistantService) {
    this.enrichmentAssistantService = enrichmentAssistantService;
  }

  @PostMapping("/suggestions")
  @Operation(
      summary = "Get AI enrichment suggestions for a venue profile",
      description = "Sends the tenant's free-text message to the AI assistant and returns a "
                    + "conversational reply plus a list of structured field suggestions that map "
                    + "directly onto the venue's metadata.items schema. "
                    + "The optional fieldHint scopes the reply to a specific metadata group "
                    + "(e.g. 'capacity', 'amenities'). "
                    + "The optional currentMetadata carries the venue's current serialised metadata "
                    + "document so the assistant avoids re-suggesting already-filled fields. "
                    + "When the AI model is unavailable the endpoint still returns 200 with a "
                    + "fallback reply and an empty suggestedPatches list. "
                    + "Responds 404 when the venue does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Enrichment suggestions produced",
                   content = @Content(schema = @Schema(implementation = VenueDtos.VenueEnrichmentSuggestionResponse.class))),
      @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueDtos.VenueEnrichmentSuggestionResponse> suggest(
      @Parameter(description = "Owning venue UUID") @PathVariable final UUID venueId,
      @Valid @RequestBody final VenueDtos.VenueEnrichmentSuggestionRequest request) {
    return ResponseEntity.ok(enrichmentAssistantService.suggest(venueId, request));
  }
}
