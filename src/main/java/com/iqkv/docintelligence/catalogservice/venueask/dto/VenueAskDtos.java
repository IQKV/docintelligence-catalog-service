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

package com.iqkv.docintelligence.catalogservice.venueask.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueAssetDtos;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueTextContentDtos;

/**
 * Request and response records for the venue ask / RAG surface, grouped in one place per the
 * project convention.
 *
 * <p>The ask endpoints retrieve the tenant's own venue data (profile, source text chunks, assets)
 * and ground the model's answer in it. Every response therefore carries the retrieved sources
 * alongside the answer, so the UI can show citations and act on them.
 */
public final class VenueAskDtos {

  private VenueAskDtos() {
  }

  /**
   * Question scoped to one venue.
   *
   * @param question   the natural-language question
   * @param maxSources optional cap on how many source chunks are retrieved and cited
   */
  public record VenueAskRequest(
      @NotBlank String question,
      Integer maxSources) {
  }

  /**
   * One retrieved source chunk cited in an answer.
   *
   * @param sourceTextId the {@code venue_text_contents} row the snippet came from
   * @param assetId      the {@code venue_assets} row the text was extracted from, when known
   * @param label        the human-readable source name (title or file name), when known
   * @param snippet      the verbatim excerpt used as grounding
   * @param similarity   cosine similarity in [0, 1] when the chunk was ranked by vector search
   */
  public record VenueAskSource(
      UUID sourceTextId,
      UUID assetId,
      String label,
      String snippet,
      double similarity) {
  }

  /**
   * Answer grounded in one venue's data.
   *
   * @param venueId  the venue the question was scoped to
   * @param answer   the model's answer, or a fixed message when {@code answered} is false
   * @param sources  the retrieved source chunks, always populated when any exist
   * @param answered false when the AI feature is off, the model is unreachable, or no context was
   *                 found — the endpoint never errors on those, it degrades
   * @param model    the model tag used, when a model call was made
   */
  public record VenueAskResponse(
      UUID venueId,
      String answer,
      List<VenueAskSource> sources,
      boolean answered,
      String model) {
  }

  /**
   * Question scoped to the list / grid of venues.
   *
   * @param question the natural-language question
   * @param city     optional explicit city filter; overrides the city the query planner derives
   * @param limit    optional cap on how many venues are retrieved and cited
   */
  public record VenueListAskRequest(
      @NotBlank String question,
      String city,
      Integer limit) {
  }

  /**
   * One venue cited in a list-scope answer.
   */
  public record VenueListAskVenue(
      UUID id,
      String name,
      String city,
      String primaryPhotoUrl,
      double similarity) {
  }

  /**
   * Answer grounded in the ranked list of venues.
   *
   * @param answer         the model's answer, or a fixed message when {@code answered} is false
   * @param venues         the ranked venues the answer was grounded in, always populated
   * @param appliedFilters hard filters the query planner applied, or an all-null filter set
   * @param answered       false when the AI feature is off or the model is unreachable
   * @param model          the model tag used, when a model call was made
   */
  public record VenueListAskResponse(
      String answer,
      List<VenueListAskVenue> venues,
      VenueDtos.VenueSemanticSearchFilters appliedFilters,
      boolean answered,
      String model) {
  }

  /**
   * The full context bundle of one venue — the same retrieval the ask endpoint grounds on,
   * returned without any model call so the UI can preload and inspect it.
   *
   * @param venue   the venue's safe projection
   * @param sources the venue's text-content rows (documents / extracted text)
   * @param assets  the venue's assets (gallery, documents)
   */
  public record VenueContextResponse(
      VenueDtos.VenuePublicResponse venue,
      VenueTextContentDtos.VenueTextContentListResponse sources,
      VenueAssetDtos.VenueAssetListResponse assets) {
  }
}
