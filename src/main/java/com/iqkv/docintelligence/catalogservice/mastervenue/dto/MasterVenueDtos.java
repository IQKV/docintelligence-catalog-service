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

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response types for the master venue API surface.
 *
 * <p>Two projections are kept deliberately separate: the tenant-facing records carry only
 * what a member may see, while the admin records expose the operational fields
 * ({@code confidence}, {@code source}, raw {@code metadata}).
 */
public final class MasterVenueDtos {

  private MasterVenueDtos() {
  }

  /**
   * Tenant-facing view of a single master venue.
   *
   * <p>Safe projection: {@code confidence}, {@code source} and the raw {@code metadata}
   * document are internal curation fields and are deliberately absent.
   */
  public record MasterVenuePublicResponse(
      UUID id,
      String name,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Tenant-facing list item — a narrower projection than {@link MasterVenuePublicResponse}.
   */
  public record MasterVenueSummaryView(
      UUID id,
      String name,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Tenant-facing search result page.
   *
   * @param items         the page of venues, ordered by name
   * @param totalElements total number of venues matching the same filters
   */
  public record MasterVenueListResponse(List<MasterVenueSummaryView> items, long totalElements) {
  }

  /**
   * Full admin view of a master venue, including curation fields.
   */
  public record MasterVenueResponse(
      UUID id,
      String name,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String metadata,
      BigDecimal confidence,
      String source,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Admin search result page.
   *
   * @param items         the page of venues, ordered by name
   * @param totalElements total number of venues matching the same filters
   */
  public record AdminMasterVenueListResponse(List<MasterVenueResponse> items, long totalElements) {
  }

  /**
   * Write payload for both {@code POST /} (create) and {@code PUT /{id}} (full replace).
   *
   * <p>{@code metadata} and {@code confidence} are optional: a create falls back to the
   * canonical empty metadata document and full confidence.
   */
  public record MasterVenueWriteRequest(
      @NotBlank String name,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String metadata,
      BigDecimal confidence,
      @NotBlank String source) {
  }

  /**
   * Partial update payload. A {@code null} field means "leave unchanged"; there is no way to
   * clear a field with this request, which is the documented limitation of a merge-style PATCH.
   */
  public record MasterVenuePatchRequest(
      String name,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String metadata,
      BigDecimal confidence,
      String source) {
  }
}
