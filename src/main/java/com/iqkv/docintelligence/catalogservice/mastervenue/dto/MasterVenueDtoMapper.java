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

package com.iqkv.docintelligence.catalogservice.mastervenue.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.mastervenue.MasterVenue;

/**
 * Static translation between {@link MasterVenue} rows and the master venue DTOs.
 */
public final class MasterVenueDtoMapper {

  /**
   * Canonical metadata document used when a create request omits {@code metadata}.
   * Mirrors the column default so an inserted row is always valid JSONB.
   */
  private static final String DEFAULT_METADATA = "{\"_schema_version\":1}";

  private MasterVenueDtoMapper() {
  }

  public static MasterVenueDtos.MasterVenueResponse toResponse(final MasterVenue venue) {
    return new MasterVenueDtos.MasterVenueResponse(
        venue.getId(),
        venue.getName(),
        venue.getAddress(),
        venue.getCity(),
        venue.getLocationLat(),
        venue.getLocationLng(),
        venue.getMetadata(),
        venue.getConfidence(),
        venue.getSource(),
        venue.getCreatedAt(),
        venue.getUpdatedAt()
    );
  }

  public static MasterVenueDtos.MasterVenuePublicResponse toPublicResponse(final MasterVenue venue) {
    return new MasterVenueDtos.MasterVenuePublicResponse(
        venue.getId(),
        venue.getName(),
        venue.getAddress(),
        venue.getCity(),
        venue.getLocationLat(),
        venue.getLocationLng(),
        venue.getCreatedAt(),
        venue.getUpdatedAt()
    );
  }

  public static MasterVenueDtos.MasterVenueSummaryView toSummaryView(final MasterVenue venue) {
    return new MasterVenueDtos.MasterVenueSummaryView(
        venue.getId(),
        venue.getName(),
        venue.getCity(),
        venue.getLocationLat(),
        venue.getLocationLng(),
        venue.getCreatedAt(),
        venue.getUpdatedAt()
    );
  }

  /**
   * Builds a new row from a create request, applying an id and the documented defaults.
   * The id is generated here because the schema has no database-side default.
   */
  public static MasterVenue toEntity(final MasterVenueDtos.MasterVenueWriteRequest request) {
    final MasterVenue venue = new MasterVenue();
    venue.setId(UUID.randomUUID());
    applyWrite(venue, request);
    return venue;
  }

  /**
   * Applies a full replace: every request field overwrites the current value, with the
   * documented defaults for the two optional curation fields.
   */
  public static void applyWrite(final MasterVenue venue, final MasterVenueDtos.MasterVenueWriteRequest request) {
    venue.setName(request.name());
    venue.setAddress(request.address());
    venue.setCity(request.city());
    venue.setLocationLat(request.locationLat());
    venue.setLocationLng(request.locationLng());
    venue.setMetadata(request.metadata() != null ? request.metadata() : DEFAULT_METADATA);
    venue.setConfidence(request.confidence() != null ? request.confidence() : BigDecimal.ONE);
    venue.setSource(request.source());
  }

  /**
   * Applies a partial update: only non-null request fields overwrite the current value.
   */
  public static void applyPatch(final MasterVenue venue, final MasterVenueDtos.MasterVenuePatchRequest request) {
    if (request.name() != null) {
      venue.setName(request.name());
    }
    if (request.address() != null) {
      venue.setAddress(request.address());
    }
    if (request.city() != null) {
      venue.setCity(request.city());
    }
    if (request.locationLat() != null) {
      venue.setLocationLat(request.locationLat());
    }
    if (request.locationLng() != null) {
      venue.setLocationLng(request.locationLng());
    }
    if (request.metadata() != null) {
      venue.setMetadata(request.metadata());
    }
    if (request.confidence() != null) {
      venue.setConfidence(request.confidence());
    }
    if (request.source() != null) {
      venue.setSource(request.source());
    }
  }
}
