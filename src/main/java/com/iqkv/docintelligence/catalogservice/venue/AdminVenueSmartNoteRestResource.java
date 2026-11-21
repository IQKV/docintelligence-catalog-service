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
import com.iqkv.foundation.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * Platform-admin REST surface for venue smart notes
 * ({@code /api/v1/catalog/admin/{tenantKey}/venue-smart-notes}).
 *
 * <p>All endpoints require {@code PLATFORM_ADMIN} authority. The active tenant schema is set from
 * the {@code {tenantKey}} path variable in a try/finally block so it is always cleared. The note
 * author is recorded from the admin's JWT {@code user_id} claim at create time.
 */
@RestController
@RequestMapping("/api/v1/catalog/admin/{tenantKey}/venue-smart-notes")
@Tag(name = "Admin Venue Smart Notes", description = "Platform-admin CRUD over tenant venue smart-note rows")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
public class AdminVenueSmartNoteRestResource {

  private final VenueSmartNoteService service;

  public AdminVenueSmartNoteRestResource(final VenueSmartNoteService service) {
    this.service = service;
  }

  @GetMapping("/venue/{venueId}")
  @Operation(
      summary = "List smart notes for a venue (platform admin)",
      description = "Returns all smart notes attached to the given venue, ordered by sort_order "
                    + "then created_at in the requested direction. Optionally filtered to pinned.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Smart notes for the venue",
                   content = @Content(schema = @Schema(implementation = VenueSmartNoteDtos.VenueSmartNoteListResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueSmartNoteDtos.VenueSmartNoteListResponse> listByVenue(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Venue UUID") @PathVariable final UUID venueId,
      @Parameter(description = "Sort direction for created_at — ASC (default) or DESC")
      @RequestParam(required = false) final String direction,
      @Parameter(description = "Filter: true for pinned only, false for unpinned only, omit for all")
      @RequestParam(required = false) final Boolean pinned) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(service.listByVenue(venueId, direction, pinned));
    } finally {
      TenantContext.clear();
    }
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get a smart note (platform admin)",
      description = "Returns the given note. Responds 404 when it does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Note found",
                   content = @Content(schema = @Schema(implementation = VenueSmartNoteDtos.VenueSmartNoteResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Note not found", content = @Content)
  })
  public ResponseEntity<VenueSmartNoteDtos.VenueSmartNoteResponse> getById(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Note UUID") @PathVariable final UUID id) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(service.getById(id));
    } finally {
      TenantContext.clear();
    }
  }

  @GetMapping("/{id}/asset")
  @Operation(
      summary = "Load the asset linked to a smart note (platform admin)",
      description = "Returns the venue asset this note is linked to. Responds 404 when the note "
                    + "does not exist or has no linked asset.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Linked asset found",
                   content = @Content(schema = @Schema(implementation = VenueAssetDtos.VenueAssetResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Note not found or no linked asset", content = @Content)
  })
  public ResponseEntity<VenueAssetDtos.VenueAssetResponse> getLinkedAsset(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Note UUID") @PathVariable final UUID id) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(service.getLinkedAsset(id));
    } finally {
      TenantContext.clear();
    }
  }

  @PostMapping
  @Operation(
      summary = "Create a smart note (platform admin)",
      description = "Stores a new note. The author is recorded from the admin's JWT user_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Note created",
                   content = @Content(schema = @Schema(implementation = VenueSmartNoteDtos.VenueSmartNoteResponse.class))),
      @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueSmartNoteDtos.VenueSmartNoteResponse> create(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @AuthenticationPrincipal final Jwt jwt,
      @Valid @RequestBody final VenueSmartNoteDtos.VenueSmartNoteCreateRequest request) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      final UUID authorId = UUID.fromString(jwt.getClaimAsString(JwtClaimNames.USER_ID));
      return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, authorId));
    } finally {
      TenantContext.clear();
    }
  }

  @PatchMapping("/{id}")
  @Operation(
      summary = "Partially update a smart note (platform admin)",
      description = "Updates the given note. Only non-null fields are overwritten. "
                    + "Responds 404 when the note does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Note updated",
                   content = @Content(schema = @Schema(implementation = VenueSmartNoteDtos.VenueSmartNoteResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Note not found", content = @Content)
  })
  public ResponseEntity<VenueSmartNoteDtos.VenueSmartNoteResponse> patch(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Note UUID") @PathVariable final UUID id,
      @AuthenticationPrincipal final Jwt jwt,
      @RequestBody final VenueSmartNoteDtos.VenueSmartNotePatchRequest request) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      final UUID actorId = UUID.fromString(jwt.getClaimAsString(JwtClaimNames.USER_ID));
      return ResponseEntity.ok(service.patch(id, request, actorId, true));
    } finally {
      TenantContext.clear();
    }
  }

  @PutMapping("/{id}/pinned")
  @Operation(
      summary = "Pin or unpin a smart note (platform admin)",
      description = "Sets the pin flag on one note in a single call. Responds 404 when the note "
                    + "does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Note updated",
                   content = @Content(schema = @Schema(implementation = VenueSmartNoteDtos.VenueSmartNoteResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Note not found", content = @Content)
  })
  public ResponseEntity<VenueSmartNoteDtos.VenueSmartNoteResponse> setPinned(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Note UUID") @PathVariable final UUID id,
      @AuthenticationPrincipal final Jwt jwt,
      @Valid @RequestBody final VenueSmartNoteDtos.VenueSmartNoteSetPinnedRequest request) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      final UUID actorId = UUID.fromString(jwt.getClaimAsString(JwtClaimNames.USER_ID));
      return ResponseEntity.ok(service.setPinned(id, request.pinned(), actorId, true));
    } finally {
      TenantContext.clear();
    }
  }

  @DeleteMapping("/{id}")
  @Operation(
      summary = "Delete a smart note (platform admin)",
      description = "Permanently deletes the note. Responds 404 when it does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Note deleted", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Note not found", content = @Content)
  })
  public ResponseEntity<Void> delete(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Note UUID") @PathVariable final UUID id) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      service.delete(id);
      return ResponseEntity.noContent().build();
    } finally {
      TenantContext.clear();
    }
  }
}
