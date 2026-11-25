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

package com.iqkv.docintelligence.catalogservice.venueask;

import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venueask.dto.VenueAskDtos;

/**
 * Retrieval-augmented question answering over the tenant's own venue data.
 *
 * <p>Answers are grounded in retrieved venue data and returned together with the sources they
 * cite. The active tenant schema scopes every lookup; a venue id from another tenant resolves to
 * nothing.
 */
public interface VenueAskService {

  /**
   * Answers a question scoped to one venue, grounded in that venue's profile, source text chunks
   * and assets.
   *
   * @throws com.iqkv.docintelligence.catalogservice.venue.VenueNotFoundException when the venue
   *     does not exist in the active tenant schema
   * @throws EmptyMessageException        when the question carries no visible text
   * @throws PromptInjectionException     when the question is rejected by the prompt-injection guard
   */
  VenueAskDtos.VenueAskResponse askVenue(UUID venueId, VenueAskDtos.VenueAskRequest request, UUID userId);

  /**
   * The full retrieved context of one venue — the same data the ask endpoint grounds on, returned
   * without any model call so the UI can preload and inspect it.
   *
   * @throws com.iqkv.docintelligence.catalogservice.venue.VenueNotFoundException when the venue
   *     does not exist in the active tenant schema
   */
  VenueAskDtos.VenueContextResponse getVenueContext(UUID venueId, UUID userId);

  /**
   * Answers a question scoped to the ranked list / grid of venues.
   *
   * @throws EmptyMessageException    when the question carries no visible text
   * @throws PromptInjectionException when the question is rejected by the prompt-injection guard
   */
  VenueAskDtos.VenueListAskResponse askVenues(VenueAskDtos.VenueListAskRequest request, UUID userId);
}
