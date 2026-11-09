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

package com.iqkv.docintelligence.catalogservice.venuedeck;

import jakarta.validation.Valid;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.infrastructure.security.JwtClaimNames;
import com.iqkv.docintelligence.catalogservice.venuedeck.dto.VenueDeckDtos;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-admin upload and management of venue deck PDFs across tenants.
 *
 * <p>Every request is scoped to the tenant named in the {@code {tenantKey}} path variable — the
 * controller sets {@code TenantContext} from it so the {@code MyBatisSchemaInterceptor} routes
 * queries to {@code t_{tenantKey}}. The path is under {@code /admin/}, so
 * {@code TenantExtractionFilter} does not run and {@code SecurityConfig} enforces the
 * {@code PLATFORM_ADMIN} authority. The upload flow mirrors the tenant surface
 * ({@link VenueDeckRestResource}).
 */
@RestController
@RequestMapping("/api/v1/catalog/admin/{tenantKey}/venue-decks")
@Tag(name = "Admin Venue Decks", description = "Platform-admin upload and management of tenant venue deck PDFs")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
public class AdminVenueDeckRestResource {

  private final VenueDeckService venueDeckService;

  public AdminVenueDeckRestResource(final VenueDeckService venueDeckService) {
    this.venueDeckService = venueDeckService;
  }

  @PostMapping("/initiate")
  @Operation(
      summary = "Initiate a venue deck upload (platform admin)",
      description = "Validates the declared content type and size against the upload policy, "
                    + "generates a tenant-scoped object key and a presigned S3 PUT URL, and returns "
                    + "the document id to use in the confirm call on behalf of the tenant named in "
                    + "the path. No row is persisted until confirm.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Presigned URL generated",
                   content = @Content(schema = @Schema(implementation = VenueDeckDtos.InitiateUploadResponse.class))),
      @ApiResponse(responseCode = "400", description = "Unsupported content type or file too large", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueDeckDtos.InitiateUploadResponse> initiateUpload(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @AuthenticationPrincipal final Jwt jwt,
      @Valid @RequestBody final VenueDeckDtos.InitiateUploadRequest request) {
    final UUID uploadedBy = UUID.fromString(jwt.getClaimAsString(JwtClaimNames.USER_ID));
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(venueDeckService.initiateUpload(request, uploadedBy));
    } finally {
      TenantContext.clear();
    }
  }

  @PostMapping("/{id}/confirm")
  @Operation(
      summary = "Confirm a venue deck upload (platform admin)",
      description = "Called after the client has PUT the file to the presigned URL. Validates that "
                    + "the object key matches this document and that the object exists in storage, "
                    + "reads the actual size and content type back, persists the file metadata and "
                    + "sets status UPLOADED. Responds 400 when the object key is wrong or the object "
                    + "was not uploaded.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Upload confirmed and metadata persisted",
                   content = @Content(schema = @Schema(implementation = VenueDeckDtos.VenueDocumentResponse.class))),
      @ApiResponse(responseCode = "400", description = "Object key mismatch or object not found in storage", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueDeckDtos.VenueDocumentResponse> confirmUpload(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @AuthenticationPrincipal final Jwt jwt,
      @Parameter(description = "Document UUID returned by initiate") @PathVariable final UUID id,
      @Valid @RequestBody final VenueDeckDtos.ConfirmUploadRequest request) {
    final UUID uploadedBy = UUID.fromString(jwt.getClaimAsString(JwtClaimNames.USER_ID));
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(venueDeckService.confirmUpload(id, request, uploadedBy));
    } finally {
      TenantContext.clear();
    }
  }

  @GetMapping
  @Operation(
      summary = "List venue decks (platform admin)",
      description = "Returns the venue decks of the tenant named in the path, ordered by most "
                    + "recently created, with offset pagination.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Venue decks of the tenant",
                   content = @Content(schema = @Schema(implementation = VenueDeckDtos.VenueDocumentListResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<VenueDeckDtos.VenueDocumentListResponse> list(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Maximum number of documents to return")
      @RequestParam(defaultValue = "20") final int limit,
      @Parameter(description = "Zero-based row offset")
      @RequestParam(defaultValue = "0") final int offset) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(venueDeckService.listDocuments(limit, offset));
    } finally {
      TenantContext.clear();
    }
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get a venue deck (platform admin)",
      description = "Returns a single venue deck of the tenant named in the path. Responds 404 when "
                    + "the document does not exist in the tenant schema.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Venue deck found",
                   content = @Content(schema = @Schema(implementation = VenueDeckDtos.VenueDocumentResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Venue deck not found", content = @Content)
  })
  public ResponseEntity<VenueDeckDtos.VenueDocumentResponse> getById(
      @Parameter(description = "8-char tenant key") @PathVariable final String tenantKey,
      @Parameter(description = "Document UUID") @PathVariable final UUID id) {
    try {
      TenantContext.setCurrentTenant(tenantKey);
      return ResponseEntity.ok(venueDeckService.getDocument(id));
    } finally {
      TenantContext.clear();
    }
  }
}
