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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.infrastructure.config.VenueAskProperties;
import com.iqkv.docintelligence.catalogservice.venue.VenueService;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;
import com.iqkv.docintelligence.catalogservice.venueask.dto.VenueAskDtos;
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
 * grounding block. A turn that names a venue is instead grounded in that one venue's profile and
 * source chunks, so a conversation opened from a venue page stays scoped to it. When retrieval or
 * the model is unavailable the conversation still continues — the turn is answered ungrounded
 * rather than failing.
 */
@Service
class VenueChatServiceImpl implements VenueChatService {

  private static final Logger log = LoggerFactory.getLogger(VenueChatServiceImpl.class);

  private static final int TITLE_MAX_CHARS = 60;

  /** How many catalogue venues are retrieved to ground one chat turn. */
  private static final int GROUNDING_VENUES = 10;

  /** How many source chunks of a single venue are retrieved to ground one chat turn. */
  private static final int GROUNDING_SOURCES = 6;

  /** Returned when no reply could be produced for a turn. */
  static final String UNAVAILABLE_REPLY =
      "I can't answer that from your venue catalogue right now.";

  private final VenueChatSessionMapper sessionMapper;
  private final VenueChatMessageMapper messageMapper;
  private final VenueContextAssembler contextAssembler;
  private final VenueAskAssistant assistant;
  private final VenueAskProperties properties;
  private final VenueService venueService;

  VenueChatServiceImpl(final VenueChatSessionMapper sessionMapper,
                       final VenueChatMessageMapper messageMapper,
                       final VenueContextAssembler contextAssembler,
                       final VenueAskAssistant assistant,
                       final VenueAskProperties properties,
                       final VenueService venueService) {
    this.sessionMapper = sessionMapper;
    this.messageMapper = messageMapper;
    this.contextAssembler = contextAssembler;
    this.assistant = assistant;
    this.properties = properties;
    this.venueService = venueService;
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

    // Resolved before anything is persisted, so a venue that is unknown in this tenant's schema
    // leaves no trace of the turn.
    final VenueDtos.VenuePublicResponse venue = request.venueId() == null
        ? null : venueService.getPublicVenue(request.venueId());

    final VenueChatSession session = resolveSession(userId, request);
    // Read before the current turn is persisted, so the memory holds the earlier turns only.
    final List<VenueChatMessage> history = history(session.getId());
    final String userContent = truncate(request.content(), properties.maxInputChars());
    insertMessage(session.getId(), MessageRole.USER, userContent, null);

    final GroundedReply reply = reply(userContent, history, venue, session.getModel());
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
   * Answers one turn, grounded either in the one venue the turn names or, when it names none, in
   * the catalogue venues most relevant to the message. Retrieval is ranked against the turn plus
   * the previous question (see {@link #retrievalQuery}), and the earlier turns of the session are
   * handed to the model as memory. A failed retrieval or model call degrades to
   * {@link #UNAVAILABLE_REPLY} so the conversation continues.
   */
  private GroundedReply reply(final String userContent,
                              final List<VenueChatMessage> history,
                              final VenueDtos.VenuePublicResponse venue,
                              final String model) {
    final String query = retrievalQuery(history, userContent);
    final String context;
    final List<VenueAskDtos.VenueAskSource> sources;
    if (venue == null) {
      final VenueDtos.VenuePublicSemanticSearchResponse search =
          contextAssembler.retrieveVenues(query, null, GROUNDING_VENUES);
      context = search.items().isEmpty()
          ? "" : truncate(VenueContextAssembler.renderVenueListBlock(search.items()), properties.maxContextChars());
      // The catalogue path cites venues, not source chunks, so there is nothing to return here.
      sources = List.of();
    } else {
      sources = contextAssembler.retrieveSources(venue.id(), query, GROUNDING_SOURCES);
      context = truncate(VenueContextAssembler.renderVenueBlock(venue, sources), properties.maxContextChars());
    }

    final String reply = assistant.reply(userContent, context, history, model);
    if (reply == null) {
      log.info("Venue chat produced no reply, using the unavailable-reply fallback");
      return new GroundedReply(UNAVAILABLE_REPLY, sources);
    }
    return new GroundedReply(reply, sources);
  }

  /**
   * The earlier turns of {@code sessionId} in speaking order, trimmed to the configured turn count
   * and total size so a long conversation still fits the model's context. An empty list means the
   * turn is answered without memory — the first turn of every session, and any session whose stored
   * turns do not fit the budget.
   */
  private List<VenueChatMessage> history(final UUID sessionId) {
    final List<VenueChatMessage> recent =
        new ArrayList<>(messageMapper.findRecentBySessionId(sessionId, properties.maxHistoryMessages()));
    Collections.reverse(recent);
    int budget = properties.maxHistoryChars();
    int from = recent.size();
    while (from > 0) {
      final String content = recent.get(from - 1).getContent();
      final int cost = content == null ? 0 : content.length();
      if (cost > budget) {
        break;
      }
      budget -= cost;
      from--;
    }
    return List.copyOf(recent.subList(from, recent.size()));
  }

  /**
   * The text retrieval is ranked against. A follow-up such as "and the parking?" names no subject of
   * its own, so it is ranked together with the previous question of the conversation; the current
   * text stays first so it survives the input cap. A turn with no earlier question — or one whose
   * earlier turns carry none, as when the model answered a question of its own — ranks on its own
   * text alone.
   */
  private String retrievalQuery(final List<VenueChatMessage> history, final String question) {
    String previous = "";
    for (int i = history.size() - 1; i >= 0; i--) {
      final VenueChatMessage message = history.get(i);
      if (message.getRole() == MessageRole.USER && message.getContent() != null
          && !message.getContent().isBlank()) {
        previous = message.getContent();
        break;
      }
    }
    return previous.isEmpty() ? question : truncate(question + " " + previous, properties.maxInputChars());
  }

  /** Stores the assistant turn, refreshes the session and builds the response. */
  private VenueChatDtos.ChatResponse recordReply(final VenueChatSession session, final String userContent,
                                                 final GroundedReply reply) {
    final Instant now = Instant.now();
    // The cited sources are stored on the assistant turn so the citations survive a reload.
    insertMessage(session.getId(), MessageRole.ASSISTANT, reply.text(),
        VenueChatDtoMapper.sourcesToJson(reply.sources()));

    session.setUpdatedAt(now);
    if (session.getTitle() == null) {
      session.setTitle(truncate(userContent, TITLE_MAX_CHARS));
    }
    sessionMapper.update(session);

    return new VenueChatDtos.ChatResponse(session.getId(), session.getTitle(), reply.text(), session.getModel(),
        now, reply.sources());
  }

  private void insertMessage(final UUID sessionId, final MessageRole role, final String content,
                             final String sources) {
    final VenueChatMessage message = new VenueChatMessage();
    message.setId(UUID.randomUUID());
    message.setSessionId(sessionId);
    message.setRole(role);
    message.setContent(content);
    message.setSources(sources);
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

  /** One answered turn — the reply text and the source chunks it was grounded in. */
  private record GroundedReply(String text, List<VenueAskDtos.VenueAskSource> sources) {
  }
}
