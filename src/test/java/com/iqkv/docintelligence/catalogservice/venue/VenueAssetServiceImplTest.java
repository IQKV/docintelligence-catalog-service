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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.infrastructure.config.ObjectStorageConfigurationProperties;
import com.iqkv.docintelligence.catalogservice.shared.exception.InvalidUploadRequestException;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueAssetDtos;
import com.iqkv.docintelligence.venue.Venue;
import com.iqkv.docintelligence.venue.VenueAsset;
import com.iqkv.docintelligence.venue.VenueAssetMapper;
import com.iqkv.docintelligence.venue.VenueMapper;
import com.iqkv.foundation.tenancy.TenantContext;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("VenueAssetServiceImpl Unit Tests")
class VenueAssetServiceImplTest {

  private static final String TENANT_KEY = "testkey";
  private static final String ALLOWED_TYPE = "application/pdf";
  private static final long MAX_SIZE = 5_242_880L;
  private static final UUID VENUE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Mock
  private VenueMapper venueMapper;
  @Mock
  private VenueAssetMapper assetMapper;
  @Mock
  private MinioClient minioClient;
  @Mock
  private MinioClient presigningMinioClient;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private VenueAssetServiceImpl service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenant(TENANT_KEY);
    final ObjectStorageConfigurationProperties props = new ObjectStorageConfigurationProperties(
        "http://localhost:9000", "", "iqkv", "pw", "catalog-assets", "us-east-1", false,
        new ObjectStorageConfigurationProperties.Upload(MAX_SIZE, 60, List.of(ALLOWED_TYPE)));
    service = new VenueAssetServiceImpl(
        venueMapper, assetMapper, minioClient, presigningMinioClient, props, eventPublisher);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private void stubVenue() {
    final Venue venue = new Venue();
    venue.setId(VENUE_ID);
    when(venueMapper.findById(VENUE_ID)).thenReturn(Optional.of(venue));
  }

  private static VenueAsset asset(final UUID id, final UUID venueId) {
    final VenueAsset asset = new VenueAsset();
    asset.setId(id);
    asset.setVenueId(venueId);
    asset.setAssetType("PHOTO");
    asset.setDisplayOrder(0);
    asset.setFileName("asset.pdf");
    asset.setContentType(ALLOWED_TYPE);
    asset.setSizeBytes(2048L);
    asset.setS3Key("docintelligence/tenant/" + TENANT_KEY + "/venues/" + venueId + "/assets/" + id + "/asset.pdf");
    asset.setUploadedBy(UUID.randomUUID());
    asset.setCreatedAt(Instant.now());
    asset.setUpdatedAt(Instant.now());
    return asset;
  }

  @Test
  @DisplayName("initiate throws when the venue does not exist")
  void initiateThrowsWhenVenueMissing() {
    when(venueMapper.findById(VENUE_ID)).thenReturn(Optional.empty());
    final var request = new VenueAssetDtos.InitiateAssetUploadRequest("asset.pdf", ALLOWED_TYPE, 1024L);

    assertThatThrownBy(() -> service.initiateUpload(VENUE_ID, request, UUID.randomUUID()))
        .isInstanceOf(VenueNotFoundException.class);
  }

  @Test
  @DisplayName("initiate rejects an unsupported content type")
  void initiateRejectsUnsupportedContentType() throws Exception {
    stubVenue();
    final var request = new VenueAssetDtos.InitiateAssetUploadRequest("asset.txt", "text/plain", 1024L);

    assertThatThrownBy(() -> service.initiateUpload(VENUE_ID, request, UUID.randomUUID()))
        .isInstanceOf(InvalidUploadRequestException.class)
        .hasMessageContaining("Unsupported content type");

    verifyNoStorageOrPersistence();
  }

  @Test
  @DisplayName("initiate rejects a file over the configured maximum size")
  void initiateRejectsOversize() throws Exception {
    stubVenue();
    final var request = new VenueAssetDtos.InitiateAssetUploadRequest("asset.pdf", ALLOWED_TYPE, MAX_SIZE + 1);

    assertThatThrownBy(() -> service.initiateUpload(VENUE_ID, request, UUID.randomUUID()))
        .isInstanceOf(InvalidUploadRequestException.class)
        .hasMessageContaining("exceeds maximum");

    verifyNoStorageOrPersistence();
  }

  @Test
  @DisplayName("initiate returns a presigned URL and a venue-scoped object key, persisting nothing")
  void initiateReturnsPresignedUrlAndPersistsNothing() throws Exception {
    stubVenue();
    when(presigningMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
        .thenReturn("http://presigned-url");
    final var request = new VenueAssetDtos.InitiateAssetUploadRequest("asset.pdf", ALLOWED_TYPE, 1024L);

    final var response = service.initiateUpload(VENUE_ID, request, UUID.randomUUID());

    assertThat(response.assetId()).isNotNull();
    assertThat(response.presignedUploadUrl()).isEqualTo("http://presigned-url");
    assertThat(response.objectKey()).isEqualTo(
        "docintelligence/tenant/" + TENANT_KEY + "/venues/" + VENUE_ID
            + "/assets/" + response.assetId() + "/asset.pdf");
    assertThat(response.expiresInMinutes()).isEqualTo(60);
    verify(presigningMinioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    verify(assetMapper, never()).insert(any());
  }

  @Test
  @DisplayName("initiate sanitises a file name so the object key cannot traverse")
  void initiateSanitisesFileName() throws Exception {
    stubVenue();
    when(presigningMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
        .thenReturn("http://presigned-url");
    final var request = new VenueAssetDtos.InitiateAssetUploadRequest("../../etc/passwd", ALLOWED_TYPE, 1024L);

    final var response = service.initiateUpload(VENUE_ID, request, UUID.randomUUID());

    assertThat(response.objectKey()).endsWith("/assets/" + response.assetId() + "/.._.._etc_passwd");
  }

  @Test
  @DisplayName("initiate without a venue returns a venue-less object key and reads no venue")
  void initiateWithoutVenueOmitsVenueSegment() throws Exception {
    when(presigningMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
        .thenReturn("http://presigned-url");
    final var request = new VenueAssetDtos.InitiateAssetUploadRequest("asset.pdf", ALLOWED_TYPE, 1024L);

    final var response = service.initiateUpload(request, UUID.randomUUID());

    assertThat(response.objectKey()).isEqualTo(
        "docintelligence/tenant/" + TENANT_KEY + "/assets/" + response.assetId() + "/asset.pdf");
    verify(venueMapper, never()).findById(any(UUID.class));
  }

  @Test
  @DisplayName("confirm without a venue persists a venue-less UPLOADED asset and publishes the event")
  void confirmWithoutVenuePersistsAndPublishes() throws Exception {
    final UUID assetId = UUID.randomUUID();
    final String objectKey = "docintelligence/tenant/" + TENANT_KEY + "/assets/" + assetId + "/asset.pdf";

    final StatObjectResponse stat = org.mockito.Mockito.mock(StatObjectResponse.class);
    when(stat.size()).thenReturn(2048L);
    when(stat.contentType()).thenReturn(ALLOWED_TYPE);
    when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);

    when(assetMapper.findById(assetId)).thenReturn(Optional.of(asset(assetId, null)));

    final var request = new VenueAssetDtos.ConfirmAssetUploadRequest(
        objectKey, "asset.pdf", "DOCUMENT", null, 0, null);
    final var response = service.confirmUpload(assetId, request, UUID.randomUUID());

    final ArgumentCaptor<VenueAsset> captor = ArgumentCaptor.forClass(VenueAsset.class);
    verify(assetMapper).insert(captor.capture());
    assertThat(captor.getValue().getVenueId()).isNull();
    assertThat(captor.getValue().getStatus()).isEqualTo(VenueAsset.STATUS_UPLOADED);
    assertThat(response.venueId()).isNull();

    verify(eventPublisher).publishEvent(any(VenueAssetUploadedEvent.class));
  }

  @Test
  @DisplayName("confirm rejects an object key that does not match the asset and venue")
  void confirmRejectsMismatchedObjectKey() throws Exception {
    stubVenue();
    final UUID assetId = UUID.randomUUID();
    final var request = new VenueAssetDtos.ConfirmAssetUploadRequest(
        "docintelligence/tenant/other/venues/" + VENUE_ID + "/assets/" + assetId + "/asset.pdf",
        "asset.pdf", "PHOTO", null, 0, null);

    assertThatThrownBy(() -> service.confirmUpload(VENUE_ID, assetId, request, UUID.randomUUID()))
        .isInstanceOf(InvalidUploadRequestException.class)
        .hasMessageContaining("does not match asset");

    verify(minioClient, never()).statObject(any(StatObjectArgs.class));
    verify(assetMapper, never()).insert(any());
  }

  @Test
  @DisplayName("confirm rejects when the object is not found in storage")
  void confirmRejectsMissingObject() throws Exception {
    stubVenue();
    final UUID assetId = UUID.randomUUID();
    final String objectKey = "docintelligence/tenant/" + TENANT_KEY + "/venues/" + VENUE_ID
        + "/assets/" + assetId + "/asset.pdf";
    when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(new RuntimeException("NoSuchKey"));
    final var request = new VenueAssetDtos.ConfirmAssetUploadRequest(objectKey, "asset.pdf", "PHOTO", null, 0, null);

    assertThatThrownBy(() -> service.confirmUpload(VENUE_ID, assetId, request, UUID.randomUUID()))
        .isInstanceOf(InvalidUploadRequestException.class)
        .hasMessageContaining("not found in storage");

    verify(assetMapper, never()).insert(any());
  }

  @Test
  @DisplayName("confirm persists the asset with the taxonomy and the size and type read from storage")
  void confirmPersistsAsset() throws Exception {
    stubVenue();
    final UUID assetId = UUID.randomUUID();
    final UUID uploadedBy = UUID.randomUUID();
    final String objectKey = "docintelligence/tenant/" + TENANT_KEY + "/venues/" + VENUE_ID
        + "/assets/" + assetId + "/asset.pdf";

    final StatObjectResponse stat = org.mockito.Mockito.mock(StatObjectResponse.class);
    when(stat.size()).thenReturn(2048L);
    when(stat.contentType()).thenReturn(ALLOWED_TYPE);
    when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);

    when(assetMapper.findById(assetId)).thenReturn(Optional.of(asset(assetId, VENUE_ID)));

    final var request = new VenueAssetDtos.ConfirmAssetUploadRequest(
        objectKey, "asset.pdf", "PHOTO", "Exterior", 3, "Front facade");
    final var response = service.confirmUpload(VENUE_ID, assetId, request, uploadedBy);

    final ArgumentCaptor<VenueAsset> captor = ArgumentCaptor.forClass(VenueAsset.class);
    verify(assetMapper).insert(captor.capture());
    final VenueAsset persisted = captor.getValue();
    assertThat(persisted.getVenueId()).isEqualTo(VENUE_ID);
    assertThat(persisted.getAssetType()).isEqualTo("PHOTO");
    assertThat(persisted.getPhotoCategory()).isEqualTo("Exterior");
    assertThat(persisted.getDisplayOrder()).isEqualTo(3);
    assertThat(persisted.getLabel()).isEqualTo("Front facade");
    assertThat(persisted.getContentType()).isEqualTo(ALLOWED_TYPE);
    assertThat(persisted.getSizeBytes()).isEqualTo(2048L);
    assertThat(persisted.getS3Key()).isEqualTo(objectKey);
    assertThat(persisted.getUploadedBy()).isEqualTo(uploadedBy);

    assertThat(response.id()).isEqualTo(assetId);
    assertThat(response.venueId()).isEqualTo(VENUE_ID);
  }

  @Test
  @DisplayName("getAsset throws when the asset does not exist")
  void getAssetThrowsWhenNotFound() {
    stubVenue();
    final UUID assetId = UUID.randomUUID();
    when(assetMapper.findById(assetId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getAsset(VENUE_ID, assetId))
        .isInstanceOf(VenueAssetNotFoundException.class);
  }

  @Test
  @DisplayName("getAsset throws when the asset belongs to another venue")
  void getAssetRejectsForeignAsset() {
    stubVenue();
    final UUID assetId = UUID.randomUUID();
    when(assetMapper.findById(assetId)).thenReturn(Optional.of(asset(assetId, UUID.randomUUID())));

    assertThatThrownBy(() -> service.getAsset(VENUE_ID, assetId))
        .isInstanceOf(VenueAssetNotFoundException.class);
  }

  @Test
  @DisplayName("getAsset throws when the venue does not exist")
  void getAssetThrowsWhenVenueMissing() {
    when(venueMapper.findById(VENUE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getAsset(VENUE_ID, UUID.randomUUID()))
        .isInstanceOf(VenueNotFoundException.class);
  }

  @Test
  @DisplayName("listAssets maps the page and the total count")
  void listAssetsMapsPageAndCount() {
    stubVenue();
    final VenueAsset asset = asset(UUID.randomUUID(), VENUE_ID);
    when(assetMapper.findByVenueId(VENUE_ID)).thenReturn(List.of(asset));

    final var response = service.listAssets(VENUE_ID);

    assertThat(response.items()).hasSize(1);
    assertThat(response.totalElements()).isEqualTo(1L);
    assertThat(response.items().get(0).fileName()).isEqualTo("asset.pdf");
  }

  @Test
  @DisplayName("listPhotos maps the photo subset")
  void listPhotosMapsPhotos() {
    stubVenue();
    final VenueAsset photo = asset(UUID.randomUUID(), VENUE_ID);
    when(assetMapper.findPhotosByVenueId(VENUE_ID)).thenReturn(List.of(photo));

    final var response = service.listPhotos(VENUE_ID);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).assetType()).isEqualTo("PHOTO");
  }

  private void verifyNoStorageOrPersistence() throws Exception {
    verify(minioClient, never()).statObject(any(StatObjectArgs.class));
    verify(presigningMinioClient, never()).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    verify(assetMapper, never()).insert(any());
  }

  @Test
  @DisplayName("deleteAsset throws when the venue does not exist")
  void deleteAssetThrowsWhenVenueMissing() {
    when(venueMapper.findById(VENUE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteAsset(VENUE_ID, UUID.randomUUID()))
        .isInstanceOf(VenueNotFoundException.class);
    verify(assetMapper, never()).deleteById(any(UUID.class));
  }

  @Test
  @DisplayName("deleteAsset throws when the asset does not exist")
  void deleteAssetThrowsWhenAssetMissing() {
    stubVenue();
    final UUID assetId = UUID.randomUUID();
    when(assetMapper.findById(assetId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteAsset(VENUE_ID, assetId))
        .isInstanceOf(VenueAssetNotFoundException.class);
    verify(assetMapper, never()).deleteById(any(UUID.class));
  }

  @Test
  @DisplayName("deleteAsset throws when the asset belongs to another venue")
  void deleteAssetRejectsForeignAsset() {
    stubVenue();
    final UUID assetId = UUID.randomUUID();
    when(assetMapper.findById(assetId)).thenReturn(Optional.of(asset(assetId, UUID.randomUUID())));

    assertThatThrownBy(() -> service.deleteAsset(VENUE_ID, assetId))
        .isInstanceOf(VenueAssetNotFoundException.class);
    verify(assetMapper, never()).deleteById(any(UUID.class));
  }

  @Test
  @DisplayName("deleteAsset removes the object from storage and deletes the row")
  void deleteAssetRemovesObjectAndRow() throws Exception {
    stubVenue();
    final UUID assetId = UUID.randomUUID();
    when(assetMapper.findById(assetId)).thenReturn(Optional.of(asset(assetId, VENUE_ID)));

    service.deleteAsset(VENUE_ID, assetId);

    verify(minioClient).removeObject(any(io.minio.RemoveObjectArgs.class));
    verify(assetMapper).deleteById(assetId);
  }

  @Test
  @DisplayName("deleteAsset still deletes the row when storage removal fails")
  void deleteAssetDeletesRowEvenIfStorageFails() throws Exception {
    stubVenue();
    final UUID assetId = UUID.randomUUID();
    when(assetMapper.findById(assetId)).thenReturn(Optional.of(asset(assetId, VENUE_ID)));
    doThrow(new RuntimeException("MinIO unavailable"))
        .when(minioClient).removeObject(any(io.minio.RemoveObjectArgs.class));

    service.deleteAsset(VENUE_ID, assetId);

    verify(assetMapper).deleteById(assetId);
  }
}
