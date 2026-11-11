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
import com.iqkv.foundation.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-admin mirror of {@link VenueAssetImportRestResource}: venue-less asset import that
 * derives a venue record, scoped to the tenant named in the {@code {tenantKey}} path variable.
 *
 * <p>Every request sets {@code TenantContext} from the path variable so the
 * {@code MyBatisSchemaInterceptor} routes queries to {@code t_{tenantKey}}. The path is under
 * {@code /admin/}, so {@code TenantExtractionFilter} does not run. The two-phase presigned upload
 * flow is identical to the tenant surface — {@code POST /initiate} mints a presigned PUT URL and
 * an asset id, the client uploads directly to MinIO, then {@code POST /{id}/confirm} persists the
 * asset with a {@code null} venue and status {@code UPLOADED} for the processing worker to pick up.
 */
@RestController
@RequestMapping("/api/v1/catalog/admin/{tenantKey}/venue-assets")
@Tag(name = "Admin Venue Asset Import", description = "Platform-admin venue-less asset import that derives a venue record")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
public class AdminVenueAssetImportRestResource {

  private final VenueAssetService venueAssetService;

  public AdminVenueAssetImportRestResource(final VenueAssetService venueAssetService) {
    this.venueAssetService = venueAssetService;
  }

  @PostMapping("/initiate")
  @Operation(
      summary = "Initiate a venue-less asset upload (platform admin)",
      description = "Validates the declared content type and size against the upload policy, "
                    + "generates a tenant-scoped object key and a presigned S3 PUT URL on behalf of "
                    + "the tenant named in the path, and returns the asset id to use in the confirm "
                    + "call. No row is persisted until confirm.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Presigned URL generated",
                   content = @Content(schema = @Schema(implementation = VenueAssetDtos.InitiateAssetUploadResponse.class))),
      @ApiResponse(responseCode = "400", description = "Unsupported content type or file too large", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueAssetDtos.InitiateAssetUploadResponse> initiateUpload(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @AuthenticationPrincipal final Jwt jwt,
      @Valid @RequestBody final VenueAssetDtos.InitiateAssetUploadRequest request) {
    final UUID uploadedBy = UUID.fromString(jwt.getClaimAsString(JwtClaimNames.USER_ID));
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(venueAssetService.initiateUpload(request, uploadedBy));
    } finally {
      TenantContext.clear();
    }
  }

  @PostMapping("/{id}/confirm")
  @Operation(
      summary = "Confirm a venue-less asset upload (platform admin)",
      description = "Called after the client has PUT the file to the presigned URL. Validates that "
                    + "the object key matches this asset and that the object exists in storage, "
                    + "reads the actual size and content type back, and persists the asset with a "
                    + "null venue and status UPLOADED so the processing worker picks it up.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Upload confirmed and queued for processing",
                   content = @Content(schema = @Schema(implementation = VenueAssetDtos.VenueAssetResponse.class))),
      @ApiResponse(responseCode = "400", description = "Object key mismatch or object not found in storage", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueAssetDtos.VenueAssetResponse> confirmUpload(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @AuthenticationPrincipal final Jwt jwt,
      @Parameter(description = "Asset UUID returned by initiate") @PathVariable final UUID id,
      @Valid @RequestBody final VenueAssetDtos.ConfirmAssetUploadRequest request) {
    final UUID uploadedBy = UUID.fromString(jwt.getClaimAsString(JwtClaimNames.USER_ID));
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(venueAssetService.confirmUpload(id, request, uploadedBy));
    } finally {
      TenantContext.clear();
    }
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get an imported asset and its processing status (platform admin)",
      description = "Returns the asset and its status: UPLOADED, PARSING, then EXTRACTED (the venue "
                    + "is linked — see venueId) or FAILED (see failureReason).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Asset found",
                   content = @Content(schema = @Schema(implementation = VenueAssetDtos.VenueAssetResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Asset not found", content = @Content)
  })
  public ResponseEntity<VenueAssetDtos.VenueAssetResponse> getById(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Asset UUID") @PathVariable final UUID id) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(venueAssetService.getAssetById(id));
    } finally {
      TenantContext.clear();
    }
  }
}
