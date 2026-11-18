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

import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venueask.dto.VenueChatDtos;

/**
 * Conversational chat over the tenant's venue catalogue.
 *
 * <p>Sessions and messages are persisted in the active tenant schema and owned by the calling user.
 * Each turn is grounded, best-effort, in the venues the tenant's catalogue ranks highest for the
 * message, so conversations stay focused on the tenant's own venue data.
 */
public interface VenueChatService {

  /**
   * Sends one message in a chat session and returns the assistant's reply.
   *
   * <p>A {@code null} {@code sessionId} starts a new session owned by {@code userId}; otherwise the
   * session is resumed and must be owned by {@code userId}.
   *
   * @throws EmptyMessageException             when the message carries no visible text
   * @throws PromptInjectionException          when the message is rejected by the prompt-injection guard
   * @throws VenueChatSessionNotFoundException when a non-null {@code sessionId} is unknown or owned
   *     by another user
   */
  VenueChatDtos.ChatResponse chat(UUID userId, VenueChatDtos.SendMessageRequest request);

  /** Lists the caller's own chat sessions, most recently updated first. */
  VenueChatDtos.SessionListResponse getSessions(UUID userId, int limit, int offset);

  /**
   * Lists the messages in one of the caller's own chat sessions, oldest first.
   *
   * @throws VenueChatSessionNotFoundException when the session is unknown or owned by another user
   */
  VenueChatDtos.MessageListResponse getMessages(UUID sessionId, UUID userId, int limit, int offset);

  /**
   * Deletes one of the caller's own chat sessions and its messages.
   *
   * @throws VenueChatSessionNotFoundException when the session is unknown or owned by another user
   */
  void deleteSession(UUID sessionId, UUID userId);
}
