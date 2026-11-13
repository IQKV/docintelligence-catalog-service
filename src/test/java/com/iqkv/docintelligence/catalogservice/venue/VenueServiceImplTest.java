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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;
import com.iqkv.docintelligence.venue.Venue;
import com.iqkv.docintelligence.venue.VenueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("VenueServiceImpl Unit Tests")
class VenueServiceImplTest {

  @Mock
  private VenueMapper venueMapper;
  @Mock
  private VenueAssetService venueAssetService;
  @Mock
  private VenueEmbeddingService embeddingService;
  @Mock
  private VenueSemanticSearchService semanticSearchService;
  @Mock
  private tools.jackson.databind.ObjectMapper objectMapper;

  private VenueServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new VenueServiceImpl(venueMapper, venueAssetService, embeddingService, semanticSearchService, objectMapper);
  }

  @Test
  @DisplayName("searchVenues maps the page and the total count")
  void searchMapsPageAndCount() {
    final Venue venue = savedVenue(UUID.randomUUID(), "Grand Hall");
    when(venueMapper.search("grand", "Paris", "name", "asc", 20, 0)).thenReturn(List.of(venue));
    when(venueMapper.countSearch("grand", "Paris")).thenReturn(1L);

    final var response = service.searchVenues("grand", "Paris", "name", "asc", 20, 0);

    assertThat(response.items()).hasSize(1);
    assertThat(response.totalElements()).isEqualTo(1L);
    assertThat(response.items().get(0).name()).isEqualTo("Grand Hall");
  }

  @Test
  @DisplayName("searchVenues resolves the primary photo from the venue's photo assets")
  void searchResolvesPrimaryPhoto() {
    final UUID id = UUID.randomUUID();
    when(venueMapper.search("grand", "Paris", "name", "asc", 20, 0))
        .thenReturn(List.of(savedVenue(id, "Grand Hall")));
    when(venueMapper.countSearch("grand", "Paris")).thenReturn(1L);
    when(venueAssetService.resolvePrimaryPhotoUrl(id)).thenReturn("https://cdn.example.com/grand-hall.jpg");

    final var response = service.searchVenues("grand", "Paris", "name", "asc", 20, 0);

    assertThat(response.items().get(0).primaryPhotoUrl()).isEqualTo("https://cdn.example.com/grand-hall.jpg");
  }

  @Test
  @DisplayName("searchVenues returns a null primary photo when the venue has no photo")
  void searchWithoutPhotoReturnsNullPrimaryPhoto() {
    final UUID id = UUID.randomUUID();
    when(venueMapper.search("grand", "Paris", "name", "asc", 20, 0))
        .thenReturn(List.of(savedVenue(id, "Grand Hall")));
    when(venueMapper.countSearch("grand", "Paris")).thenReturn(1L);

    final var response = service.searchVenues("grand", "Paris", "name", "asc", 20, 0);

    assertThat(response.items().get(0).primaryPhotoUrl()).isNull();
  }

  @Test
  @DisplayName("getPublicVenue resolves the primary photo from the venue's photo assets")
  void getPublicVenueResolvesPrimaryPhoto() {
    final UUID id = UUID.randomUUID();
    when(venueMapper.findById(id)).thenReturn(Optional.of(savedVenue(id, "Grand Hall")));
    when(venueAssetService.resolvePrimaryPhotoUrl(id)).thenReturn("https://cdn.example.com/grand-hall.jpg");

    final var response = service.getPublicVenue(id);

    assertThat(response.primaryPhotoUrl()).isEqualTo("https://cdn.example.com/grand-hall.jpg");
  }

  @Test
  @DisplayName("getVenue returns the venue when found")
  void getVenueReturnsWhenFound() {
    final UUID id = UUID.randomUUID();
    when(venueMapper.findById(id)).thenReturn(Optional.of(savedVenue(id, "Grand Hall")));

    final var response = service.getVenue(id);

    assertThat(response.id()).isEqualTo(id);
    assertThat(response.name()).isEqualTo("Grand Hall");
  }

  @Test
  @DisplayName("getVenue throws when the venue does not exist")
  void getVenueThrowsWhenNotFound() {
    final UUID id = UUID.randomUUID();
    when(venueMapper.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getVenue(id))
        .isInstanceOf(VenueNotFoundException.class);
  }

  @Test
  @DisplayName("createVenue inserts and re-reads the row")
  void createInsertsAndReReads() {
    when(venueMapper.findById(any(UUID.class))).thenReturn(Optional.of(savedVenue(UUID.randomUUID(), "Grand Hall")));

    final var response = service.createVenue(new VenueDtos.VenueWriteRequest(
        "Grand Hall", null, "1 Rue", "Paris", new BigDecimal("48.856614"), new BigDecimal("2.352221"),
        null, null, "MANUAL", null));

    verify(venueMapper).insert(any(Venue.class));
    assertThat(response.name()).isEqualTo("Grand Hall");
  }

  @Test
  @DisplayName("replaceVenue applies a full replace and re-reads")
  void replaceAppliesFullReplace() {
    final UUID id = UUID.randomUUID();
    final Venue existing = savedVenue(id, "Old Name");
    when(venueMapper.findById(id)).thenReturn(Optional.of(existing));

    service.replaceVenue(id, new VenueDtos.VenueWriteRequest(
        "Grand Hall", null, "1 Rue", "Paris", null, null, null, null, "MANUAL", null));

    verify(venueMapper).update(existing);
    assertThat(existing.getName()).isEqualTo("Grand Hall");
    assertThat(existing.getSource()).isEqualTo("MANUAL");
  }

  @Test
  @DisplayName("replaceVenue throws when the venue does not exist")
  void replaceThrowsWhenNotFound() {
    final UUID id = UUID.randomUUID();
    when(venueMapper.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.replaceVenue(id, new VenueDtos.VenueWriteRequest(
        "Grand Hall", null, null, null, null, null, null, null, "MANUAL", null)))
        .isInstanceOf(VenueNotFoundException.class);
    verify(venueMapper, never()).update(any(Venue.class));
  }

  @Test
  @DisplayName("patchVenue applies only the provided fields")
  void patchAppliesOnlyProvidedFields() {
    final UUID id = UUID.randomUUID();
    final Venue existing = savedVenue(id, "Old Name");
    when(venueMapper.findById(id)).thenReturn(Optional.of(existing));

    service.patchVenue(id, new VenueDtos.VenuePatchRequest(
        "New Name", null, null, null, null, null, null, null, null, null));

    verify(venueMapper).update(existing);
    assertThat(existing.getName()).isEqualTo("New Name");
    assertThat(existing.getCity()).isEqualTo("Paris"); // unchanged
  }

  @Test
  @DisplayName("deleteVenue deletes when the venue exists")
  void deleteDeletesWhenExists() {
    final UUID id = UUID.randomUUID();
    when(venueMapper.findById(id)).thenReturn(Optional.of(savedVenue(id, "Grand Hall")));

    service.deleteVenue(id);

    verify(venueMapper).deleteById(eq(id));
  }

  @Test
  @DisplayName("deleteVenue throws when the venue does not exist")
  void deleteThrowsWhenNotFound() {
    final UUID id = UUID.randomUUID();
    when(venueMapper.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteVenue(id))
        .isInstanceOf(VenueNotFoundException.class);
    verify(venueMapper, never()).deleteById(any(UUID.class));
  }

  @Test
  @DisplayName("semanticSearchPublic maps the ranked items to the tenant safe projection")
  void semanticSearchPublicMapsToSafeProjection() {
    final UUID id = UUID.randomUUID();
    final VenueDtos.VenueSemanticSearchResult adminResult = new VenueDtos.VenueSemanticSearchResult(
        id, "Grand Hall", "1 Rue", "Paris",
        new BigDecimal("48.856614"), new BigDecimal("2.352221"),
        "https://cdn.example.com/grand-hall.jpg",
        "{\"_schema_version\":1}", new BigDecimal("0.90"), "MANUAL", null,
        Instant.now(), Instant.now(), 0.87);
    final VenueDtos.VenueSemanticSearchFilters filters = new VenueDtos.VenueSemanticSearchFilters(
        "Paris", List.of("hotel"), null, null, null, List.of(), null);
    when(semanticSearchService.search("rooftop", "Paris", 20)).thenReturn(
        new VenueDtos.VenueSemanticSearchResponse(List.of(adminResult), 1L, filters));

    final var response = service.semanticSearchPublic("rooftop", "Paris", 20);

    assertThat(response.items()).hasSize(1);
    assertThat(response.totalElements()).isEqualTo(1L);
    assertThat(response.appliedFilters()).isSameAs(filters);
    final var item = response.items().get(0);
    assertThat(item.id()).isEqualTo(id);
    assertThat(item.primaryPhotoUrl()).isEqualTo("https://cdn.example.com/grand-hall.jpg");
    assertThat(item.similarity()).isEqualTo(0.87);
  }

  private Venue savedVenue(final UUID id, final String name) {
    final Venue venue = new Venue();
    venue.setId(id);
    venue.setName(name);
    venue.setAddress("1 Rue");
    venue.setCity("Paris");
    venue.setLocationLat(new BigDecimal("48.856614"));
    venue.setLocationLng(new BigDecimal("2.352221"));
    venue.setMetadata("{\"_schema_version\":1}");
    venue.setConfidence(new BigDecimal("1.00"));
    venue.setSource("MANUAL");
    venue.setCreatedAt(Instant.now());
    venue.setUpdatedAt(Instant.now());
    return venue;
  }
}
