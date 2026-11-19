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

import java.util.List;

import com.iqkv.docintelligence.catalogservice.venueask.VenueChatMessage;
import com.iqkv.docintelligence.catalogservice.venueask.VenueChatSession;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps the chat domain models to their response projections.
 *
 * <p>A message's cited sources are stored as raw JSONB and converted here — the single JSON
 * boundary of the slice.
 */
public final class VenueChatDtoMapper {

  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  private VenueChatDtoMapper() {
  }

  public static VenueChatDtos.SessionResponse toSessionResponse(final VenueChatSession session) {
    return new VenueChatDtos.SessionResponse(
        session.getId(),
        session.getUserId(),
        session.getTitle(),
        session.getModel(),
        session.getCreatedAt(),
        session.getUpdatedAt()
    );
  }

  public static VenueChatDtos.MessageResponse toMessageResponse(final VenueChatMessage message) {
    return new VenueChatDtos.MessageResponse(
        message.getId(),
        message.getSessionId(),
        message.getRole(),
        message.getContent(),
        message.getCreatedAt(),
        parseSources(message.getSources())
    );
  }

  /**
   * Serialises the sources one turn was grounded in to the JSON stored on its message row.
   * Returns {@code null} when there is nothing to cite, so the column stays null.
   */
  public static String sourcesToJson(final List<VenueAskDtos.VenueAskSource> sources) {
    return sources == null || sources.isEmpty() ? null : JSON_MAPPER.writeValueAsString(sources);
  }

  private static List<VenueAskDtos.VenueAskSource> parseSources(final String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return JSON_MAPPER.readValue(json, new TypeReference<List<VenueAskDtos.VenueAskSource>>() {
      });
    } catch (final Exception ignored) {
      // An unreadable row cites nothing rather than breaking the whole message list.
      return List.of();
    }
  }
}
