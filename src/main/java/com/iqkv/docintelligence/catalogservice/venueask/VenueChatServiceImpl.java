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

import com.iqkv.docintelligence.catalogservice.infrastructure.config.VenueAskProperties;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;
import com.iqkv.docintelligence.catalogservice.venueask.dto.VenueChatDtoMapper;
import com.iqkv.docintelligence.catalogservice.venueask.dto.VenueChatDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default {@link VenueChatService}: persists the conversation and grounds each turn in the
 * tenant's own venue catalogue.
 *
 * <p>Every turn runs the same prompt-injection guard as the ask surface, then retrieves the venues
 * the catalogue ranks highest for the message and hands them to {@link VenueAskAssistant} as a
 * grounding block. When retrieval or the model is unavailable the conversation still continues —
 * the turn is answered ungrounded rather than failing.
 */
@Service
class VenueChatServiceImpl implements VenueChatService {

  private static final Logger log = LoggerFactory.getLogger(VenueChatServiceImpl.class);

  private static final int TITLE_MAX_CHARS = 60;

  /** How many catalogue venues are retrieved to ground one chat turn. */
  private static final int GROUNDING_VENUES = 10;

  /** Returned when no reply could be produced for a turn. */
  static final String UNAVAILABLE_REPLY =
      "I can't answer that from your venue catalogue right now.";

  private final VenueChatSessionMapper sessionMapper;
  private final VenueChatMessageMapper messageMapper;
  private final VenueContextAssembler contextAssembler;
  private final VenueAskAssistant assistant;
  private final VenueAskProperties properties;

  VenueChatServiceImpl(final VenueChatSessionMapper sessionMapper,
                       final VenueChatMessageMapper messageMapper,
                       final VenueContextAssembler contextAssembler,
                       final VenueAskAssistant assistant,
                       final VenueAskProperties properties) {
    this.sessionMapper = sessionMapper;
    this.messageMapper = messageMapper;
    this.contextAssembler = contextAssembler;
    this.assistant = assistant;
    this.properties = properties;
  }

  @Override
  public VenueChatDtos.ChatResponse chat(final UUID userId,
                                         final VenueChatDtos.SendMessageRequest request) {
    // Reject input that carries no visible text before anything is persisted or sent to the model.
    if (PromptGuard.isEffectivelyBlank(request.content())) {
      log.warn("Rejected empty venue chat message: userId={}", userId);
      throw new EmptyMessageException();
    }
    final PromptGuard.Verdict verdict =
        guardEnabled() ? PromptGuard.inspect(request.content()) : PromptGuard.Verdict.allowed();
    if (verdict.blocked()) {
      // Rejected before anything is persisted, so a blocked attempt leaves no trace.
      log.warn("Rejected venue chat message: userId={}, rule={}", userId, verdict.rule());
      throw new PromptInjectionException(verdict.rule());
    }

    final VenueChatSession session = resolveSession(userId, request);
    final String userContent = truncate(request.content(), properties.maxInputChars());
    insertMessage(session.getId(), MessageRole.USER, userContent);

    final String reply = reply(userContent, session.getModel());
    return recordReply(session, userContent, reply);
  }

  @Override
  public VenueChatDtos.SessionListResponse getSessions(final UUID userId, final int limit, final int offset) {
    final var sessions = sessionMapper.findByUserId(userId, limit, offset);
    return new VenueChatDtos.SessionListResponse(
        sessions.stream().map(VenueChatDtoMapper::toSessionResponse).toList(),
        sessionMapper.countByUserId(userId));
  }

  @Override
  public VenueChatDtos.MessageListResponse getMessages(final UUID sessionId, final UUID userId,
                                                       final int limit, final int offset) {
    requireOwnedSession(sessionId, userId);
    final var messages = messageMapper.findBySessionId(sessionId, limit, offset);
    return new VenueChatDtos.MessageListResponse(
        messages.stream().map(VenueChatDtoMapper::toMessageResponse).toList(),
        messageMapper.countBySessionId(sessionId));
  }

  @Override
  public void deleteSession(final UUID sessionId, final UUID userId) {
    requireOwnedSession(sessionId, userId);
    messageMapper.deleteBySessionId(sessionId);
    sessionMapper.delete(sessionId);
  }

  /**
   * Creates a session owned by {@code userId}, or resumes the requested one when it exists and is
   * owned by the caller. An unknown or foreign session resolves to 404 rather than leaking that it
   * exists.
   */
  private VenueChatSession resolveSession(final UUID userId, final VenueChatDtos.SendMessageRequest request) {
    if (request.sessionId() == null) {
      final VenueChatSession session = new VenueChatSession();
      session.setId(UUID.randomUUID());
      session.setUserId(userId);
      session.setModel(request.model() != null && !request.model().isBlank()
          ? request.model() : assistant.model());
      session.setTitle(null);
      session.setCreatedAt(Instant.now());
      session.setUpdatedAt(Instant.now());
      sessionMapper.insert(session);
      log.info("Created venue chat session: sessionId={}, userId={}, model={}",
          session.getId(), userId, session.getModel());
      return session;
    }
    final VenueChatSession session = requireOwnedSession(request.sessionId(), userId);
    log.info("Resumed venue chat session: sessionId={}, userId={}", session.getId(), userId);
    return session;
  }

  private VenueChatSession requireOwnedSession(final UUID sessionId, final UUID userId) {
    return sessionMapper.findById(sessionId)
        .filter(session -> session.getUserId().equals(userId))
        .orElseThrow(() -> new VenueChatSessionNotFoundException(sessionId));
  }

  /**
   * Answers one turn, grounded in the catalogue venues most relevant to the message. A failed
   * retrieval or model call degrades to {@link #UNAVAILABLE_REPLY} so the conversation continues.
   */
  private String reply(final String userContent, final String model) {
    final VenueDtos.VenuePublicSemanticSearchResponse search =
        contextAssembler.retrieveVenues(userContent, null, GROUNDING_VENUES);
    final String context = search.items().isEmpty()
        ? "" : truncate(VenueContextAssembler.renderVenueListBlock(search.items()), properties.maxContextChars());

    final String reply = assistant.reply(userContent, context, model);
    if (reply == null) {
      log.info("Venue chat produced no reply, using the unavailable-reply fallback");
      return UNAVAILABLE_REPLY;
    }
    return reply;
  }

  /** Stores the assistant turn, refreshes the session and builds the response. */
  private VenueChatDtos.ChatResponse recordReply(final VenueChatSession session, final String userContent,
                                                 final String reply) {
    final Instant now = Instant.now();
    insertMessage(session.getId(), MessageRole.ASSISTANT, reply);

    session.setUpdatedAt(now);
    if (session.getTitle() == null) {
      session.setTitle(truncate(userContent, TITLE_MAX_CHARS));
    }
    sessionMapper.update(session);

    return new VenueChatDtos.ChatResponse(session.getId(), session.getTitle(), reply, session.getModel(), now);
  }

  private void insertMessage(final UUID sessionId, final MessageRole role, final String content) {
    final VenueChatMessage message = new VenueChatMessage();
    message.setId(UUID.randomUUID());
    message.setSessionId(sessionId);
    message.setRole(role);
    message.setContent(content);
    message.setCreatedAt(Instant.now());
    messageMapper.insert(message);
  }

  private static String truncate(final String value, final int max) {
    if (value == null || max <= 0 || value.length() <= max) {
      return value;
    }
    return value.substring(0, max);
  }

  private boolean guardEnabled() {
    return properties.guard() != null && properties.guard().enabled();
  }
}
