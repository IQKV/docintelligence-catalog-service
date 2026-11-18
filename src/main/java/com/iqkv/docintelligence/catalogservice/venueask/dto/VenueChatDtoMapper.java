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

import com.iqkv.docintelligence.catalogservice.venueask.VenueChatMessage;
import com.iqkv.docintelligence.catalogservice.venueask.VenueChatSession;

/**
 * Maps the chat domain models to their response projections.
 */
public final class VenueChatDtoMapper {

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
        message.getCreatedAt()
    );
  }
}
