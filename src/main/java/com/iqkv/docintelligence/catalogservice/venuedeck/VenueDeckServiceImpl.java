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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.iqkv.docintelligence.catalogservice.infrastructure.config.ObjectStorageConfigurationProperties;
import com.iqkv.docintelligence.catalogservice.shared.exception.InvalidUploadRequestException;
import com.iqkv.docintelligence.catalogservice.venuedeck.dto.VenueDeckDtoMapper;
import com.iqkv.docintelligence.catalogservice.venuedeck.dto.VenueDeckDtos;
import com.iqkv.docintelligence.venue.VenueDocument;
import com.iqkv.docintelligence.venue.VenueDocumentMapper;
import com.iqkv.foundation.tenancy.TenantContext;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-phase presigned upload for tenant venue decks, backed by MinIO and the tenant-scoped
 * {@code venue_documents} table.
 *
 * <p>Initiate is stateless: it generates a document id and a tenant-scoped object key
 * ({@code docintelligence/tenant/{tenantKey}/venue-decks/{documentId}}), validates the declared
 * content type and size against the configured upload policy, and returns a presigned PUT URL.
 * No row is persisted until confirm.
 *
 * <p>Confirm validates that the supplied object key matches the one that would have been
 * generated for this document and tenant, verifies the object exists in storage with a
 * {@code statObject} HEAD (so a non-existent upload cannot be confirmed), reads the actual size
 * and content type back from storage, and persists a {@code venue_documents} row with status
 * {@code UPLOADED}.
 *
 * <p>Object keys embed the tenant key so confirm can validate ownership by exact match against
 * the key that would be generated for the document id and the active tenant — the client cannot
 * confirm an object it does not own.
 */
@Service
@Transactional
public class VenueDeckServiceImpl implements VenueDeckService {

  private static final Logger log = LoggerFactory.getLogger(VenueDeckServiceImpl.class);
  private static final String OBJECT_KEY_PREFIX = "docintelligence/tenant/";
  private static final String OBJECT_KEY_SEGMENT = "/venue-decks/";
  private static final String STATUS_UPLOADED = "UPLOADED";

  private final VenueDocumentMapper documentMapper;
  private final MinioClient minioClient;
  private final MinioClient presigningMinioClient;
  private final ObjectStorageConfigurationProperties storageProps;

  public VenueDeckServiceImpl(final VenueDocumentMapper documentMapper,
                              final MinioClient minioClient,
                              @Qualifier("presigningMinioClient") final MinioClient presigningMinioClient,
                              final ObjectStorageConfigurationProperties storageProps) {
    this.documentMapper = documentMapper;
    this.minioClient = minioClient;
    this.presigningMinioClient = presigningMinioClient;
    this.storageProps = storageProps;
  }

  @Override
  public VenueDeckDtos.InitiateUploadResponse initiateUpload(final VenueDeckDtos.InitiateUploadRequest request,
                                                              final UUID uploadedBy) {
    validateUploadPolicy(request.contentType(), request.sizeBytes());
    final UUID documentId = UUID.randomUUID();
    final String tenantKey = TenantContext.getCurrentTenant();
    final String objectKey = buildObjectKey(tenantKey, documentId);
    final String presignedUrl = generatePresignedPutUrl(objectKey);
    log.info("Venue deck upload initiated: documentId={}, tenantKey={}, uploadedBy={}",
        documentId, tenantKey, uploadedBy);
    return new VenueDeckDtos.InitiateUploadResponse(
        documentId, presignedUrl, objectKey, storageProps.upload().presignedUrlExpirationMinutes());
  }

  @Override
  public VenueDeckDtos.VenueDocumentResponse confirmUpload(final UUID documentId,
                                                            final VenueDeckDtos.ConfirmUploadRequest request,
                                                            final UUID uploadedBy) {
    final String tenantKey = TenantContext.getCurrentTenant();
    final String expectedKey = buildObjectKey(tenantKey, documentId);
    if (!expectedKey.equals(request.objectKey())) {
      throw new InvalidUploadRequestException(
          "Object key does not match document " + documentId + " for tenant " + tenantKey);
    }

    final StatObjectResponse stat = statObject(request.objectKey());
    final String contentType = stat.contentType();
    validateUploadPolicy(contentType, stat.size());

    final VenueDocument document = new VenueDocument();
    document.setId(documentId);
    document.setFileName(request.fileName());
    document.setContentType(contentType);
    document.setSizeBytes(stat.size());
    document.setObjectKey(request.objectKey());
    document.setStatus(STATUS_UPLOADED);
    document.setUploadedBy(uploadedBy);
    documentMapper.insert(document);

    // Re-read so created_at / updated_at reflect the database-computed values.
    final VenueDocument saved = documentMapper.findById(documentId)
        .orElseThrow(() -> new IllegalStateException("Inserted venue document not found: " + documentId));
    log.info("Venue deck confirmed: documentId={}, tenantKey={}, size={}", documentId, tenantKey, stat.size());
    return VenueDeckDtoMapper.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public VenueDeckDtos.VenueDocumentListResponse listDocuments(final int limit, final int offset) {
    final List<VenueDocument> documents = documentMapper.findAll(limit, offset);
    return new VenueDeckDtos.VenueDocumentListResponse(
        documents.stream().map(VenueDeckDtoMapper::toResponse).toList(),
        documentMapper.countAll());
  }

  @Override
  @Transactional(readOnly = true)
  public VenueDeckDtos.VenueDocumentResponse getDocument(final UUID id) {
    return VenueDeckDtoMapper.toResponse(requireDocument(id));
  }

  private VenueDocument requireDocument(final UUID id) {
    return documentMapper.findById(id)
        .orElseThrow(() -> new VenueDeckNotFoundException(id));
  }

  private void validateUploadPolicy(final String contentType, final long sizeBytes) {
    if (contentType == null || !storageProps.upload().allowedMimeTypes().contains(contentType)) {
      throw new InvalidUploadRequestException("Unsupported content type: " + contentType);
    }
    if (sizeBytes > storageProps.upload().maxFileSizeBytes()) {
      throw new InvalidUploadRequestException(
          "File size " + sizeBytes + " exceeds maximum allowed " + storageProps.upload().maxFileSizeBytes());
    }
  }

  private String buildObjectKey(final String tenantKey, final UUID documentId) {
    return OBJECT_KEY_PREFIX + tenantKey + OBJECT_KEY_SEGMENT + documentId;
  }

  private String generatePresignedPutUrl(final String objectKey) {
    try {
      return presigningMinioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(io.minio.Http.Method.PUT)
              .bucket(storageProps.bucketName())
              .object(objectKey)
              .expiry(storageProps.upload().presignedUrlExpirationMinutes(), TimeUnit.MINUTES)
              .build());
    } catch (final Exception e) {
      log.error("Failed to generate presigned URL for objectKey={}", objectKey, e);
      throw new RuntimeException("Failed to generate presigned URL", e);
    }
  }

  private StatObjectResponse statObject(final String objectKey) {
    try {
      return minioClient.statObject(
          StatObjectArgs.builder()
              .bucket(storageProps.bucketName())
              .object(objectKey)
              .build());
    } catch (final Exception e) {
      throw new InvalidUploadRequestException("Uploaded object not found in storage for key: " + objectKey);
    }
  }
}
