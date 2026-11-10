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

package com.iqkv.docintelligence.catalogservice.venue.dto;

import com.iqkv.docintelligence.venue.VenueAsset;

/**
 * Static mapper between the {@link VenueAsset} domain model and the {@link VenueAssetDtos}
 * response records. No Spring bean — stateless conversion only.
 */
public final class VenueAssetDtoMapper {

  private VenueAssetDtoMapper() {
  }

  public static VenueAssetDtos.VenueAssetResponse toResponse(final VenueAsset asset) {
    return new VenueAssetDtos.VenueAssetResponse(
        asset.getId(),
        asset.getVenueId(),
        asset.getAssetType(),
        asset.getPhotoCategory(),
        asset.getDisplayOrder(),
        asset.getLabel(),
        asset.getFileName(),
        asset.getContentType(),
        asset.getSizeBytes(),
        asset.getS3Key(),
        asset.getUploadedBy(),
        asset.getCreatedAt(),
        asset.getUpdatedAt());
  }

  public static VenueAssetDtos.VenueAssetPublicResponse toPublicResponse(final VenueAsset asset,
                                                                         final String sourceUrl) {
    return new VenueAssetDtos.VenueAssetPublicResponse(
        asset.getId(),
        asset.getVenueId(),
        asset.getAssetType(),
        asset.getPhotoCategory(),
        asset.getDisplayOrder(),
        asset.getLabel(),
        asset.getFileName(),
        asset.getContentType(),
        asset.getSizeBytes(),
        sourceUrl,
        asset.getUploadedBy(),
        asset.getCreatedAt(),
        asset.getUpdatedAt());
  }
}
