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

import java.time.Instant;
import java.util.UUID;

/**
 * One turn of a {@link VenueChatSession}.
 */
public class VenueChatMessage {

  private UUID id;
  private UUID sessionId;
  private MessageRole role;
  private String content;
  /** Denormalised JSONB copy of the sources this turn cited; null on user turns. */
  private String sources;
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public void setId(final UUID id) {
    this.id = id;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public void setSessionId(final UUID sessionId) {
    this.sessionId = sessionId;
  }

  public MessageRole getRole() {
    return role;
  }

  public void setRole(final MessageRole role) {
    this.role = role;
  }

  public String getContent() {
    return content;
  }

  public void setContent(final String content) {
    this.content = content;
  }

  public String getSources() {
    return sources;
  }

  public void setSources(final String sources) {
    this.sources = sources;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(final Instant createdAt) {
    this.createdAt = createdAt;
  }
}
