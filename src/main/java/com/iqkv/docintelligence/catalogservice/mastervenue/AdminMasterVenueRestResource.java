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

package com.iqkv.docintelligence.catalogservice.mastervenue;

import jakarta.validation.Valid;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.mastervenue.dto.MasterVenueDtos;
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
 * Platform-admin master venue surface — full CRUD plus search.
 *
 * <p>Restricted to the {@code PLATFORM_ADMIN} authority. The path sits under
 * {@code /api/v1/catalog/admin/**}, so {@code SecurityConfig} enforces the authority and
 * {@code TenantExtractionFilter} never requires a tenant context here: master venues are
 * platform-owned reference data in the {@code public} schema, not tenant-scoped rows.
 */
@RestController
@RequestMapping("/api/v1/catalog/admin/master-venues")
@Tag(name = "Admin Master Venues", description = "Platform-admin curation of the master venue catalog")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
public class AdminMasterVenueRestResource {

  private final MasterVenueService masterVenueService;

  public AdminMasterVenueRestResource(final MasterVenueService masterVenueService) {
    this.masterVenueService = masterVenueService;
  }

  @GetMapping
  @Operation(
      summary = "Search master venues (platform admin)",
      description = "Keyword search over name, address and city, with an optional exact-city filter "
                    + "and offset pagination. Returns the full record, including curation fields.")
  public ResponseEntity<MasterVenueDtos.AdminMasterVenueListResponse> search(
      @Parameter(description = "Free-text keyword matched against name, address and city")
      @RequestParam(required = false) final String search,
      @Parameter(description = "Exact city filter, compared case-insensitively")
      @RequestParam(required = false) final String city,
      @Parameter(description = "Maximum number of venues to return")
      @RequestParam(defaultValue = "20") final int limit,
      @Parameter(description = "Zero-based row offset")
      @RequestParam(defaultValue = "0") final int offset) {
    return ResponseEntity.ok(masterVenueService.searchVenues(search, city, limit, offset));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get a master venue (platform admin)",
      description = "Returns the full record, including confidence, source audit and raw metadata. "
                    + "Responds 404 when the venue does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Master venue found",
                   content = @Content(schema = @Schema(implementation = MasterVenueDtos.MasterVenueResponse.class))),
      @ApiResponse(responseCode = "404", description = "Master venue not found", content = @Content)
  })
  public ResponseEntity<MasterVenueDtos.MasterVenueResponse> getById(
      @Parameter(description = "Master venue UUID") @PathVariable final UUID id) {
    return ResponseEntity.ok(masterVenueService.getVenue(id));
  }

  @PostMapping
  @Operation(
      summary = "Create a master venue",
      description = "Creates a master venue. Omitted metadata falls back to the canonical empty "
                    + "document and omitted confidence to 1.0.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Master venue created",
                   content = @Content(schema = @Schema(implementation = MasterVenueDtos.MasterVenueResponse.class))),
      @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content)
  })
  public ResponseEntity<MasterVenueDtos.MasterVenueResponse> create(
      @Valid @RequestBody final MasterVenueDtos.MasterVenueWriteRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(masterVenueService.createVenue(request));
  }

  @PutMapping("/{id}")
  @Operation(
      summary = "Replace a master venue",
      description = "Full replace — every writable field is overwritten, including metadata and "
                    + "confidence scores. Responds 404 when the venue does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Master venue replaced",
                   content = @Content(schema = @Schema(implementation = MasterVenueDtos.MasterVenueResponse.class))),
      @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content),
      @ApiResponse(responseCode = "404", description = "Master venue not found", content = @Content)
  })
  public ResponseEntity<MasterVenueDtos.MasterVenueResponse> replace(
      @Parameter(description = "Master venue UUID") @PathVariable final UUID id,
      @Valid @RequestBody final MasterVenueDtos.MasterVenueWriteRequest request) {
    return ResponseEntity.ok(masterVenueService.replaceVenue(id, request));
  }

  @PatchMapping("/{id}")
  @Operation(
      summary = "Partially update a master venue",
      description = "Only the fields present in the request are overwritten. "
                    + "Responds 404 when the venue does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Master venue updated",
                   content = @Content(schema = @Schema(implementation = MasterVenueDtos.MasterVenueResponse.class))),
      @ApiResponse(responseCode = "404", description = "Master venue not found", content = @Content)
  })
  public ResponseEntity<MasterVenueDtos.MasterVenueResponse> patch(
      @Parameter(description = "Master venue UUID") @PathVariable final UUID id,
      @Valid @RequestBody final MasterVenueDtos.MasterVenuePatchRequest request) {
    return ResponseEntity.ok(masterVenueService.patchVenue(id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(
      summary = "Delete a master venue",
      description = "Permanently deletes the venue. Aliases and external provider records are "
                    + "removed by the database cascade. Responds 404 when the venue does not exist.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Master venue deleted", content = @Content),
      @ApiResponse(responseCode = "404", description = "Master venue not found", content = @Content)
  })
  public ResponseEntity<Void> delete(
      @Parameter(description = "Master venue UUID") @PathVariable final UUID id) {
    masterVenueService.deleteVenue(id);
    return ResponseEntity.noContent().build();
  }
}
