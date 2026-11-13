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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant upload and management of venue assets — a venue's photos and attachments.
 *
 * <p>Two-phase presigned flow: {@code POST /initiate} mints a presigned PUT URL and an asset id;
 * the client uploads directly to MinIO; {@code POST /{id}/confirm} validates the object and
 * persists the asset metadata. List, photos and detail reads are nested under the owning venue and
 * tenant-scoped via the {@code X-Tenant-ID} header / JWT {@code tenant_id} claim resolved by
 * {@code TenantExtractionFilter}.
 */
@RestController
@RequestMapping("/api/v1/catalog/venues/{venueId}/assets")
@Tag(name = "Venue Assets", description = "Tenant upload and management of venue assets")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'MEMBER')")
public class VenueAssetRestResource {

  private final VenueAssetService venueAssetService;

  public VenueAssetRestResource(final VenueAssetService venueAssetService) {
    this.venueAssetService = venueAssetService;
  }

  @PostMapping("/initiate")
  @Operation(
      summary = "Initiate a venue asset upload",
      description = "Validates the declared content type and size against the upload policy, "
                    + "generates a tenant-scoped object key and a presigned S3 PUT URL, and returns "
                    + "the asset id to use in the confirm call. No row is persisted until confirm. "
                    + "Responds 404 when the venue does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Presigned URL generated",
                   content = @Content(schema = @Schema(implementation = VenueAssetDtos.InitiateAssetUploadResponse.class))),
      @ApiResponse(responseCode = "400", description = "Unsupported content type or file too large", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueAssetDtos.InitiateAssetUploadResponse> initiateUpload(
      @Parameter(description = "Owning venue UUID") @PathVariable final UUID venueId,
      @AuthenticationPrincipal final Jwt jwt,
      @Valid @RequestBody final VenueAssetDtos.InitiateAssetUploadRequest request) {
    final UUID uploadedBy = UUID.fromString(jwt.getClaimAsString(JwtClaimNames.USER_ID));
    return ResponseEntity.ok(venueAssetService.initiateUpload(venueId, request, uploadedBy));
  }

  @PostMapping("/{id}/confirm")
  @Operation(
      summary = "Confirm a venue asset upload",
      description = "Called after the client has PUT the file to the presigned URL. Validates that "
                    + "the object key matches this asset and that the object exists in storage, "
                    + "reads the actual size and content type back, and persists the asset metadata. "
                    + "Responds 400 when the object key is wrong or the object was not uploaded, and "
                    + "404 when the venue does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Upload confirmed and metadata persisted",
                   content = @Content(schema = @Schema(implementation = VenueAssetDtos.VenueAssetResponse.class))),
      @ApiResponse(responseCode = "400", description = "Object key mismatch or object not found in storage", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueAssetDtos.VenueAssetResponse> confirmUpload(
      @Parameter(description = "Owning venue UUID") @PathVariable final UUID venueId,
      @AuthenticationPrincipal final Jwt jwt,
      @Parameter(description = "Asset UUID returned by initiate") @PathVariable final UUID id,
      @Valid @RequestBody final VenueAssetDtos.ConfirmAssetUploadRequest request) {
    final UUID uploadedBy = UUID.fromString(jwt.getClaimAsString(JwtClaimNames.USER_ID));
    return ResponseEntity.ok(venueAssetService.confirmUpload(venueId, id, request, uploadedBy));
  }

  @GetMapping
  @Operation(
      summary = "List the assets of a venue",
      description = "Returns the photos and attachments of the given venue, ordered by display "
                    + "order. Responds 404 when the venue does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Assets of the venue",
                   content = @Content(schema = @Schema(implementation = VenueAssetDtos.VenueAssetListResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueAssetDtos.VenueAssetListResponse> list(
      @Parameter(description = "Owning venue UUID") @PathVariable final UUID venueId) {
    return ResponseEntity.ok(venueAssetService.listAssets(venueId));
  }

  @GetMapping("/photos")
  @Operation(
      summary = "List the photos of a venue",
      description = "Returns the assets of type PHOTO of the given venue, ordered by display order "
                    + "— the subset of /assets the gallery renders. Each item includes a "
                    + "time-limited presigned GET URL in 'sourceUrl' for direct browser rendering. "
                    + "Responds 404 when the venue does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Photos of the venue",
                   content = @Content(schema = @Schema(implementation = VenueAssetDtos.VenueAssetPublicListResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueAssetDtos.VenueAssetPublicListResponse> listPhotos(
      @Parameter(description = "Owning venue UUID") @PathVariable final UUID venueId) {
    return ResponseEntity.ok(venueAssetService.listPhotosPublic(venueId));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get a venue asset",
      description = "Returns a single asset of the given venue. Responds 404 when the venue does "
                    + "not exist, or when the asset does not exist or is not owned by that venue.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Asset found",
                   content = @Content(schema = @Schema(implementation = VenueAssetDtos.VenueAssetResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue or asset not found", content = @Content)
  })
  public ResponseEntity<VenueAssetDtos.VenueAssetResponse> getById(
      @Parameter(description = "Owning venue UUID") @PathVariable final UUID venueId,
      @Parameter(description = "Asset UUID") @PathVariable final UUID id) {
    return ResponseEntity.ok(venueAssetService.getAsset(venueId, id));
  }

  @GetMapping("/{id}/preview-url")
  @Operation(
      summary = "Get a presigned preview URL for a venue asset",
      description = "Mints a short-lived presigned GET URL for the stored object so the browser "
                    + "can render it inline — images and PDFs alike. Responds 404 when the venue "
                    + "does not exist, or when the asset does not exist or is not owned by that venue.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Presigned preview URL generated",
                   content = @Content(schema = @Schema(implementation = VenueAssetDtos.VenueAssetPreviewUrlResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue or asset not found", content = @Content)
  })
  public ResponseEntity<VenueAssetDtos.VenueAssetPreviewUrlResponse> previewUrl(
      @Parameter(description = "Owning venue UUID") @PathVariable final UUID venueId,
      @Parameter(description = "Asset UUID") @PathVariable final UUID id) {
    return ResponseEntity.ok(venueAssetService.getPreviewUrl(venueId, id));
  }

  @GetMapping("/master-photos")
  @Operation(
      summary = "List the master-catalog photos of a venue",
      description = "When the venue is linked to a master-catalog record, returns the photos of "
                    + "that master venue, ordered by display order — the reference gallery shown "
                    + "beneath the venue's own photos. Each item includes a time-limited presigned "
                    + "GET URL in 'sourceUrl'. Returns an empty list when the venue has no master "
                    + "link. Responds 404 when the venue does not exist.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Master-catalog photos of the venue",
                   content = @Content(schema = @Schema(implementation = VenueAssetDtos.VenueAssetPublicListResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
  })
  public ResponseEntity<VenueAssetDtos.VenueAssetPublicListResponse> listMasterPhotos(
      @Parameter(description = "Owning venue UUID") @PathVariable final UUID venueId) {
    return ResponseEntity.ok(venueAssetService.listMasterPhotos(venueId));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'ADMIN')")
  @Operation(
      summary = "Delete a venue asset",
      description = "Permanently deletes the asset row and removes the object from storage. "
                    + "Responds 404 when the venue does not exist, or when the asset does not "
                    + "exist or is not owned by that venue. Storage removal is best-effort: a "
                    + "storage failure is logged but does not prevent the row from being deleted.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Asset deleted", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403",
                   description = "Access denied — TENANT_OWNER or ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue or asset not found", content = @Content)
  })
  public ResponseEntity<Void> delete(
      @Parameter(description = "Owning venue UUID") @PathVariable final UUID venueId,
      @Parameter(description = "Asset UUID") @PathVariable final UUID id) {
    venueAssetService.deleteAsset(venueId, id);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }
}
