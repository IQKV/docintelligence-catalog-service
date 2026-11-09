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

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-facing venue surface — search and read only.
 *
 * <p>Writes live exclusively on {@link AdminVenueRestResource}. Every response here uses the safe
 * projection: curation fields such as {@code confidence}, {@code source} and the raw
 * {@code metadata} document are not exposed; the optional {@code masterVenueId} relation key is
 * kept so a member can see whether a venue is linked to a master catalog entry.
 *
 * <p>The tenant context is resolved for these paths by {@code TenantExtractionFilter} from the
 * {@code X-Tenant-ID} header or the JWT {@code tenant_id} claim, so the
 * {@code MyBatisSchemaInterceptor} routes queries to the member's own {@code t_{tenantKey}}
 * schema.
 */
@RestController
@RequestMapping("/api/v1/catalog/venues")
@Tag(name = "Venues", description = "Read-only access to the tenant's own venue records")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('MEMBER')")
public class VenueRestResource {

  private final VenueService venueService;

  public VenueRestResource(final VenueService venueService) {
    this.venueService = venueService;
  }

  @GetMapping
  @Operation(
      summary = "Search tenant venues",
      description = "Keyword search over name, address and city, with an optional exact-city "
                    + "filter and offset pagination. Supports optional sort by name (asc/desc); "
                    + "omitting sortBy returns venues ordered by most recently added. "
                    + "Returns the safe projection only.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Matching tenant venues",
                   content = @Content(schema = @Schema(implementation = VenueDtos.VenueListResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — MEMBER authority required", content = @Content)
  })
  public ResponseEntity<VenueDtos.VenueListResponse> search(
      @Parameter(description = "Free-text keyword matched against name, address and city")
      @RequestParam(required = false) final String search,
      @Parameter(description = "Exact city filter, compared case-insensitively")
      @RequestParam(required = false) final String city,
      @Parameter(description = "Sort field: 'name' or omit for recently added (default)")
      @RequestParam(required = false) final String sortBy,
      @Parameter(description = "Sort direction: 'asc' (default when sortBy is set) or 'desc'")
      @RequestParam(required = false) final String sortDir,
      @Parameter(description = "Maximum number of venues to return")
      @RequestParam(defaultValue = "20") final int limit,
      @Parameter(description = "Zero-based row offset")
      @RequestParam(defaultValue = "0") final int offset) {
    return ResponseEntity.ok(venueService.searchPublicVenues(search, city, sortBy, sortDir, limit, offset));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get a tenant venue",
      description = "Returns the safe projection of the given tenant venue. "
                    + "Responds 404 when the venue does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Venue found",
                   content = @Content(schema = @Schema(implementation = VenueDtos.VenuePublicResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueDtos.VenuePublicResponse> getById(
      @Parameter(description = "Venue UUID") @PathVariable final UUID id) {
    return ResponseEntity.ok(venueService.getPublicVenue(id));
  }
}
