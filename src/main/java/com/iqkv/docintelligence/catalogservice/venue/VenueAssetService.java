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

import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueAssetDtos;

/**
 * Asset operations over a tenant venue record.
 *
 * <p>Implements the two-phase presigned upload flow for venue assets: {@link #initiateUpload} mints
 * a tenant-scoped object key and a presigned PUT URL (no row is persisted), then
 * {@link #confirmUpload} validates the object key, verifies the object exists in storage, and
 * persists a {@code venue_assets} row. List, photos and detail reads are tenant-scoped (the active
 * tenant schema isolates rows).
 *
 * <p>The owning venue is optional. The venue-scoped overloads take it from the request path and
 * throw {@link VenueNotFoundException} when that venue does not exist; the venue-less overloads are
 * the import flow — a document is uploaded first and {@code docintelligence-catalog-processing-worker}
 * derives the venue from it, so the confirmed row starts with {@code venueId == null} and status
 * {@code UPLOADED}. {@link #getAsset} additionally throws {@link VenueAssetNotFoundException} when
 * the asset does not exist or is not owned by the named venue.
 */
public interface VenueAssetService {

  VenueAssetDtos.InitiateAssetUploadResponse initiateUpload(UUID venueId,
                                                            VenueAssetDtos.InitiateAssetUploadRequest request,
                                                            UUID uploadedBy);

  /**
   * Venue-less variant of {@link #initiateUpload} for the import flow — the object key omits the
   * venue segment because no venue exists yet.
   */
  VenueAssetDtos.InitiateAssetUploadResponse initiateUpload(VenueAssetDtos.InitiateAssetUploadRequest request,
                                                            UUID uploadedBy);

  VenueAssetDtos.VenueAssetResponse confirmUpload(UUID venueId, UUID assetId,
                                                  VenueAssetDtos.ConfirmAssetUploadRequest request,
                                                  UUID uploadedBy);

  /**
   * Venue-less variant of {@link #confirmUpload} for the import flow — the persisted row has a
   * {@code null} {@code venueId} and is queued for the processing worker.
   */
  VenueAssetDtos.VenueAssetResponse confirmUpload(UUID assetId,
                                                  VenueAssetDtos.ConfirmAssetUploadRequest request,
                                                  UUID uploadedBy);

  VenueAssetDtos.VenueAssetListResponse listAssets(UUID venueId);

  VenueAssetDtos.VenueAssetListResponse listPhotos(UUID venueId);

  VenueAssetDtos.VenueAssetPublicListResponse listPhotosPublic(UUID venueId);

  /**
   * Populates the venue's denormalised {@code primary_photo_url} when it is empty but the venue
   * already has at least one PHOTO asset — a self-heal for photos that reached a venue without
   * passing the venue-scoped confirm path (for example a venue-less import the processing worker
   * later linked). Mints a presigned GET URL for the first photo (by display order) and stores it,
   * so subsequent reads return it from the column. A no-op when the venue already has a URL or has
   * no photo.
   *
   * @param venueId the venue to reconcile
   * @return the effective primary photo URL, or {@code null} when the venue has no photo
   */
  String syncPrimaryPhoto(UUID venueId);

  VenueAssetDtos.VenueAssetResponse getAsset(UUID venueId, UUID assetId);

  /**
   * Mints a presigned GET URL for the stored object of one venue-owned asset, so the caller can
   * render it inline (images and PDFs alike). Throws {@link VenueNotFoundException} when the venue
   * does not exist and {@link VenueAssetNotFoundException} when the asset does not exist or is not
   * owned by the named venue.
   */
  VenueAssetDtos.VenueAssetPreviewUrlResponse getPreviewUrl(UUID venueId, UUID assetId);

  /**
   * Reads one asset by id alone, without requiring a venue — the import flow polls this to watch
   * the asset move from {@code UPLOADED} to {@code EXTRACTED}. Throws
   * {@link VenueAssetNotFoundException} when the asset does not exist.
   */
  VenueAssetDtos.VenueAssetResponse getAssetById(UUID assetId);

  void deleteAsset(UUID venueId, UUID assetId);
}
