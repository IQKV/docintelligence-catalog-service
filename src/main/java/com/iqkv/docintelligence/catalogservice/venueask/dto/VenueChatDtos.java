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

package com.iqkv.docintelligence.catalogservice.venueask.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venueask.MessageRole;

/**
 * Request and response records for the catalog chat session surface.
 *
 * <p>The shapes mirror the existing AI chat client contract so the venue UI can point at these
 * catalog endpoints without any DTO change — the client swaps the endpoint, not the payload.
 */
public final class VenueChatDtos {

  private VenueChatDtos() {
  }

  /**
   * Request to send a message. {@code sessionId} is nullable — null means create a new session.
   */
  public record SendMessageRequest(UUID sessionId, @NotBlank String content, String model) {
  }

  public record ChatResponse(UUID sessionId, String sessionTitle, String reply, String model, Instant timestamp) {
  }

  public record SessionResponse(UUID id, UUID userId, String title, String model, Instant createdAt,
                                Instant updatedAt) {
  }

  public record SessionListResponse(List<SessionResponse> items, long totalElements) {
  }

  public record MessageResponse(UUID id, UUID sessionId, MessageRole role, String content, Instant createdAt) {
  }

  public record MessageListResponse(List<MessageResponse> items, long totalElements) {
  }
}
