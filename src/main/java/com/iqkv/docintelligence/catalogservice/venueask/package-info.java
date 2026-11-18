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

/**
 * Catalog-native AI chat and retrieval-augmented question answering over the tenant's own venue
 * data.
 *
 * <p>This slice wires the venue UI's chat surface to the catalog: it retrieves the tenant's venue
 * profile, source text chunks ({@code venue_text_contents}) and assets, grounds the model's answer
 * in that data, and returns the cited sources so a conversation stays actionable.
 *
 * <p>Two REST surfaces sit on top of it:
 * <ul>
 *   <li>{@code VenueAskRestResource} — one-shot, sourced answers over a single venue
 *       ({@code /venues/{venueId}/ask}, {@code /venues/{venueId}/context}) or over the ranked list
 *       of venues ({@code /venues/ask}).</li>
 *   <li>{@code VenueChatRestResource} — persisted conversational sessions
 *       ({@code /chat}, {@code /chat/sessions}) whose request and response shapes mirror the AI
 *       chat client contract, so the UI swaps only the endpoint it calls.</li>
 * </ul>
 *
 * <p>Chat sessions and messages live in the tenant schema ({@code venue_chat_sessions},
 * {@code venue_chat_messages}) with their models and MyBatis mappers kept local to this slice, so
 * the shared {@code com.iqkv:docintelligence-venue} artifact stays free of this feature.
 *
 * <p>Every AI path degrades instead of failing: when the feature flag is off, the Ollama chat
 * model is unreachable, or no context is retrieved, answers fall back to {@code answered=false}
 * with whatever sources were retrieved, and a chat turn falls back to a fixed reply. Empty input
 * and prompt-injection attempts are rejected by {@link
 * com.iqkv.docintelligence.catalogservice.venueask.PromptGuard}; questions in any language are
 * accepted, since the venue catalogue is multilingual.
 */
package com.iqkv.docintelligence.catalogservice.venueask;
