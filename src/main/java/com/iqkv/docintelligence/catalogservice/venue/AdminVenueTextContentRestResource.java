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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueTextContentDtos;
import com.iqkv.foundation.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-admin REST surface for venue text-content rows
 * ({@code /api/v1/catalog/admin/{tenantKey}/venue-text-contents}).
 *
 * <p>All endpoints require {@code PLATFORM_ADMIN} authority. The active tenant schema is set from
 * the {@code {tenantKey}} path variable in a try/finally block so it is always cleared.
 */
@RestController
@RequestMapping("/api/v1/catalog/admin/{tenantKey}/venue-text-contents")
@Tag(name = "Admin Venue Text Contents", description = "Platform-admin CRUD over tenant venue text-content rows")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
public class AdminVenueTextContentRestResource {

  private final VenueTextContentService service;

  public AdminVenueTextContentRestResource(final VenueTextContentService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Search venue text contents (platform admin)",
      description = "Keyword search over content, optionally filtered by venue, with offset pagination.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Matching rows",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentListResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentListResponse> search(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Filter by venue UUID") @RequestParam(required = false) final UUID venueId,
      @Parameter(description = "Free-text keyword matched against content") @RequestParam(required = false) final String search,
      @Parameter(description = "Maximum number of rows to return") @RequestParam(defaultValue = "20") final int limit,
      @Parameter(description = "Zero-based row offset") @RequestParam(defaultValue = "0") final int offset) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(service.search(venueId, search, limit, offset));
    } finally {
      TenantContext.clear();
    }
  }

  @GetMapping("/venue/{venueId}")
  @Operation(
      summary = "List text contents for a venue (platform admin)",
      description = "Returns all text-content rows linked to the given venue, oldest first.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Text contents for the venue",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentListResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentListResponse> listByVenue(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Venue UUID") @PathVariable final UUID venueId) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(service.listByVenue(venueId));
    } finally {
      TenantContext.clear();
    }
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get a venue text content (platform admin)",
      description = "Returns the given row. Responds 404 when it does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Row found",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Row not found", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentResponse> getById(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Row UUID") @PathVariable final UUID id) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(service.getById(id));
    } finally {
      TenantContext.clear();
    }
  }

  @GetMapping("/search/semantic")
  @Operation(
      summary = "Semantic search over venue text contents (platform admin)",
      description = "Embeds the query and ranks rows by cosine similarity. Only embedded rows are considered.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Ranked rows",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentSemanticSearchResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentSemanticSearchResponse> semanticSearch(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Natural-language search query") @RequestParam final String q,
      @Parameter(description = "Optional venue filter") @RequestParam(required = false) final UUID venueId,
      @Parameter(description = "Maximum number of rows to return (default 20, max 100)") @RequestParam(defaultValue = "20") final int limit) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(service.semanticSearch(q, venueId, Math.min(limit, 100)));
    } finally {
      TenantContext.clear();
    }
  }

  @PostMapping
  @Operation(
      summary = "Create a venue text content (platform admin)",
      description = "Stores a raw or markdown text chunk. venueId is optional.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Row created",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentResponse.class))),
      @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentResponse> create(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Valid @RequestBody final VenueTextContentDtos.VenueTextContentWriteRequest request) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    } finally {
      TenantContext.clear();
    }
  }

  @PatchMapping("/{id}")
  @Operation(
      summary = "Partially update a venue text content (platform admin)",
      description = "Updates the content of the given row. Responds 404 when it does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Row updated",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Row not found", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentResponse> patch(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Row UUID") @PathVariable final UUID id,
      @RequestBody final VenueTextContentDtos.VenueTextContentPatchRequest request) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(service.patch(id, request));
    } finally {
      TenantContext.clear();
    }
  }

  @PostMapping("/{id}/link")
  @Operation(
      summary = "Link a venue-less text content to a venue (platform admin)",
      description = "Sets the venueId on a row that was created before the venue was derived. "
                    + "Responds 404 when the row does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Row linked",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentResponse.class))),
      @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Row not found", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentResponse> link(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Row UUID") @PathVariable final UUID id,
      @Valid @RequestBody final VenueTextContentDtos.VenueTextContentLinkRequest request) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(service.linkVenue(id, request.venueId()));
    } finally {
      TenantContext.clear();
    }
  }

  @DeleteMapping("/{id}")
  @Operation(
      summary = "Delete a venue text content (platform admin)",
      description = "Permanently deletes the row. Responds 404 when it does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Row deleted", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Row not found", content = @Content)
  })
  public ResponseEntity<Void> delete(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Row UUID") @PathVariable final UUID id) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      service.delete(id);
      return ResponseEntity.noContent().build();
    } finally {
      TenantContext.clear();
    }
  }
}
