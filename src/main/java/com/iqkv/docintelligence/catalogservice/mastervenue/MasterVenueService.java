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

package com.iqkv.docintelligence.catalogservice.mastervenue;

import java.util.List;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.mastervenue.dto.MasterVenueDtos;

/**
 * Master venue use cases, split by the two API tiers that expose them.
 *
 * <p>The {@code *PublicVenue*} methods back the tenant-facing read-only surface and return the
 * safe projection. The remaining methods back the platform-admin surface and return the full
 * record. Every method that takes an id throws {@link MasterVenueNotFoundException} when the
 * venue does not exist.
 */
public interface MasterVenueService {

  MasterVenueDtos.MasterVenueListResponse searchPublicVenues(String search, String city, String sortBy, String sortDir, int limit, int offset);

  MasterVenueDtos.MasterVenuePublicResponse getPublicVenue(UUID id);

  MasterVenueDtos.MasterVenueAssetListResponse listPublicVenueAssets(UUID id);

  MasterVenueDtos.MasterVenueAssetListResponse listPublicVenuePhotos(UUID id);

  MasterVenueDtos.AdminMasterVenueListResponse searchVenues(String search, String city, String sortBy, String sortDir, int limit, int offset);

  MasterVenueDtos.MasterVenueResponse getVenue(UUID id);

  MasterVenueDtos.AdminMasterVenueAssetListResponse listVenueAssets(UUID id);

  MasterVenueDtos.AdminMasterVenueAssetListResponse listVenuePhotos(UUID id);

  MasterVenueDtos.MasterVenueResponse createVenue(MasterVenueDtos.MasterVenueWriteRequest request);

  MasterVenueDtos.MasterVenueResponse replaceVenue(UUID id, MasterVenueDtos.MasterVenueWriteRequest request);

  MasterVenueDtos.MasterVenueResponse patchVenue(UUID id, MasterVenueDtos.MasterVenuePatchRequest request);

  void deleteVenue(UUID id);

  /**
   * Deletes every venue whose identifier appears in {@code ids}.
   *
   * <p>Idempotent: identifiers that match no venue are ignored, so repeating the call is always
   * safe. Applies as one unit — either every matching venue is removed or none is.
   *
   * @param ids the identifiers of the venues to delete
   */
  void deleteVenues(List<UUID> ids);

  /**
   * Runs a semantic (vector) search over the master venue catalog.
   *
   * @param query natural-language query string
   * @param city  optional exact city filter; {@code null} disables the filter
   * @param limit maximum number of results
   * @return similarity-ranked search response
   */
  MasterVenueDtos.SemanticSearchResponse semanticSearch(String query, String city, int limit);
}
