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

import com.iqkv.docintelligence.venue.Venue;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Static translation between {@link Venue} rows and the admin venue DTOs.
 */
public final class VenueDtoMapper {

  /**
   * Canonical metadata document used when a create request omits {@code metadata}.
   * Mirrors the column default so an inserted row is always valid JSONB.
   */
  private static final String DEFAULT_METADATA = "{\"_schema_version\":1}";

  private VenueDtoMapper() {
  }

  public static VenueDtos.VenueResponse toResponse(final Venue venue) {
    return new VenueDtos.VenueResponse(
        venue.getId(),
        venue.getName(),
        venue.getAddress(),
        venue.getCity(),
        venue.getLocationLat(),
        venue.getLocationLng(),
        venue.getMetadata(),
        venue.getConfidence(),
        venue.getSource(),
        venue.getMasterVenueId(),
        venue.getCreatedAt(),
        venue.getUpdatedAt());
  }

  public static VenueDtos.VenueSummaryView toSummaryView(final Venue venue) {
    return new VenueDtos.VenueSummaryView(
        venue.getId(),
        venue.getName(),
        venue.getCity(),
        venue.getLocationLat(),
        venue.getLocationLng(),
        venue.getMasterVenueId(),
        venue.getCreatedAt(),
        venue.getUpdatedAt());
  }

  /**
   * Builds the tenant-facing safe projection of a venue — curation fields are dropped and the
   * optional {@code masterVenueId} relation key is kept.
   */
  public static VenueDtos.VenuePublicResponse toPublicResponse(final Venue venue) {
    return new VenueDtos.VenuePublicResponse(
        venue.getId(),
        venue.getName(),
        venue.getAddress(),
        venue.getCity(),
        venue.getLocationLat(),
        venue.getLocationLng(),
        venue.getMasterVenueId(),
        venue.getCreatedAt(),
        venue.getUpdatedAt());
  }

  /**
   * Builds a new row from a create request, applying an id and the documented defaults.
   * The id is generated here because the schema has no database-side default.
   */
  public static Venue toEntity(final VenueDtos.VenueWriteRequest request) {
    final Venue venue = new Venue();
    venue.setId(UUID.randomUUID());
    applyWrite(venue, request);
    return venue;
  }

  /**
   * Applies a full replace: every request field overwrites the current value, with the
   * documented defaults for the two optional curation fields.
   */
  public static void applyWrite(final Venue venue, final VenueDtos.VenueWriteRequest request) {
    venue.setName(request.name());
    venue.setAddress(request.address());
    venue.setCity(request.city());
    venue.setLocationLat(request.locationLat());
    venue.setLocationLng(request.locationLng());
    venue.setMetadata(request.metadata() != null ? request.metadata() : DEFAULT_METADATA);
    venue.setConfidence(request.confidence() != null ? request.confidence() : BigDecimal.ONE);
    venue.setSource(request.source());
    venue.setMasterVenueId(request.masterVenueId());
  }

  /**
   * Applies a partial update: only non-null request fields overwrite the current value.
   */
  public static void applyPatch(final Venue venue, final VenueDtos.VenuePatchRequest request) {
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
    if (request.masterVenueId() != null) {
      venue.setMasterVenueId(request.masterVenueId());
    }
  }
}
