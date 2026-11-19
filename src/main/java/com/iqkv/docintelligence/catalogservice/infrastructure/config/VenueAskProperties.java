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

package com.iqkv.docintelligence.catalogservice.infrastructure.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code iqkv.catalog.venue-ask.*} from {@code application.yml}.
 *
 * <p>Controls the catalog-native chat / RAG feature that grounds answers in the tenant's own
 * venue data (venue profile, source text chunks, assets) instead of a general-purpose assistant:
 * <ul>
 *   <li>{@code enabled} — master switch for both the ask/RAG endpoints and the chat endpoints.
 *       When off, the ask endpoints answer {@code answered=false} with the retrieved sources and
 *       the chat endpoints still persist sessions but degrade to a canned reply.</li>
 *   <li>{@code system-prompt} — persona and grounding rules injected as the system message.</li>
 *   <li>{@code personas} — selectable persona overlays, keyed by the value the client sends with a
 *       turn; each value is appended to the system message after the base prompt and before the
 *       untrusted-data rules. Only configured keys resolve, so client input never reaches the
 *       system message.</li>
 *   <li>{@code max-input-chars} — user message is truncated to this length before being sent.</li>
 *   <li>{@code max-output-tokens} — completion length cap.</li>
 *   <li>{@code temperature} — response creativity; low by default for factual grounding.</li>
 *   <li>{@code default-model} — model tag used when the client does not specify one.</li>
 *   <li>{@code max-context-chars} — total size of the retrieved venue context sent as grounding.</li>
 *   <li>{@code max-history-messages} — how many earlier turns of a chat session are replayed as
 *       memory, so a follow-up like "and the parking?" can be resolved against the conversation.</li>
 *   <li>{@code max-history-chars} — total size the replayed turns may add to the prompt.</li>
 *   <li>{@code guard} — prompt-injection guard; {@code guard.enabled} turns rejection and the
 *       output leak check on or off.</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "iqkv.catalog.venue-ask")
public record VenueAskProperties(
    @DefaultValue("true") boolean enabled,
    String systemPrompt,
    Map<String, String> personas,
    @DefaultValue("4000") int maxInputChars,
    @DefaultValue("1024") int maxOutputTokens,
    @DefaultValue("0.2") double temperature,
    String defaultModel,
    @DefaultValue("12000") int maxContextChars,
    @DefaultValue("8") int maxHistoryMessages,
    @DefaultValue("4000") int maxHistoryChars,
    Guard guard) {

  /**
   * Prompt-injection guard settings.
   *
   * @param enabled when false, messages are forwarded to the model unfiltered and replies are
   *                not checked for system-prompt leakage — a kill switch for false positives
   */
  public record Guard(@DefaultValue("true") boolean enabled) {
  }
}
