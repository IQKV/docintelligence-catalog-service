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

package com.iqkv.docintelligence.catalogservice.venuedeck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.infrastructure.config.ObjectStorageConfigurationProperties;
import com.iqkv.docintelligence.catalogservice.shared.exception.InvalidUploadRequestException;
import com.iqkv.docintelligence.catalogservice.venuedeck.dto.VenueDeckDtos;
import com.iqkv.docintelligence.venue.VenueDocument;
import com.iqkv.docintelligence.venue.VenueDocumentMapper;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("VenueDeckServiceImpl Unit Tests")
class VenueDeckServiceImplTest {

  private static final String TENANT_KEY = "testkey";
  private static final String ALLOWED_TYPE = "application/pdf";
  private static final long MAX_SIZE = 5_242_880L;

  @Mock
  private VenueDocumentMapper documentMapper;
  @Mock
  private MinioClient minioClient;
  @Mock
  private MinioClient presigningMinioClient;

  private VenueDeckServiceImpl service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenant(TENANT_KEY);
    final ObjectStorageConfigurationProperties props = new ObjectStorageConfigurationProperties(
        "http://localhost:9000", "", "iqkv", "pw", "catalog-assets", "us-east-1", false,
        new ObjectStorageConfigurationProperties.Upload(MAX_SIZE, 60, List.of(ALLOWED_TYPE)));
    service = new VenueDeckServiceImpl(documentMapper, minioClient, presigningMinioClient, props);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("initiate rejects an unsupported content type")
  void initiateRejectsUnsupportedContentType() throws Exception {
    final var request = new VenueDeckDtos.InitiateUploadRequest("deck.txt", "text/plain", 1024L);

    assertThatThrownBy(() -> service.initiateUpload(request, UUID.randomUUID()))
        .isInstanceOf(InvalidUploadRequestException.class)
        .hasMessageContaining("Unsupported content type");

    verifyNoStorageOrPersistence();
  }

  @Test
  @DisplayName("initiate rejects a file over the configured maximum size")
  void initiateRejectsOversize() throws Exception {
    final var request = new VenueDeckDtos.InitiateUploadRequest("deck.pdf", ALLOWED_TYPE, MAX_SIZE + 1);

    assertThatThrownBy(() -> service.initiateUpload(request, UUID.randomUUID()))
        .isInstanceOf(InvalidUploadRequestException.class)
        .hasMessageContaining("exceeds maximum");

    verifyNoStorageOrPersistence();
  }

  @Test
  @DisplayName("initiate returns a presigned URL and a tenant-scoped object key, persisting nothing")
  void initiateReturnsPresignedUrlAndPersistsNothing() throws Exception {
    when(presigningMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
        .thenReturn("http://presigned-url");
    final var request = new VenueDeckDtos.InitiateUploadRequest("deck.pdf", ALLOWED_TYPE, 1024L);

    final var response = service.initiateUpload(request, UUID.randomUUID());

    assertThat(response.documentId()).isNotNull();
    assertThat(response.presignedUploadUrl()).isEqualTo("http://presigned-url");
    assertThat(response.objectKey()).isEqualTo("docintelligence/tenant/" + TENANT_KEY + "/venue-decks/" + response.documentId());
    assertThat(response.expiresInMinutes()).isEqualTo(60);
    verify(presigningMinioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    verify(documentMapper, never()).insert(any());
  }

  @Test
  @DisplayName("confirm rejects an object key that does not match the document and tenant")
  void confirmRejectsMismatchedObjectKey() throws Exception {
    final UUID documentId = UUID.randomUUID();
    final var request = new VenueDeckDtos.ConfirmUploadRequest(
        "docintelligence/tenant/other/venue-decks/" + documentId, "deck.pdf");

    assertThatThrownBy(() -> service.confirmUpload(documentId, request, UUID.randomUUID()))
        .isInstanceOf(InvalidUploadRequestException.class)
        .hasMessageContaining("does not match document");

    verify(minioClient, never()).statObject(any(StatObjectArgs.class));
    verify(documentMapper, never()).insert(any());
  }

  @Test
  @DisplayName("confirm rejects when the object is not found in storage")
  void confirmRejectsMissingObject() throws Exception {
    final UUID documentId = UUID.randomUUID();
    final String objectKey = "docintelligence/tenant/" + TENANT_KEY + "/venue-decks/" + documentId;
    when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(new RuntimeException("NoSuchKey"));
    final var request = new VenueDeckDtos.ConfirmUploadRequest(objectKey, "deck.pdf");

    assertThatThrownBy(() -> service.confirmUpload(documentId, request, UUID.randomUUID()))
        .isInstanceOf(InvalidUploadRequestException.class)
        .hasMessageContaining("not found in storage");

    verify(documentMapper, never()).insert(any());
  }

  @Test
  @DisplayName("confirm persists a document with status UPLOADED using the size and type read from storage")
  void confirmPersistsUploadedDocument() throws Exception {
    final UUID documentId = UUID.randomUUID();
    final UUID uploadedBy = UUID.randomUUID();
    final String objectKey = "docintelligence/tenant/" + TENANT_KEY + "/venue-decks/" + documentId;

    final StatObjectResponse stat = org.mockito.Mockito.mock(StatObjectResponse.class);
    when(stat.size()).thenReturn(2048L);
    when(stat.contentType()).thenReturn(ALLOWED_TYPE);
    when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);

    final VenueDocument saved = new VenueDocument();
    saved.setId(documentId);
    saved.setFileName("deck.pdf");
    saved.setContentType(ALLOWED_TYPE);
    saved.setSizeBytes(2048L);
    saved.setObjectKey(objectKey);
    saved.setStatus("UPLOADED");
    saved.setUploadedBy(uploadedBy);
    saved.setCreatedAt(Instant.now());
    saved.setUpdatedAt(Instant.now());
    when(documentMapper.findById(documentId)).thenReturn(Optional.of(saved));

    final var request = new VenueDeckDtos.ConfirmUploadRequest(objectKey, "deck.pdf");
    final var response = service.confirmUpload(documentId, request, uploadedBy);

    final ArgumentCaptor<VenueDocument> captor = ArgumentCaptor.forClass(VenueDocument.class);
    verify(documentMapper).insert(captor.capture());
    final VenueDocument persisted = captor.getValue();
    assertThat(persisted.getStatus()).isEqualTo("UPLOADED");
    assertThat(persisted.getContentType()).isEqualTo(ALLOWED_TYPE);
    assertThat(persisted.getSizeBytes()).isEqualTo(2048L);
    assertThat(persisted.getObjectKey()).isEqualTo(objectKey);
    assertThat(persisted.getUploadedBy()).isEqualTo(uploadedBy);

    assertThat(response.id()).isEqualTo(documentId);
    assertThat(response.status()).isEqualTo("UPLOADED");
    assertThat(response.sizeBytes()).isEqualTo(2048L);
  }

  @Test
  @DisplayName("getDocument throws when the document does not exist")
  void getDocumentThrowsWhenNotFound() {
    final UUID id = UUID.randomUUID();
    when(documentMapper.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDocument(id))
        .isInstanceOf(VenueDeckNotFoundException.class);
  }

  @Test
  @DisplayName("listDocuments maps the page and the total count")
  void listDocumentsMapsPageAndCount() {
    final VenueDocument doc = new VenueDocument();
    doc.setId(UUID.randomUUID());
    doc.setFileName("deck.pdf");
    doc.setContentType(ALLOWED_TYPE);
    doc.setSizeBytes(1024L);
    doc.setObjectKey("docintelligence/tenant/" + TENANT_KEY + "/venue-decks/" + doc.getId());
    doc.setStatus("UPLOADED");
    doc.setUploadedBy(UUID.randomUUID());
    when(documentMapper.findAll(20, 0)).thenReturn(List.of(doc));
    when(documentMapper.countAll()).thenReturn(1L);

    final var response = service.listDocuments(20, 0);

    assertThat(response.items()).hasSize(1);
    assertThat(response.totalElements()).isEqualTo(1L);
    assertThat(response.items().get(0).fileName()).isEqualTo("deck.pdf");
  }

  private void verifyNoStorageOrPersistence() throws Exception {
    verify(minioClient, never()).statObject(any(StatObjectArgs.class));
    verify(presigningMinioClient, never()).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    verify(documentMapper, never()).insert(any());
  }
}
