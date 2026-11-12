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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.iqkv.docintelligence.catalogservice.infrastructure.config.ObjectStorageConfigurationProperties;
import com.iqkv.docintelligence.catalogservice.shared.exception.InvalidUploadRequestException;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueAssetDtoMapper;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueAssetDtos;
import com.iqkv.docintelligence.venue.Venue;
import com.iqkv.docintelligence.venue.VenueAsset;
import com.iqkv.docintelligence.venue.VenueAssetMapper;
import com.iqkv.docintelligence.venue.VenueMapper;
import com.iqkv.foundation.tenancy.TenantContext;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-phase presigned upload for tenant venue assets, backed by MinIO and the tenant-scoped
 * {@code venue_assets} table.
 *
 * <p>Initiate validates that the named venue exists, validates the declared content type and size
 * against the configured upload policy, generates an asset id and a tenant-scoped object key
 * ({@code docintelligence/tenant/{tenantKey}/venues/{venueId}/assets/{assetId}/{fileName}}), and
 * returns a presigned PUT URL. No row is persisted until confirm.
 *
 * <p>Confirm validates that the supplied object key matches the one that would have been generated
 * for this asset and tenant, verifies the object exists in storage with a {@code statObject} HEAD
 * (so a non-existent upload cannot be confirmed), reads the actual size and content type back from
 * storage, and persists a {@code venue_assets} row with the taxonomy fields from the request.
 *
 * <p>Object keys embed the tenant key and, when present, the venue id so confirm can validate
 * ownership by exact match against the key that would be generated for the asset id and the active
 * tenant — the client cannot confirm an object it does not own. A venue-less import key omits the
 * venue segment.
 *
 * <p>Confirm publishes a {@link VenueAssetUploadedEvent}; the listener in
 * {@code infrastructure.messaging} forwards it to RabbitMQ after the transaction commits, so the
 * processing worker never reads the asset before its row is visible.
 */
@Service
@Transactional
public class VenueAssetServiceImpl implements VenueAssetService {

  private static final Logger log = LoggerFactory.getLogger(VenueAssetServiceImpl.class);
  private static final String OBJECT_KEY_PREFIX = "docintelligence/tenant/";
  private static final String OBJECT_KEY_SEGMENT = "/assets/";

  private final VenueMapper venueMapper;
  private final VenueAssetMapper assetMapper;
  private final MinioClient minioClient;
  private final MinioClient presigningMinioClient;
  private final ObjectStorageConfigurationProperties storageProps;
  private final ApplicationEventPublisher eventPublisher;

  public VenueAssetServiceImpl(final VenueMapper venueMapper,
                               final VenueAssetMapper assetMapper,
                               final MinioClient minioClient,
                               @Qualifier("presigningMinioClient") final MinioClient presigningMinioClient,
                               final ObjectStorageConfigurationProperties storageProps,
                               final ApplicationEventPublisher eventPublisher) {
    this.venueMapper = venueMapper;
    this.assetMapper = assetMapper;
    this.minioClient = minioClient;
    this.presigningMinioClient = presigningMinioClient;
    this.storageProps = storageProps;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public VenueAssetDtos.InitiateAssetUploadResponse initiateUpload(final UUID venueId,
                                                                   final VenueAssetDtos.InitiateAssetUploadRequest request,
                                                                   final UUID uploadedBy) {
    if (venueId != null) {
      requireVenue(venueId);
    }
    validateUploadPolicy(request.contentType(), request.sizeBytes());
    final UUID assetId = UUID.randomUUID();
    final String tenantKey = TenantContext.getCurrentTenant();
    final String objectKey = buildObjectKey(tenantKey, venueId, assetId, request.fileName());
    final String presignedUrl = generatePresignedPutUrl(objectKey);
    log.info("Venue asset upload initiated: assetId={}, venueId={}, tenantKey={}, uploadedBy={}",
        assetId, venueId, tenantKey, uploadedBy);
    return new VenueAssetDtos.InitiateAssetUploadResponse(
        assetId, presignedUrl, objectKey, storageProps.upload().presignedUrlExpirationMinutes());
  }

  @Override
  public VenueAssetDtos.InitiateAssetUploadResponse initiateUpload(final VenueAssetDtos.InitiateAssetUploadRequest request,
                                                                   final UUID uploadedBy) {
    return initiateUpload(null, request, uploadedBy);
  }

  @Override
  public VenueAssetDtos.VenueAssetResponse confirmUpload(final UUID venueId, final UUID assetId,
                                                         final VenueAssetDtos.ConfirmAssetUploadRequest request,
                                                         final UUID uploadedBy) {
    if (venueId != null) {
      requireVenue(venueId);
    }
    final String tenantKey = TenantContext.getCurrentTenant();
    final String expectedKey = buildObjectKey(tenantKey, venueId, assetId, request.fileName());
    if (!expectedKey.equals(request.objectKey())) {
      throw new InvalidUploadRequestException(
          "Object key does not match asset " + assetId + " for venue " + venueId);
    }

    final StatObjectResponse stat = statObject(request.objectKey());
    final String contentType = stat.contentType();
    validateUploadPolicy(contentType, stat.size());

    final VenueAsset asset = new VenueAsset();
    asset.setId(assetId);
    asset.setVenueId(venueId);
    asset.setAssetType(request.assetType());
    asset.setPhotoCategory(request.photoCategory());
    asset.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
    asset.setLabel(request.label());
    asset.setFileName(sanitizeFileName(request.fileName()));
    asset.setContentType(contentType);
    asset.setSizeBytes(stat.size());
    asset.setS3Key(request.objectKey());
    asset.setUploadedBy(uploadedBy);
    asset.setStatus(VenueAsset.STATUS_UPLOADED);
    assetMapper.insert(asset);

    // Re-read so created_at / updated_at reflect the database-computed values.
    final VenueAsset saved = assetMapper.findById(assetId)
        .orElseThrow(() -> new IllegalStateException("Inserted venue asset not found: " + assetId));

    // Set primary_photo_url on the venue when this is the first confirmed PHOTO asset.
    if (venueId != null && "PHOTO".equalsIgnoreCase(saved.getAssetType())) {
      final long photoCount = assetMapper.findPhotosByVenueId(venueId).size();
      if (photoCount == 1) {
        final String photoUrl = generatePresignedGetUrl(saved.getS3Key());
        venueMapper.updatePrimaryPhotoUrl(venueId, photoUrl);
      }
    }
    // Queued for the processing worker after this transaction commits.
    eventPublisher.publishEvent(new VenueAssetUploadedEvent(
        tenantKey, assetId, venueId, saved.getAssetType(), saved.getContentType(),
        saved.getFileName(), saved.getS3Key()));
    log.info("Venue asset confirmed: assetId={}, venueId={}, tenantKey={}, size={}",
        assetId, venueId, tenantKey, stat.size());
    return VenueAssetDtoMapper.toResponse(saved);
  }

  @Override
  public VenueAssetDtos.VenueAssetResponse confirmUpload(final UUID assetId,
                                                         final VenueAssetDtos.ConfirmAssetUploadRequest request,
                                                         final UUID uploadedBy) {
    return confirmUpload(null, assetId, request, uploadedBy);
  }

  @Override
  @Transactional(readOnly = true)
  public VenueAssetDtos.VenueAssetListResponse listAssets(final UUID venueId) {
    requireVenue(venueId);
    return toList(assetMapper.findByVenueId(venueId));
  }

  @Override
  @Transactional(readOnly = true)
  public VenueAssetDtos.VenueAssetListResponse listPhotos(final UUID venueId) {
    requireVenue(venueId);
    return toList(assetMapper.findPhotosByVenueId(venueId));
  }

  @Override
  @Transactional(readOnly = true)
  public VenueAssetDtos.VenueAssetPublicListResponse listPhotosPublic(final UUID venueId) {
    requireVenue(venueId);
    final List<VenueAsset> photos = assetMapper.findPhotosByVenueId(venueId);
    final List<VenueAssetDtos.VenueAssetPublicResponse> items = photos.stream()
        .map(a -> VenueAssetDtoMapper.toPublicResponse(a, generatePresignedGetUrlSafe(a.getS3Key())))
        .toList();
    return new VenueAssetDtos.VenueAssetPublicListResponse(items, items.size());
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public String syncPrimaryPhoto(final UUID venueId) {
    final Venue venue = venueMapper.findById(venueId).orElse(null);
    if (venue == null) {
      return null;
    }
    if (venue.getPrimaryPhotoUrl() != null) {
      return venue.getPrimaryPhotoUrl();
    }
    final List<VenueAsset> photos = assetMapper.findPhotosByVenueId(venueId);
    if (photos.isEmpty()) {
      return null;
    }
    final String photoUrl = generatePresignedGetUrlSafe(photos.get(0).getS3Key());
    if (photoUrl == null) {
      return null;
    }
    venueMapper.updatePrimaryPhotoUrl(venueId, photoUrl);
    log.info("Backfilled venue primary photo: venueId={}, tenantKey={}", venueId,
        TenantContext.getCurrentTenant());
    return photoUrl;
  }

  @Override
  @Transactional(readOnly = true)
  public VenueAssetDtos.VenueAssetResponse getAsset(final UUID venueId, final UUID assetId) {
    requireVenue(venueId);
    final VenueAsset asset = assetMapper.findById(assetId)
        .filter(found -> venueId.equals(found.getVenueId()))
        .orElseThrow(() -> new VenueAssetNotFoundException(assetId));
    return VenueAssetDtoMapper.toResponse(asset);
  }

  @Override
  @Transactional(readOnly = true)
  public VenueAssetDtos.VenueAssetPreviewUrlResponse getPreviewUrl(final UUID venueId, final UUID assetId) {
    requireVenue(venueId);
    final VenueAsset asset = assetMapper.findById(assetId)
        .filter(found -> venueId.equals(found.getVenueId()))
        .orElseThrow(() -> new VenueAssetNotFoundException(assetId));
    return new VenueAssetDtos.VenueAssetPreviewUrlResponse(
        asset.getId(),
        generatePresignedGetUrl(asset.getS3Key()),
        asset.getContentType(),
        asset.getFileName(),
        storageProps.upload().presignedUrlExpirationMinutes());
  }

  @Override
  @Transactional(readOnly = true)
  public VenueAssetDtos.VenueAssetResponse getAssetById(final UUID assetId) {
    final VenueAsset asset = assetMapper.findById(assetId)
        .orElseThrow(() -> new VenueAssetNotFoundException(assetId));
    return VenueAssetDtoMapper.toResponse(asset);
  }

  @Override
  public void deleteAsset(final UUID venueId, final UUID assetId) {
    requireVenue(venueId);
    final VenueAsset asset = assetMapper.findById(assetId)
        .filter(found -> venueId.equals(found.getVenueId()))
        .orElseThrow(() -> new VenueAssetNotFoundException(assetId));
    removeObject(asset.getS3Key());
    assetMapper.deleteById(assetId);
    log.info("Venue asset deleted: assetId={}, venueId={}, s3Key={}", assetId, venueId, asset.getS3Key());
  }

  private VenueAssetDtos.VenueAssetListResponse toList(final List<VenueAsset> assets) {
    return new VenueAssetDtos.VenueAssetListResponse(
        assets.stream().map(VenueAssetDtoMapper::toResponse).toList(),
        assets.size());
  }

  private Venue requireVenue(final UUID venueId) {
    return venueMapper.findById(venueId)
        .orElseThrow(() -> new VenueNotFoundException(venueId));
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

  private String buildObjectKey(final String tenantKey, final UUID venueId, final UUID assetId,
                                final String fileName) {
    // A venue-less import has no venue segment — the venue does not exist until the worker derives it.
    final String venueSegment = venueId == null ? "" : "/venues/" + venueId;
    return OBJECT_KEY_PREFIX + tenantKey + venueSegment + OBJECT_KEY_SEGMENT + assetId
        + "/" + sanitizeFileName(fileName);
  }

  /**
   * Reduces a client-supplied file name to a single safe path segment: any forward or backward
   * slash is replaced so the value cannot traverse outside the asset's object-key prefix.
   */
  private static String sanitizeFileName(final String fileName) {
    return fileName.replaceAll("[\\\\/]", "_");
  }

  private String generatePresignedPutUrl(final String objectKey) {
    try {
      return presigningMinioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Http.Method.PUT)
              .bucket(storageProps.bucketName())
              .object(objectKey)
              .expiry(storageProps.upload().presignedUrlExpirationMinutes(), TimeUnit.MINUTES)
              .build());
    } catch (final Exception e) {
      log.error("Failed to generate presigned URL for objectKey={}", objectKey, e);
      throw new RuntimeException("Failed to generate presigned URL", e);
    }
  }

  private String generatePresignedGetUrl(final String objectKey) {
    try {
      return presigningMinioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Http.Method.GET)
              .bucket(storageProps.bucketName())
              .object(objectKey)
              .expiry(storageProps.upload().presignedUrlExpirationMinutes(), TimeUnit.MINUTES)
              .build());
    } catch (final Exception e) {
      log.error("Failed to generate presigned GET URL for objectKey={}", objectKey, e);
      throw new RuntimeException("Failed to generate presigned GET URL", e);
    }
  }

  /**
   * Like {@link #generatePresignedGetUrl} but returns {@code null} on failure — used for lists.
   */
  private String generatePresignedGetUrlSafe(final String objectKey) {
    try {
      return generatePresignedGetUrl(objectKey);
    } catch (final Exception e) {
      return null;
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

  private void removeObject(final String objectKey) {
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder()
              .bucket(storageProps.bucketName())
              .object(objectKey)
              .build());
    } catch (final Exception e) {
      // Log and continue — the row is deleted regardless; storage can be garbage-collected later.
      log.warn("Failed to remove object from storage during asset delete: key={}", objectKey, e);
    }
  }
}
