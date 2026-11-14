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
 * <p>Applying an extracted value is done by the tenant UI through
 * {@code PATCH /api/v1/catalog/venues/{id}/metadata} — this endpoint is read-only.
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

  private final VenueEnrichmentAssistantService enrichmentAssistantService;

  public VenueEnrichmentAssistRestResource(final VenueEnrichmentAssistantService enrichmentAssistantService) {
    this.enrichmentAssistantService = enrichmentAssistantService;
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
}
