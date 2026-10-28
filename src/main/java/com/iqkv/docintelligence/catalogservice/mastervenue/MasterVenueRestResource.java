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

import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.mastervenue.dto.MasterVenueDtos;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-facing master venue surface — search and read only.
 *
 * <p>Writes live exclusively on {@link AdminMasterVenueRestResource}. Every response here uses the
 * safe projection: curation fields such as {@code confidence}, {@code source} and the raw
 * {@code metadata} document are not exposed.
 *
 * <p>The tenant context is resolved for these paths by {@code TenantExtractionFilter} from the
 * {@code X-Tenant-ID} header or the JWT {@code tenant_id} claim.
 */
@RestController
@RequestMapping("/api/v1/catalog/master-venues")
@Tag(name = "Master Venues", description = "Read-only access to the platform master venue catalog")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('MEMBER')")
public class MasterVenueRestResource {

  private final MasterVenueService masterVenueService;

  public MasterVenueRestResource(final MasterVenueService masterVenueService) {
    this.masterVenueService = masterVenueService;
  }

  @GetMapping
  @Operation(
      summary = "Search master venues",
      description = "Keyword search over name, address and city, with an optional exact-city filter "
                    + "and offset pagination. Results are ordered by name. Returns the safe projection only.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Matching master venues",
                   content = @Content(schema = @Schema(implementation = MasterVenueDtos.MasterVenueListResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — MEMBER authority required", content = @Content)
  })
  public ResponseEntity<MasterVenueDtos.MasterVenueListResponse> search(
      @Parameter(description = "Free-text keyword matched against name, address and city")
      @RequestParam(required = false) final String search,
      @Parameter(description = "Exact city filter, compared case-insensitively")
      @RequestParam(required = false) final String city,
      @Parameter(description = "Maximum number of venues to return")
      @RequestParam(defaultValue = "20") final int limit,
      @Parameter(description = "Zero-based row offset")
      @RequestParam(defaultValue = "0") final int offset) {
    return ResponseEntity.ok(masterVenueService.searchPublicVenues(search, city, limit, offset));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get a master venue",
      description = "Returns the safe projection of the given master venue. "
                    + "Responds 404 when the venue does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Master venue found",
                   content = @Content(schema = @Schema(implementation = MasterVenueDtos.MasterVenuePublicResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Master venue not found", content = @Content)
  })
  public ResponseEntity<MasterVenueDtos.MasterVenuePublicResponse> getById(
      @Parameter(description = "Master venue UUID") @PathVariable final UUID id) {
    return ResponseEntity.ok(masterVenueService.getPublicVenue(id));
  }

  @GetMapping("/{id}/assets")
  @Operation(
      summary = "List the assets of a master venue",
      description = "Returns the photos and attachments of the given master venue, ordered by display "
                    + "order. The ingestion provenance fields are not exposed. Responds 404 when the "
                    + "venue does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Assets of the master venue",
                   content = @Content(schema = @Schema(implementation = MasterVenueDtos.MasterVenueAssetListResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Master venue not found", content = @Content)
  })
  public ResponseEntity<MasterVenueDtos.MasterVenueAssetListResponse> listAssets(
      @Parameter(description = "Master venue UUID") @PathVariable final UUID id) {
    return ResponseEntity.ok(masterVenueService.listPublicVenueAssets(id));
  }

  @GetMapping("/{id}/photos")
  @Operation(
      summary = "List the photos of a master venue",
      description = "Returns the assets of type PHOTO of the given master venue, ordered by display "
                    + "order — the subset of /assets the gallery renders. The ingestion provenance "
                    + "fields are not exposed. Responds 404 when the venue does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Photos of the master venue",
                   content = @Content(schema = @Schema(implementation = MasterVenueDtos.MasterVenueAssetListResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Master venue not found", content = @Content)
  })
  public ResponseEntity<MasterVenueDtos.MasterVenueAssetListResponse> listPhotos(
      @Parameter(description = "Master venue UUID") @PathVariable final UUID id) {
    return ResponseEntity.ok(masterVenueService.listPublicVenuePhotos(id));
  }
}
