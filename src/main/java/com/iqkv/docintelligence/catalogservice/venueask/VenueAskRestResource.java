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

import jakarta.validation.Valid;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venueask.dto.VenueAskDtos;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retrieval-augmented question answering over the tenant's own venue data.
 *
 * <p>Two scopes are offered: a single venue (grounded in that venue's profile, source chunks and
 * assets) and the ranked list / grid of venues. Every answer is returned together with the sources
 * it was grounded in, so the UI can show citations and act on them.
 *
 * <p>The tenant context is resolved by {@code TenantExtractionFilter} from the {@code X-Tenant-ID}
 * header or the JWT {@code tenant_id} claim, so all retrieval is scoped to the member's own
 * {@code t_{tenantKey}} schema. When the AI feature is off or the model is unreachable the surface
 * degrades to {@code answered=false} rather than failing.
 */
@RestController
@RequestMapping("/api/v1/catalog/venues")
@Tag(name = "Venue Ask", description = "Retrieval-augmented question answering over the tenant's venues")
@SecurityRequirement(name = "bearerAuth")
public class VenueAskRestResource {

  private final VenueAskService venueAskService;

  public VenueAskRestResource(final VenueAskService venueAskService) {
    this.venueAskService = venueAskService;
  }

  @PostMapping("/{venueId}/ask")
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'MEMBER')")
  @Operation(
      summary = "Ask a question about one venue",
      description = "Answers a natural-language question grounded in the given venue's profile, "
                    + "source text chunks and assets. Returns the answer together with the source "
                    + "chunks it cited. Degrades to answered=false (with any sources found) when the "
                    + "AI feature is off or the model is unreachable.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Grounded answer with its sources",
                   content = @Content(schema = @Schema(implementation = VenueAskDtos.VenueAskResponse.class))),
      @ApiResponse(responseCode = "400", description = "Empty question, rejected by the prompt-injection guard, or tenant ID unresolvable", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueAskDtos.VenueAskResponse> askVenue(
      @Parameter(description = "Venue UUID") @PathVariable final UUID venueId,
      @Valid @RequestBody final VenueAskDtos.VenueAskRequest request) {
    return ResponseEntity.ok(venueAskService.askVenue(venueId, request));
  }

  @GetMapping("/{venueId}/context")
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'MEMBER')")
  @Operation(
      summary = "Get one venue's retrieved context",
      description = "Returns the venue profile, its source text chunks and its assets — the same "
                    + "retrieval the ask endpoint grounds on, without any model call, so the UI can "
                    + "preload and inspect it.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The venue's retrieved context",
                   content = @Content(schema = @Schema(implementation = VenueAskDtos.VenueContextResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueAskDtos.VenueContextResponse> getVenueContext(
      @Parameter(description = "Venue UUID") @PathVariable final UUID venueId) {
    return ResponseEntity.ok(venueAskService.getVenueContext(venueId));
  }

  @PostMapping("/ask")
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'MEMBER')")
  @Operation(
      summary = "Ask a question about the list of venues",
      description = "Answers a natural-language question grounded in the ranked list / grid of the "
                    + "tenant's venues. The query is planned into hard filters plus a residual "
                    + "vector query, and the answer is returned with the ranked venues and the "
                    + "filters that were applied. Degrades to answered=false when the AI feature is "
                    + "off or the model is unreachable.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Grounded answer with the ranked venues",
                   content = @Content(schema = @Schema(implementation = VenueAskDtos.VenueListAskResponse.class))),
      @ApiResponse(responseCode = "400", description = "Empty question, rejected by the prompt-injection guard, or tenant ID unresolvable", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content)
  })
  public ResponseEntity<VenueAskDtos.VenueListAskResponse> askVenues(
      @Valid @RequestBody final VenueAskDtos.VenueListAskRequest request) {
    return ResponseEntity.ok(venueAskService.askVenues(request));
  }
}
