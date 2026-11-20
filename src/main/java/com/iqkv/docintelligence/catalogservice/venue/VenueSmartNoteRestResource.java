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

import com.iqkv.docintelligence.catalogservice.infrastructure.security.JwtClaimNames;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueAssetDtos;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueSmartNoteDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
 * Tenant-facing REST surface for venue smart notes
 * ({@code /api/v1/catalog/venue-smart-notes}).
 *
 * <p>Reads and creates are open to {@code MEMBER}, {@code TENANT_OWNER} and {@code ADMIN} — any
 * tenant member may author a note. Edits and deletes require {@code TENANT_OWNER} or
 * {@code ADMIN}. The note author is recorded from the JWT {@code user_id} claim at create time
 * and is never taken from the request body. Tenant context is resolved from the
 * {@code X-Tenant-ID} header or the JWT {@code tenant_id} claim by
 * {@code TenantExtractionFilter}.
 */
@RestController
@RequestMapping("/api/v1/catalog/venue-smart-notes")
@Tag(name = "Venue Smart Notes", description = "User-authored sticky notes attached to a venue")
@SecurityRequirement(name = "bearerAuth")
public class VenueSmartNoteRestResource {

  private final VenueSmartNoteService service;

  public VenueSmartNoteRestResource(final VenueSmartNoteService service) {
    this.service = service;
  }

  // ─── Reads ────────────────────────────────────────────────────────────────

  @GetMapping("/venue/{venueId}")
  @PreAuthorize("hasAnyAuthority('MEMBER', 'TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "List smart notes for a venue",
      description = "Returns all smart notes attached to the given venue, ordered by sort_order "
                    + "ascending then created_at in the requested direction. Optionally filtered "
                    + "to pinned or unpinned notes.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Smart notes for the venue",
                   content = @Content(schema = @Schema(implementation = VenueSmartNoteDtos.VenueSmartNoteListResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied", content = @Content)
  })
  public ResponseEntity<VenueSmartNoteDtos.VenueSmartNoteListResponse> listByVenue(
      @Parameter(description = "Venue UUID") @PathVariable final UUID venueId,
      @Parameter(description = "Sort direction for created_at — ASC (default) or DESC")
      @RequestParam(required = false) final String direction,
      @Parameter(description = "Filter: true for pinned only, false for unpinned only, omit for all")
      @RequestParam(required = false) final Boolean pinned) {
    return ResponseEntity.ok(service.listByVenue(venueId, direction, pinned));
  }

  @GetMapping("/venue/{venueId}/count")
  @PreAuthorize("hasAnyAuthority('MEMBER', 'TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Count smart notes for a venue",
      description = "Returns the number of smart notes attached to the given venue using a "
                    + "database-level COUNT query.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Smart-note count for the venue",
                   content = @Content(schema = @Schema(implementation = VenueSmartNoteDtos.VenueSmartNoteCountResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied", content = @Content)
  })
  public ResponseEntity<VenueSmartNoteDtos.VenueSmartNoteCountResponse> countByVenue(
      @Parameter(description = "Venue UUID") @PathVariable final UUID venueId) {
    return ResponseEntity.ok(service.countByVenue(venueId));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('MEMBER', 'TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Get a smart note",
      description = "Returns the given note. Responds 404 when it does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Note found",
                   content = @Content(schema = @Schema(implementation = VenueSmartNoteDtos.VenueSmartNoteResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied", content = @Content),
      @ApiResponse(responseCode = "404", description = "Note not found", content = @Content)
  })
  public ResponseEntity<VenueSmartNoteDtos.VenueSmartNoteResponse> getById(
      @Parameter(description = "Note UUID") @PathVariable final UUID id) {
    return ResponseEntity.ok(service.getById(id));
  }

  @GetMapping("/{id}/asset")
  @PreAuthorize("hasAnyAuthority('MEMBER', 'TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Load the asset linked to a smart note",
      description = "Returns the venue asset this note is linked to, so the UI can render it "
                    + "inline. Responds 404 when the note does not exist or has no linked asset.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Linked asset found",
                   content = @Content(schema = @Schema(implementation = VenueAssetDtos.VenueAssetResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied", content = @Content),
      @ApiResponse(responseCode = "404", description = "Note not found or no linked asset", content = @Content)
  })
  public ResponseEntity<VenueAssetDtos.VenueAssetResponse> getLinkedAsset(
      @Parameter(description = "Note UUID") @PathVariable final UUID id) {
    return ResponseEntity.ok(service.getLinkedAsset(id));
  }

  // ─── Writes ───────────────────────────────────────────────────────────────

  @PostMapping
  @PreAuthorize("hasAnyAuthority('MEMBER', 'TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Create a smart note",
      description = "Stores a new note. The author is recorded from the JWT user_id claim, not "
                    + "the request body.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Note created",
                   content = @Content(schema = @Schema(implementation = VenueSmartNoteDtos.VenueSmartNoteResponse.class))),
      @ApiResponse(responseCode = "400", description = "Request validation failed or tenant ID unresolvable", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied", content = @Content)
  })
  public ResponseEntity<VenueSmartNoteDtos.VenueSmartNoteResponse> create(
      @AuthenticationPrincipal final Jwt jwt,
      @Valid @RequestBody final VenueSmartNoteDtos.VenueSmartNoteCreateRequest request) {
    final UUID authorId = UUID.fromString(jwt.getClaimAsString(JwtClaimNames.USER_ID));
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, authorId));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Partially update a smart note",
      description = "Updates the given note. Only non-null fields are overwritten. "
                    + "Requires TENANT_OWNER or ADMIN authority. Responds 404 when the note "
                    + "does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Note updated",
                   content = @Content(schema = @Schema(implementation = VenueSmartNoteDtos.VenueSmartNoteResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Note not found", content = @Content)
  })
  public ResponseEntity<VenueSmartNoteDtos.VenueSmartNoteResponse> patch(
      @Parameter(description = "Note UUID") @PathVariable final UUID id,
      @RequestBody final VenueSmartNoteDtos.VenueSmartNotePatchRequest request) {
    return ResponseEntity.ok(service.patch(id, request));
  }

  @PutMapping("/{id}/pinned")
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Pin or unpin a smart note",
      description = "Sets the pin flag on one note in a single call. Requires TENANT_OWNER or "
                    + "ADMIN authority. Responds 404 when the note does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Note updated",
                   content = @Content(schema = @Schema(implementation = VenueSmartNoteDtos.VenueSmartNoteResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Note not found", content = @Content)
  })
  public ResponseEntity<VenueSmartNoteDtos.VenueSmartNoteResponse> setPinned(
      @Parameter(description = "Note UUID") @PathVariable final UUID id,
      @Valid @RequestBody final VenueSmartNoteDtos.VenueSmartNoteSetPinnedRequest request) {
    return ResponseEntity.ok(service.setPinned(id, request.pinned()));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Delete a smart note",
      description = "Permanently deletes the note. Requires TENANT_OWNER or ADMIN authority. "
                    + "Responds 404 when it does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Note deleted", content = @Content),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Note not found", content = @Content)
  })
  public ResponseEntity<Void> delete(
      @Parameter(description = "Note UUID") @PathVariable final UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
