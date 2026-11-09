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

package com.iqkv.docintelligence.catalogservice.mastervenue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.iqkv.docintelligence.venue.MasterVenue;
import com.iqkv.docintelligence.venue.MasterVenueMapper;
import com.iqkv.docintelligence.venue.VenueEmbeddingText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

@ExtendWith(MockitoExtension.class)
class MasterVenueEmbeddingServiceTest {

  @Mock
  private EmbeddingModel embeddingModel;

  @Mock
  private MasterVenueMapper masterVenueMapper;

  private MasterVenueEmbeddingService service;

  @BeforeEach
  void setUp() {
    service = new MasterVenueEmbeddingService(embeddingModel, masterVenueMapper);
  }

  @Test
  @DisplayName("embed builds text from the searchable fields and persists the vector")
  void shouldEmbedAndPersistVector() {
    // Arrange
    final var venue = new MasterVenue();
    venue.setId(UUID.randomUUID());
    venue.setName("Grand Hall");
    venue.setAddress("1 Main St");
    venue.setCity("Kyiv");
    venue.setMetadata("{\"_schema_version\":1}");
    final float[] vector = new float[]{0.1f, 0.2f};
    when(embeddingModel.embed(anyString())).thenReturn(vector);

    // Act
    final boolean result = service.embed(venue);

    // Assert
    assertThat(result).isTrue();
    verify(masterVenueMapper).updateEmbedding(venue.getId(), vector);
  }

  @Test
  @DisplayName("embed swallows Ollama failures so a write is never blocked")
  void shouldSwallowEmbeddingFailure() {
    // Arrange
    final var venue = new MasterVenue();
    venue.setId(UUID.randomUUID());
    venue.setName("Grand Hall");
    when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("ollama down"));

    // Act
    final boolean result = service.embed(venue);

    // Assert
    assertThat(result).isFalse();
    verify(masterVenueMapper, never()).updateEmbedding(any(UUID.class), any(float[].class));
  }

  @Test
  @DisplayName("VenueEmbeddingText joins name, address, city and metadata")
  void shouldBuildEmbeddingText() {
    // Arrange
    final var venue = new MasterVenue();
    venue.setName("Grand Hall");
    venue.setAddress("1 Main St");
    venue.setCity("Kyiv");
    venue.setMetadata("{\"_schema_version\":1}");

    // Act & Assert
    assertThat(VenueEmbeddingText.build(venue))
        .isEqualTo("Grand Hall. 1 Main St. Kyiv. {\"_schema_version\":1}");
  }
}
