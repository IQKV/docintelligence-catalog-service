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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response records for the platform-admin tenant venue surface, grouped in one
 * place per the project convention. Write records carry bean-validation annotations; read
 * records are plain projections.
 */
public final class VenueDtos {

  private VenueDtos() {
  }

  /**
   * Full admin view of a tenant venue, including the optional {@code masterVenueId} relation key.
   */
  public record VenueResponse(
      UUID id,
      String name,
      String description,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String metadata,
      BigDecimal confidence,
      String source,
      UUID masterVenueId,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * List item — a narrower projection than {@link VenueResponse}, plus the denormalised
   * {@code primaryPhotoUrl} so list and thumbnail views can render the venue photo.
   */
  public record VenueSummaryView(
      UUID id,
      String name,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String primaryPhotoUrl,
      UUID masterVenueId,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Admin search result page.
   *
   * @param items         the page of venues, ordered by the requested sort
   * @param totalElements total number of venues matching the same filters
   */
  public record VenueListResponse(List<VenueSummaryView> items, long totalElements) {
  }

  /**
   * Write payload for both {@code POST /} (create) and {@code PUT /{id}} (full replace).
   *
   * <p>{@code metadata} and {@code confidence} are optional: a create falls back to the canonical
   * empty metadata document and full confidence. {@code masterVenueId} is optional — the tenant
   * venue is not linked to a master record when it is {@code null}.
   */
  public record VenueWriteRequest(
      @NotBlank String name,
      String description,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String metadata,
      BigDecimal confidence,
      @NotBlank String source,
      UUID masterVenueId) {
  }

  /**
   * Partial update payload. A {@code null} field means "leave unchanged"; there is no way to
   * clear a field with this request, which is the documented limitation of a merge-style PATCH.
   * In particular {@code masterVenueId} can be set but not cleared to {@code null} via PATCH.
   */
  public record VenuePatchRequest(
      String name,
      String description,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String metadata,
      BigDecimal confidence,
      String source,
      UUID masterVenueId) {
  }

  /**
   * Tenant write payload for {@code POST /api/v1/catalog/venues} (create) and
   * {@code PUT /api/v1/catalog/venues/{id}} (full replace). Carries only the fields a tenant may
   * manage — curation fields ({@code metadata}, {@code confidence}, {@code source}) are not
   * accepted; the service applies ingestion defaults on create and leaves them untouched on
   * replace.
   */
  public record VenuePublicWriteRequest(
      @NotBlank String name,
      String description,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      UUID masterVenueId) {
  }

  /**
   * Tenant metadata-only patch payload. Replaces the {@code metadata} string of the venue with
   * the caller-supplied value. All other fields (name, address, curation signals) are left
   * untouched. The value is the full serialised {@code MetadataDocument} JSON — the client
   * always sends the entire document, never a delta.
   */
  public record VenueMetadataPatchRequest(
      @NotBlank String metadata) {
  }

  /**
   * Tenant partial-update payload. A {@code null} field means "leave unchanged"; there is no
   * way to clear a field with this request, which is the documented limitation of a merge-style
   * PATCH. In particular {@code masterVenueId} can be set but not cleared to {@code null} via
   * PATCH.
   */
  public record VenuePublicPatchRequest(
      String name,
      String description,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      UUID masterVenueId) {
  }

  /**
   * Tenant-facing view of a single venue — the safe projection. Hard curation signals
   * ({@code confidence}, {@code source}) are not exposed; {@code metadata} is included so
   * tenants can read and edit their own venue metadata document via the structured editor.
   * The optional {@code masterVenueId} relation key is kept so a member can see whether the
   * venue is linked to a master catalog entry, and the denormalised {@code primaryPhotoUrl}
   * lets a caller render a thumbnail without loading the asset list.
   */
  public record VenuePublicResponse(
      UUID id,
      String name,
      String description,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String primaryPhotoUrl,
      String metadata,
      UUID masterVenueId,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Request body for the tenant bulk-delete endpoint — a capped list of venue ids. The delete
   * is idempotent: ids that match no venue are silently ignored, so a repeated request is
   * always safe.
   */
  public record VenueBulkDeleteRequest(
      @NotEmpty @Size(max = 100) List<UUID> ids) {
  }

  /**
   * Single item in a tenant venue semantic search result page.
   *
   * <p>Carries the full admin projection plus a {@code similarity} score in the range
   * [0, 1] (1 = identical), derived from {@code 1 - cosine_distance}.
   */
  public record VenueSemanticSearchResult(
      UUID id,
      String name,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String primaryPhotoUrl,
      String metadata,
      BigDecimal confidence,
      String source,
      UUID masterVenueId,
      Instant createdAt,
      Instant updatedAt,
      double similarity) {
  }

  /**
   * Tenant-facing semantic search item — the safe projection plus a {@code similarity} score in
   * the range [0, 1] (1 = identical). Curation fields ({@code metadata}, {@code confidence},
   * {@code source}) are not exposed; the denormalised {@code primaryPhotoUrl} lets a caller render
   * a thumbnail without loading the asset list.
   */
  public record VenuePublicSemanticSearchResult(
      UUID id,
      String name,
      String address,
      String city,
      BigDecimal locationLat,
      BigDecimal locationLng,
      String primaryPhotoUrl,
      UUID masterVenueId,
      Instant createdAt,
      Instant updatedAt,
      double similarity) {
  }

  /**
   * Hard filters the AI query planner derived from a semantic search query and applied to the
   * candidate set. Echoed back so a caller can see why the results were narrowed;
   * {@code null} or an empty collection means the filter was not applied.
   */
  public record VenueSemanticSearchFilters(
      String city,
      List<String> venueTypes,
      Integer minCapacity,
      Integer maxCapacity,
      String cateringPolicy,
      List<String> amenities,
      Boolean accessible) {
  }

  /**
   * Semantic search result page.
   *
   * @param items          ranked by similarity descending
   * @param totalElements  total number of embedded venues matching the same hard filters
   * @param appliedFilters filters the query planner applied, or an all-null filter set when none
   */
  public record VenueSemanticSearchResponse(List<VenueSemanticSearchResult> items, long totalElements,
                                            VenueSemanticSearchFilters appliedFilters) {
  }

  /**
   * Tenant-facing semantic search result page — the safe projection of the same ranking.
   *
   * @param items          ranked by similarity descending
   * @param totalElements  total number of embedded venues matching the same hard filters
   * @param appliedFilters filters the query planner applied, or an all-null filter set when none
   */
  public record VenuePublicSemanticSearchResponse(List<VenuePublicSemanticSearchResult> items,
                                                  long totalElements,
                                                  VenueSemanticSearchFilters appliedFilters) {
  }

  /**
   * Request body for the enrichment apply endpoint — a list of AI-suggested patches to merge
   * atomically into the venue's current metadata document.
   *
   * <p>Each item maps to one {@code metadata.items} row using the same {@code group}/{@code field}
   * addressing as the ingestion worker and the enrichment suggestions endpoint. The server reads
   * the current document, removes any existing row for each {@code group}/{@code field} pair, and
   * appends the new value — identical to the client-side {@code setFieldValue} merge but
   * performed in one transaction so concurrent edits cannot race.
   */
  public record VenueEnrichmentApplyRequest(
      @NotEmpty List<VenueEnrichmentSuggestion> patches) {
  }

  // ─── Enrichment assistant ─────────────────────────────────────────────────

  /**
   * Request body for the AI-assisted venue profile enrichment endpoint.
   *
   * <p>{@code message} is the tenant's free-text question or description (e.g. "We're a 500-seat
   * rooftop bar in London with in-house catering"). {@code fieldHint} optionally scopes the
   * assistant's reply to one metadata group (e.g. {@code capacity}, {@code amenities}). {@code
   * currentMetadata} optionally carries the venue's current serialised metadata document so the
   * assistant can avoid re-suggesting already-filled fields.
   */
  public record VenueEnrichmentSuggestionRequest(
      @NotBlank String message,
      String fieldHint,
      String currentMetadata) {
  }

  /**
   * A single structured field suggestion that maps directly onto the {@code metadata.items} array
   * shape, so the UI can apply it immediately via {@code PATCH /{venueId}/metadata}.
   *
   * @param group the metadata group key (e.g. {@code capacity}, {@code venue_type})
   * @param field the field key within the group (e.g. {@code max_total}, {@code venue_type})
   * @param value the suggested value as a string; numeric values are expressed as strings here
   *              and the UI is responsible for coercing the type when building the metadata patch
   */
  public record VenueEnrichmentSuggestion(String group, String field, String value) {
  }

  /**
   * Response from the AI-assisted venue profile enrichment endpoint.
   *
   * <p>{@code reply} is the human-readable answer rendered in the tenant UI chat widget.
   * {@code suggestedPatches} carries the structured field suggestions extracted from the reply;
   * it may be empty when the model cannot produce structured output — the assistant always
   * degrades gracefully to {@code reply}-only rather than failing.
   */
  public record VenueEnrichmentSuggestionResponse(
      String reply,
      List<VenueEnrichmentSuggestion> suggestedPatches) {
  }
}
