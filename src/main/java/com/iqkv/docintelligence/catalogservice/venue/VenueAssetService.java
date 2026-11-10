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
 * <p>Every method takes the owning venue id from the request path and throws
 * {@link VenueNotFoundException} when that venue does not exist. {@link #getAsset} additionally
 * throws {@link VenueAssetNotFoundException} when the asset does not exist or is not owned by the
 * named venue.
 */
public interface VenueAssetService {

  VenueAssetDtos.InitiateAssetUploadResponse initiateUpload(UUID venueId,
                                                            VenueAssetDtos.InitiateAssetUploadRequest request,
                                                            UUID uploadedBy);

  VenueAssetDtos.VenueAssetResponse confirmUpload(UUID venueId, UUID assetId,
                                                  VenueAssetDtos.ConfirmAssetUploadRequest request,
                                                  UUID uploadedBy);

  VenueAssetDtos.VenueAssetListResponse listAssets(UUID venueId);

  VenueAssetDtos.VenueAssetListResponse listPhotos(UUID venueId);

  VenueAssetDtos.VenueAssetResponse getAsset(UUID venueId, UUID assetId);
}
