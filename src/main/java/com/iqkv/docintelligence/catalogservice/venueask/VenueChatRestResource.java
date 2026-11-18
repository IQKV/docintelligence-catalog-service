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

package com.iqkv.docintelligence.catalogservice.venueask;

import jakarta.validation.Valid;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.infrastructure.security.JwtClaimNames;
import com.iqkv.docintelligence.catalogservice.venueask.dto.VenueChatDtos;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conversational chat over the tenant's venue catalogue.
 *
 * <p>Sessions and messages live in the active tenant schema and are owned by the authenticated
 * user; sessions of other users resolve to 404. Each turn is grounded, best-effort, in the venues
 * the tenant's catalogue ranks highest for the message.
 *
 * <p>The tenant context is resolved by {@code TenantExtractionFilter} from the {@code X-Tenant-ID}
 * header or the JWT {@code tenant_id} claim, so the {@code MyBatisSchemaInterceptor} routes the
 * session and message queries to the caller's own {@code t_{tenantKey}} schema.
 *
 * <p>The routes mirror the AI chat client contract ({@code POST}, {@code GET /sessions},
 * {@code GET /sessions/{sessionId}/messages}, {@code DELETE /sessions/{sessionId}}) so the venue UI
 * can point at this service by swapping the endpoint base only.
 */
@RestController
@RequestMapping("/api/v1/catalog/chat")
@Tag(name = "Venue Chat", description = "Conversational chat grounded in the tenant's venue catalogue")
@SecurityRequirement(name = "bearerAuth")
public class VenueChatRestResource {

  private final VenueChatService venueChatService;

  public VenueChatRestResource(final VenueChatService venueChatService) {
    this.venueChatService = venueChatService;
  }

  @PostMapping
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'MEMBER')")
  @Operation(
      summary = "Send a chat message",
      description = "Sends one message in a venue-catalogue chat session and returns the reply. A "
                    + "null sessionId starts a new session; otherwise the session is resumed and "
                    + "must be owned by the caller.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The assistant's reply",
                   content = @Content(schema = @Schema(implementation = VenueChatDtos.ChatResponse.class))),
      @ApiResponse(responseCode = "400", description = "Empty message, rejected by the prompt-injection guard, or tenant ID unresolvable", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Chat session not found", content = @Content)
  })
  public ResponseEntity<VenueChatDtos.ChatResponse> chat(
      @Valid @RequestBody final VenueChatDtos.SendMessageRequest request,
      @AuthenticationPrincipal final Jwt jwt) {
    return ResponseEntity.ok(venueChatService.chat(userId(jwt), request));
  }

  @GetMapping("/sessions")
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'MEMBER')")
  @Operation(
      summary = "List the caller's chat sessions",
      description = "Lists the authenticated user's own venue-catalogue chat sessions, most "
                    + "recently updated first.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The caller's chat sessions",
                   content = @Content(schema = @Schema(implementation = VenueChatDtos.SessionListResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content)
  })
  public ResponseEntity<VenueChatDtos.SessionListResponse> getSessions(
      @Parameter(description = "Maximum number of sessions to return")
      @RequestParam(defaultValue = "20") final int limit,
      @Parameter(description = "Zero-based row offset")
      @RequestParam(defaultValue = "0") final int offset,
      @AuthenticationPrincipal final Jwt jwt) {
    return ResponseEntity.ok(venueChatService.getSessions(userId(jwt), limit, offset));
  }

  @GetMapping("/sessions/{sessionId}/messages")
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'MEMBER')")
  @Operation(
      summary = "List the messages in a chat session",
      description = "Lists the messages of one of the caller's own chat sessions, oldest first.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The session's messages",
                   content = @Content(schema = @Schema(implementation = VenueChatDtos.MessageListResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Chat session not found", content = @Content)
  })
  public ResponseEntity<VenueChatDtos.MessageListResponse> getMessages(
      @Parameter(description = "Chat session UUID") @PathVariable final UUID sessionId,
      @Parameter(description = "Maximum number of messages to return")
      @RequestParam(defaultValue = "50") final int limit,
      @Parameter(description = "Zero-based row offset")
      @RequestParam(defaultValue = "0") final int offset,
      @AuthenticationPrincipal final Jwt jwt) {
    return ResponseEntity.ok(venueChatService.getMessages(sessionId, userId(jwt), limit, offset));
  }

  @DeleteMapping("/sessions/{sessionId}")
  @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'MEMBER')")
  @Operation(
      summary = "Delete a chat session",
      description = "Deletes one of the caller's own chat sessions and all of its messages.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key. "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Session deleted", content = @Content),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — TENANT_OWNER or MEMBER authority required", content = @Content),
      @ApiResponse(responseCode = "404", description = "Chat session not found", content = @Content)
  })
  public ResponseEntity<Void> deleteSession(
      @Parameter(description = "Chat session UUID") @PathVariable final UUID sessionId,
      @AuthenticationPrincipal final Jwt jwt) {
    venueChatService.deleteSession(sessionId, userId(jwt));
    return ResponseEntity.noContent().build();
  }

  private static UUID userId(final Jwt jwt) {
    return UUID.fromString(jwt.getClaimAsString(JwtClaimNames.USER_ID));
  }
}
