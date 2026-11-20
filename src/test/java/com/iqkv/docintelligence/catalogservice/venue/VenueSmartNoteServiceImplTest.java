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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueAssetDtos;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueSmartNoteDtos;
import com.iqkv.docintelligence.venue.VenueAsset;
import com.iqkv.docintelligence.venue.VenueAssetMapper;
import com.iqkv.docintelligence.venue.VenueSmartNote;
import com.iqkv.docintelligence.venue.VenueSmartNoteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VenueSmartNoteServiceImplTest {

  @Mock
  private VenueSmartNoteMapper mapper;

  @Mock
  private VenueAssetMapper assetMapper;

  private VenueSmartNoteServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new VenueSmartNoteServiceImpl(mapper, assetMapper);
  }

  @Test
  @DisplayName("create persists a note with the caller's authorId and generated id")
  void createPersistsWithAuthorAndGeneratedId() {
    // Arrange
    final UUID venueId = UUID.randomUUID();
    final UUID authorId = UUID.randomUUID();
    final var request = new VenueSmartNoteDtos.VenueSmartNoteCreateRequest(
        venueId, "Lunch menu", "<p>Ask about vegan</p>", "#fff9c4",
        List.of("menu", "vegan"), null, 5, true);
    when(mapper.findById(any(UUID.class))).thenReturn(Optional.of(note(UUID.randomUUID())));

    // Act
    service.create(request, authorId);

    // Assert
    final ArgumentCaptor<VenueSmartNote> captor = ArgumentCaptor.forClass(VenueSmartNote.class);
    verify(mapper).insert(captor.capture());
    final VenueSmartNote saved = captor.getValue();
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getVenueId()).isEqualTo(venueId);
    assertThat(saved.getAuthorId()).isEqualTo(authorId);
    assertThat(saved.getTitle()).isEqualTo("Lunch menu");
    assertThat(saved.getColor()).isEqualTo("#fff9c4");
    assertThat(saved.getTags()).containsExactly("menu", "vegan");
    assertThat(saved.getSortOrder()).isEqualTo(5);
    assertThat(saved.isPinned()).isTrue();
  }

  @Test
  @DisplayName("create applies defaults when sortOrder and pinned are null")
  void createAppliesDefaults() {
    // Arrange
    final var request = new VenueSmartNoteDtos.VenueSmartNoteCreateRequest(
        UUID.randomUUID(), null, "note", null, null, null, null, null);
    when(mapper.findById(any(UUID.class))).thenReturn(Optional.of(note(UUID.randomUUID())));

    // Act
    service.create(request, UUID.randomUUID());

    // Assert
    final ArgumentCaptor<VenueSmartNote> captor = ArgumentCaptor.forClass(VenueSmartNote.class);
    verify(mapper).insert(captor.capture());
    assertThat(captor.getValue().getSortOrder()).isZero();
    assertThat(captor.getValue().isPinned()).isFalse();
  }

  @Test
  @DisplayName("getById throws when the note does not exist")
  void getByIdThrowsWhenNotFound() {
    final UUID id = UUID.randomUUID();
    when(mapper.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getById(id))
        .isInstanceOf(VenueSmartNoteNotFoundException.class);
  }

  @Test
  @DisplayName("listByVenue delegates direction and pinned filter to the mapper")
  void listByVenueDelegatesDirectionAndPinned() {
    final UUID venueId = UUID.randomUUID();
    when(mapper.findByVenueId(venueId, "DESC", true)).thenReturn(List.of());

    service.listByVenue(venueId, "DESC", true);

    verify(mapper).findByVenueId(venueId, "DESC", true);
  }

  @Test
  @DisplayName("patch with a single field calls only the matching update method")
  void patchCallsOnlyRelevantUpdate() {
    final UUID id = UUID.randomUUID();
    when(mapper.findById(id)).thenReturn(Optional.of(note(id)));
    final var request = new VenueSmartNoteDtos.VenueSmartNotePatchRequest(
        null, null, "#e8f5e9", null, null, null, null);

    service.patch(id, request);

    verify(mapper).updateColor(id, "#e8f5e9");
    verify(mapper, never()).updateTitle(eq(id), any());
    verify(mapper, never()).updateContent(eq(id), any());
    verify(mapper, never()).updateTags(eq(id), any());
    verify(mapper, never()).updateSortOrder(eq(id), any(int.class));
    verify(mapper, never()).updatePinned(eq(id), any(boolean.class));
  }

  @Test
  @DisplayName("patch with all fields set calls every update method")
  void patchAllFields() {
    final UUID id = UUID.randomUUID();
    final UUID assetId = UUID.randomUUID();
    when(mapper.findById(id)).thenReturn(Optional.of(note(id)));
    final var request = new VenueSmartNoteDtos.VenueSmartNotePatchRequest(
        "title", "content", "red", List.of("a"), assetId, 2, true);

    service.patch(id, request);

    verify(mapper).updateTitle(id, "title");
    verify(mapper).updateContent(id, "content");
    verify(mapper).updateColor(id, "red");
    verify(mapper).updateTags(id, List.of("a"));
    verify(mapper).updateLinkedAssetId(id, assetId);
    verify(mapper).updateSortOrder(id, 2);
    verify(mapper).updatePinned(id, true);
  }

  @Test
  @DisplayName("patch with an all-null body calls no update method but still returns the row")
  void patchNoOp() {
    final UUID id = UUID.randomUUID();
    when(mapper.findById(id)).thenReturn(Optional.of(note(id)));
    final var request = new VenueSmartNoteDtos.VenueSmartNotePatchRequest(
        null, null, null, null, null, null, null);

    final var result = service.patch(id, request);

    assertThat(result.id()).isEqualTo(id);
    verify(mapper, never()).updateTitle(eq(id), any());
    verify(mapper, never()).updateContent(eq(id), any());
  }

  @Test
  @DisplayName("patch throws when the note does not exist")
  void patchThrowsWhenNotFound() {
    final UUID id = UUID.randomUUID();
    when(mapper.findById(id)).thenReturn(Optional.empty());
    final var request = new VenueSmartNoteDtos.VenueSmartNotePatchRequest("t", null, null, null, null, null, null);

    assertThatThrownBy(() -> service.patch(id, request))
        .isInstanceOf(VenueSmartNoteNotFoundException.class);
  }

  @Test
  @DisplayName("delete removes the row when it exists")
  void deleteRemovesRow() {
    final UUID id = UUID.randomUUID();
    when(mapper.findById(id)).thenReturn(Optional.of(note(id)));

    service.delete(id);

    verify(mapper).deleteById(id);
  }

  @Test
  @DisplayName("delete throws when the note does not exist")
  void deleteThrowsWhenNotFound() {
    final UUID id = UUID.randomUUID();
    when(mapper.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(id))
        .isInstanceOf(VenueSmartNoteNotFoundException.class);
    verify(mapper, never()).deleteById(any());
  }

  @Test
  @DisplayName("setPinned calls updatePinned with the given value")
  void setPinnedCallsUpdatePinned() {
    final UUID id = UUID.randomUUID();
    when(mapper.findById(id)).thenReturn(Optional.of(note(id)));

    service.setPinned(id, true);

    verify(mapper).updatePinned(id, true);
  }

  @Test
  @DisplayName("setPinned throws when the note does not exist")
  void setPinnedThrowsWhenNotFound() {
    final UUID id = UUID.randomUUID();
    when(mapper.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.setPinned(id, false))
        .isInstanceOf(VenueSmartNoteNotFoundException.class);
    verify(mapper, never()).updatePinned(any(), anyBoolean());
  }

  @Test
  @DisplayName("getLinkedAsset returns the linked asset when it exists")
  void getLinkedAssetReturnsAsset() {
    final UUID id = UUID.randomUUID();
    final UUID assetId = UUID.randomUUID();
    final VenueSmartNote note = note(id);
    note.setLinkedAssetId(assetId);
    when(mapper.findById(id)).thenReturn(Optional.of(note));
    final VenueAsset asset = new VenueAsset();
    asset.setId(assetId);
    when(assetMapper.findById(assetId)).thenReturn(Optional.of(asset));

    final VenueAssetDtos.VenueAssetResponse result = service.getLinkedAsset(id);

    assertThat(result.id()).isEqualTo(assetId);
  }

  @Test
  @DisplayName("getLinkedAsset throws when the note has no linked asset")
  void getLinkedAssetThrowsWhenNoneLinked() {
    final UUID id = UUID.randomUUID();
    when(mapper.findById(id)).thenReturn(Optional.of(note(id)));

    assertThatThrownBy(() -> service.getLinkedAsset(id))
        .isInstanceOf(VenueAssetNotFoundException.class);
    verify(assetMapper, never()).findById(any());
  }

  @Test
  @DisplayName("getLinkedAsset throws when the linked asset row was deleted")
  void getLinkedAssetThrowsWhenAssetDeleted() {
    final UUID id = UUID.randomUUID();
    final UUID assetId = UUID.randomUUID();
    final VenueSmartNote note = note(id);
    note.setLinkedAssetId(assetId);
    when(mapper.findById(id)).thenReturn(Optional.of(note));
    when(assetMapper.findById(assetId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getLinkedAsset(id))
        .isInstanceOf(VenueAssetNotFoundException.class);
  }

  @Test
  @DisplayName("countByVenue returns the mapper count")
  void countByVenue() {
    final UUID venueId = UUID.randomUUID();
    when(mapper.countByVenueId(venueId)).thenReturn(3L);

    final var result = service.countByVenue(venueId);

    assertThat(result.venueId()).isEqualTo(venueId);
    assertThat(result.total()).isEqualTo(3L);
  }

  private VenueSmartNote note(final UUID id) {
    final VenueSmartNote note = new VenueSmartNote();
    note.setId(id);
    note.setVenueId(UUID.randomUUID());
    note.setAuthorId(UUID.randomUUID());
    note.setSortOrder(0);
    note.setPinned(false);
    note.setCreatedAt(Instant.now());
    note.setUpdatedAt(Instant.now());
    return note;
  }
}
