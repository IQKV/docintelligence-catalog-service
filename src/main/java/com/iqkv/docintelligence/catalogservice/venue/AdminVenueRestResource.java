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
import com.iqkv.foundation.tenancy.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-admin CRUD over tenant venue records.
 *
 * <p>Every request is scoped to the tenant named in the {@code {tenantKey}} path variable — the
 * controller sets {@code TenantContext} from it so the {@code MyBatisSchemaInterceptor} routes
 * queries to {@code t_{tenantKey}}. The path is under {@code /admin/}, so
 * {@code TenantExtractionFilter} does not run and {@code SecurityConfig} enforces the
 * {@code PLATFORM_ADMIN} authority.
 */
@RestController
@RequestMapping("/api/v1/catalog/admin/{tenantKey}/venues")
@Tag(name = "Admin Venues", description = "Platform-admin CRUD over tenant venue records")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
public class AdminVenueRestResource {

  private final VenueService venueService;

  public AdminVenueRestResource(final VenueService venueService) {
    this.venueService = venueService;
  }

  @GetMapping
  @Operation(
      summary = "Search tenant venues (platform admin)",
      description = "Keyword search over name, address and city, with an optional exact-city filter "
                    + "and offset pagination. Supports optional sort by name (asc/desc); omitting "
                    + "sortBy returns venues ordered by most recently added.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Matching tenant venues",
                   content = @Content(schema = @Schema(implementation = VenueDtos.VenueListResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueDtos.VenueListResponse> search(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
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
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(venueService.searchVenues(search, city, sortBy, sortDir, limit, offset));
    } finally {
      TenantContext.clear();
    }
  }

  @GetMapping("/search/semantic")
  @Operation(
      summary = "Semantic search over tenant venues (platform admin)",
      description = "The query is first analysed by the AI query planner, which extracts hard "
                    + "filters (city, venue type, capacity, catering policy, amenities, "
                    + "accessibility) and a residual free-text query. The filters narrow the "
                    + "candidate set as JSONB predicates and only the residual text is embedded, "
                    + "then the top matches are returned by cosine similarity using the per-tenant "
                    + "pgvector HNSW index. Only venues that have already been embedded are "
                    + "considered. When the planner is disabled or the chat model is unreachable "
                    + "the query is embedded unchanged and no hard filters are applied; the "
                    + "filters that were applied are echoed in 'appliedFilters'. Responds 200 with "
                    + "an empty list when no embeddings exist yet.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Semantic search results",
                   content = @Content(schema = @Schema(implementation = VenueDtos.VenueSemanticSearchResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueDtos.VenueSemanticSearchResponse> semanticSearch(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Natural-language search query", required = true)
      @RequestParam final String q,
      @Parameter(description = "Optional exact city filter, compared case-insensitively; "
                               + "overrides any city the AI planner extracts")
      @RequestParam(required = false) final String city,
      @Parameter(description = "Maximum number of results to return (default 20, max 100)")
      @RequestParam(defaultValue = "20") final int limit) {
    final int effectiveLimit = Math.min(limit, 100);
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(venueService.semanticSearch(q, city, effectiveLimit));
    } finally {
      TenantContext.clear();
    }
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get a tenant venue (platform admin)",
      description = "Returns the given tenant venue. Responds 404 when the venue does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Venue found",
                   content = @Content(schema = @Schema(implementation = VenueDtos.VenueResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueDtos.VenueResponse> getById(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Venue UUID") @PathVariable final UUID id) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(venueService.getVenue(id));
    } finally {
      TenantContext.clear();
    }
  }

  @PostMapping
  @Operation(
      summary = "Create a tenant venue (platform admin)",
      description = "Creates a tenant venue. Omitted metadata falls back to the canonical empty "
                    + "document and omitted confidence to 1.0. masterVenueId links the venue to "
                    + "a master record when provided.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Venue created",
                   content = @Content(schema = @Schema(implementation = VenueDtos.VenueResponse.class))),
      @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueDtos.VenueResponse> create(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Valid @RequestBody final VenueDtos.VenueWriteRequest request) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.status(HttpStatus.CREATED).body(venueService.createVenue(request));
    } finally {
      TenantContext.clear();
    }
  }

  @PutMapping("/{id}")
  @Operation(
      summary = "Replace a tenant venue (platform admin)",
      description = "Full replace — every writable field is overwritten, including metadata and "
                    + "confidence. Responds 404 when the venue does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Venue replaced",
                   content = @Content(schema = @Schema(implementation = VenueDtos.VenueResponse.class))),
      @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueDtos.VenueResponse> replace(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Venue UUID") @PathVariable final UUID id,
      @Valid @RequestBody final VenueDtos.VenueWriteRequest request) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(venueService.replaceVenue(id, request));
    } finally {
      TenantContext.clear();
    }
  }

  @PatchMapping("/{id}")
  @Operation(
      summary = "Partially update a tenant venue (platform admin)",
      description = "Only the fields present in the request are overwritten. Responds 404 when "
                    + "the venue does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Venue updated",
                   content = @Content(schema = @Schema(implementation = VenueDtos.VenueResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueDtos.VenueResponse> patch(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Venue UUID") @PathVariable final UUID id,
      @Valid @RequestBody final VenueDtos.VenuePatchRequest request) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(venueService.patchVenue(id, request));
    } finally {
      TenantContext.clear();
    }
  }

  @DeleteMapping("/{id}")
  @Operation(
      summary = "Delete a tenant venue (platform admin)",
      description = "Permanently deletes the venue. Responds 404 when the venue does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Venue deleted", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<Void> delete(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Venue UUID") @PathVariable final UUID id) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      venueService.deleteVenue(id);
      return ResponseEntity.noContent().build();
    } finally {
      TenantContext.clear();
    }
  }
}
