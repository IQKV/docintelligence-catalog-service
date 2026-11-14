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

package com.iqkv.docintelligence.catalogservice.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.infrastructure.config.MasterVenueAiSearchProperties;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit tests for the degradation contract of {@link VenueEnrichmentAssistantService}: when the
 * chat model is unavailable or the feature flag is off, the service must return {@code found=false}
 * rather than propagating an error.
 *
 * <p>The LLM-backed happy path is not exercised here — it requires a live Ollama instance and is
 * covered by integration tests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VenueEnrichmentAssistantService degradation contract")
class VenueEnrichmentAssistantServiceTest {

  @Mock
  private VenueService venueService;

  @Mock
  private VenueTextContentService venueTextContentService;

  @Mock
  private ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

  private static final UUID VENUE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private static final VenueDtos.VenueFieldExtractionRequest REQUEST =
      new VenueDtos.VenueFieldExtractionRequest(
          "capacity", "max_total", "Maximum capacity", "number", null, null, null);

  @Test
  @DisplayName("returns not-found when feature flag is disabled")
  void returnsNotFoundWhenDisabled() {
    // Arrange
    final var properties = new MasterVenueAiSearchProperties(false);
    final var service = new VenueEnrichmentAssistantService(venueService, venueTextContentService, chatClientBuilderProvider, properties);

    // Act
    final var response = service.extract(VENUE_ID, REQUEST);

    // Assert
    assertThat(response.found()).isFalse();
    assertThat(response.value()).isNull();
    assertThat(response.group()).isEqualTo("capacity");
  }

  @Test
  @DisplayName("returns not-found when no chat client is available")
  void returnsNotFoundWhenNoChatClient() {
    // Arrange
    final var properties = new MasterVenueAiSearchProperties(true);
    when(chatClientBuilderProvider.getIfAvailable()).thenReturn(null);
    final var service = new VenueEnrichmentAssistantService(venueService, venueTextContentService, chatClientBuilderProvider, properties);

    // Act
    final var response = service.extract(VENUE_ID, REQUEST);

    // Assert
    assertThat(response.found()).isFalse();
    assertThat(response.value()).isNull();
  }
}
