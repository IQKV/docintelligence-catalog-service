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
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueTextContentDtos;
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
 * Tenant-facing REST surface for venue text-content rows
 * ({@code /api/v1/catalog/venue-text-contents}).
 *
 * <p>Reads are open to {@code MEMBER}, {@code TENANT_OWNER} and {@code ADMIN}. Writes require
 * {@code TENANT_OWNER} or {@code ADMIN}. Tenant context is resolved from the {@code X-Tenant-ID}
 * header or the JWT {@code tenant_id} claim by {@code TenantExtractionFilter}.
 */
@RestController
@RequestMapping("/api/v1/catalog/venue-text-contents")
@Tag(name = "Venue Text Contents", description = "Raw or markdown text chunks extracted from venue source documents")
@SecurityRequirement(name = "bearerAuth")
public class VenueTextContentRestResource {

  private final VenueTextContentService service;

  public VenueTextContentRestResource(final VenueTextContentService service) {
    this.service = service;
  }

  // ─── Reads ────────────────────────────────────────────────────────────────

  @GetMapping
  @PreAuthorize("hasAnyAuthority('MEMBER', 'TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Search venue text contents",
      description = "Keyword search over content, optionally filtered by venue, with offset pagination. "
                    + "Returns rows ordered by most recently created.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Matching rows",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentListResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentListResponse> search(
      @Parameter(description = "Filter by venue UUID") @RequestParam(required = false) final UUID venueId,
      @Parameter(description = "Free-text keyword matched against content") @RequestParam(required = false) final String search,
      @Parameter(description = "Maximum number of rows to return") @RequestParam(defaultValue = "20") final int limit,
      @Parameter(description = "Zero-based row offset") @RequestParam(defaultValue = "0") final int offset) {
    return ResponseEntity.ok(service.search(venueId, search, limit, offset));
  }

  @GetMapping("/venue/{venueId}")
  @PreAuthorize("hasAnyAuthority('MEMBER', 'TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "List text contents for a venue",
      description = "Returns all text-content rows linked to the given venue, ordered by creation date ascending.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Text contents for the venue",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentListResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentListResponse> listByVenue(
      @Parameter(description = "Venue UUID") @PathVariable final UUID venueId) {
    return ResponseEntity.ok(service.listByVenue(venueId));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('MEMBER', 'TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Get a venue text content",
      description = "Returns the given row. Responds 404 when it does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Row found",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied", content = @Content),
      @ApiResponse(responseCode = "404", description = "Row not found", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentResponse> getById(
      @Parameter(description = "Row UUID") @PathVariable final UUID id) {
    return ResponseEntity.ok(service.getById(id));
  }

  @GetMapping("/search/semantic")
  @PreAuthorize("hasAnyAuthority('MEMBER', 'TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Semantic search over venue text contents",
      description = "Embeds the query and ranks text-content rows by cosine similarity. "
                    + "Only rows that have an embedding are considered. "
                    + "Optionally filtered to one venue.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Ranked rows",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentSemanticSearchResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentSemanticSearchResponse> semanticSearch(
      @Parameter(description = "Natural-language search query") @RequestParam final String q,
      @Parameter(description = "Optional venue filter") @RequestParam(required = false) final UUID venueId,
      @Parameter(description = "Maximum number of rows to return (capped at 100)") @RequestParam(defaultValue = "20") final int limit) {
    return ResponseEntity.ok(service.semanticSearch(q, venueId, Math.min(limit, 100)));
  }

  // ─── Writes ───────────────────────────────────────────────────────────────

  @PostMapping
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Create a venue text content",
      description = "Stores a raw or markdown text chunk. {@code venueId} is optional — the row "
                    + "may be created before the venue is derived and linked later via PATCH /{id}/link.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Row created",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentResponse.class))),
      @ApiResponse(responseCode = "400", description = "Request validation failed or tenant ID unresolvable", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentResponse> create(
      @Valid @RequestBody final VenueTextContentDtos.VenueTextContentWriteRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Partially update a venue text content",
      description = "Updates the content of the given row. Only non-null fields are overwritten. "
                    + "Responds 404 when the row does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Row updated",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Row not found", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentResponse> patch(
      @Parameter(description = "Row UUID") @PathVariable final UUID id,
      @RequestBody final VenueTextContentDtos.VenueTextContentPatchRequest request) {
    return ResponseEntity.ok(service.patch(id, request));
  }

  @PostMapping("/{id}/link")
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Link a venue-less text content to a venue",
      description = "Sets the venueId on a row that was created before the venue was derived. "
                    + "Responds 404 when the row does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Row linked",
                   content = @Content(schema = @Schema(implementation = VenueTextContentDtos.VenueTextContentResponse.class))),
      @ApiResponse(responseCode = "400", description = "Request validation failed or tenant ID unresolvable", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Row not found", content = @Content)
  })
  public ResponseEntity<VenueTextContentDtos.VenueTextContentResponse> link(
      @Parameter(description = "Row UUID") @PathVariable final UUID id,
      @Valid @RequestBody final VenueTextContentDtos.VenueTextContentLinkRequest request) {
    return ResponseEntity.ok(service.linkVenue(id, request.venueId()));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Delete a venue text content",
      description = "Permanently deletes the row. Responds 404 when it does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Row deleted", content = @Content),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Row not found", content = @Content)
  })
  public ResponseEntity<Void> delete(
      @Parameter(description = "Row UUID") @PathVariable final UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
