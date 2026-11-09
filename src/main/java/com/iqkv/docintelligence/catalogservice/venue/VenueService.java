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

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;

/**
 * Platform-admin use cases over tenant venue records.
 *
 * <p>Every method that takes an id throws {@link VenueNotFoundException} when the venue does not
 * exist in the active tenant schema. The active tenant schema is set by the controller from the
 * {@code {tenantKey}} path variable before these methods are called.
 */
public interface VenueService {

  VenueDtos.VenueListResponse searchVenues(String search, String city, String sortBy, String sortDir,
                                           int limit, int offset);

  VenueDtos.VenueResponse getVenue(UUID id);

  VenueDtos.VenueResponse createVenue(VenueDtos.VenueWriteRequest request);

  VenueDtos.VenueResponse replaceVenue(UUID id, VenueDtos.VenueWriteRequest request);

  VenueDtos.VenueResponse patchVenue(UUID id, VenueDtos.VenuePatchRequest request);

  void deleteVenue(UUID id);

  VenueDtos.VenueListResponse searchPublicVenues(String search, String city, String sortBy, String sortDir,
                                                 int limit, int offset);

  VenueDtos.VenuePublicResponse getPublicVenue(UUID id);

  VenueDtos.VenuePublicResponse createPublicVenue(VenueDtos.VenuePublicWriteRequest request);

  VenueDtos.VenuePublicResponse replacePublicVenue(UUID id, VenueDtos.VenuePublicWriteRequest request);

  VenueDtos.VenuePublicResponse patchPublicVenue(UUID id, VenueDtos.VenuePublicPatchRequest request);

  void deletePublicVenue(UUID id);

  void deletePublicVenues(java.util.List<UUID> ids);

  VenueDtos.VenueSemanticSearchResponse semanticSearch(String query, String city, int limit);
}
