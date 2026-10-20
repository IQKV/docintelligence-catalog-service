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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.mastervenue.dto.MasterVenueDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MasterVenueServiceImplTest {

  @Mock
  private MasterVenueMapper masterVenueMapper;

  private MasterVenueServiceImpl masterVenueService;

  @BeforeEach
  void setUp() {
    masterVenueService = new MasterVenueServiceImpl(masterVenueMapper);
  }

  @Test
  @DisplayName("getPublicVenue throws when the venue does not exist")
  void shouldThrowWhenVenueNotFound() {
    // Arrange
    final var id = UUID.randomUUID();
    when(masterVenueMapper.findById(id)).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> masterVenueService.getPublicVenue(id))
        .isInstanceOf(MasterVenueNotFoundException.class)
        .hasMessageContaining(id.toString());
  }

  @Test
  @DisplayName("deleteVenue throws when the venue does not exist")
  void shouldNotDeleteMissingVenue() {
    // Arrange
    final var id = UUID.randomUUID();
    when(masterVenueMapper.findById(id)).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> masterVenueService.deleteVenue(id))
        .isInstanceOf(MasterVenueNotFoundException.class);
  }

  @Test
  @DisplayName("createVenue generates an id and applies the documented defaults")
  void shouldApplyDefaultsOnCreate() {
    // Arrange
    final var request = new MasterVenueDtos.MasterVenueWriteRequest(
        "Grand Hall", "1 Main St", "Kyiv", null, null, null, null, "platform_seed");
    when(masterVenueMapper.findById(any(UUID.class))).thenAnswer(invocation -> {
      final MasterVenue venue = new MasterVenue();
      venue.setId(invocation.getArgument(0, UUID.class));
      return Optional.of(venue);
    });

    // Act
    final var response = masterVenueService.createVenue(request);

    // Assert
    final var captor = ArgumentCaptor.forClass(MasterVenue.class);
    verify(masterVenueMapper).insert(captor.capture());
    final MasterVenue inserted = captor.getValue();
    assertThat(inserted.getId()).isNotNull();
    assertThat(inserted.getMetadata()).isEqualTo("{\"_schema_version\":1}");
    assertThat(inserted.getConfidence()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(inserted.getSource()).isEqualTo("platform_seed");
    assertThat(response.id()).isEqualTo(inserted.getId());
  }

  @Test
  @DisplayName("patchVenue overwrites only the fields present in the request")
  void shouldPatchOnlyProvidedFields() {
    // Arrange
    final var id = UUID.randomUUID();
    final MasterVenue existing = new MasterVenue();
    existing.setId(id);
    existing.setName("Old name");
    existing.setAddress("Old address");
    existing.setSource("platform_seed");
    when(masterVenueMapper.findById(id)).thenReturn(Optional.of(existing));
    final var request = new MasterVenueDtos.MasterVenuePatchRequest(
        null, "New address", null, null, null, null, null, null);

    // Act
    masterVenueService.patchVenue(id, request);

    // Assert
    final var captor = ArgumentCaptor.forClass(MasterVenue.class);
    verify(masterVenueMapper).update(captor.capture());
    assertThat(captor.getValue().getAddress()).isEqualTo("New address");
    assertThat(captor.getValue().getName()).isEqualTo("Old name");
  }

  @Test
  @DisplayName("searchPublicVenues maps rows to the safe projection and reports the total")
  void shouldSearchPublicVenues() {
    // Arrange
    final MasterVenue venue = new MasterVenue();
    venue.setId(UUID.randomUUID());
    venue.setName("Grand Hall");
    venue.setCity("Kyiv");
    venue.setMetadata("{\"_schema_version\":1}");
    venue.setConfidence(new BigDecimal("0.75"));
    when(masterVenueMapper.search("grand", "Kyiv", 20, 0)).thenReturn(List.of(venue));
    when(masterVenueMapper.countSearch("grand", "Kyiv")).thenReturn(1L);

    // Act
    final var response = masterVenueService.searchPublicVenues("grand", "Kyiv", 20, 0);

    // Assert
    assertThat(response.totalElements()).isEqualTo(1L);
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).name()).isEqualTo("Grand Hall");
    assertThat(response.items().get(0).city()).isEqualTo("Kyiv");
  }
}
