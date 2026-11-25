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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.iqkv.docintelligence.catalogservice.venue.VenueAssetService;
import com.iqkv.docintelligence.catalogservice.venue.VenueService;
import com.iqkv.docintelligence.catalogservice.venue.VenueTextContentService;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueTextContentDtos;
import com.iqkv.docintelligence.catalogservice.venueask.dto.VenueAskDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Gathers the tenant's own venue data into the context the ask and chat surfaces ground on.
 *
 * <p>Three retrieval shapes are offered: the full context bundle of one venue (no model call — used
 * by the preload endpoint), the ranked source chunks of one venue most relevant to a question, and
 * the ranked venues of the whole catalog. Each degrades to an empty result rather than throwing, so
 * a caller can always answer with {@code answered=false}. All retrieval is tenant-scoped through the
 * active tenant schema, so a venue id from another tenant resolves to nothing.
 *
 * <p>The two rendered blocks are the grounding text handed to the model: {@link #renderVenueBlock}
 * for one venue and {@link #renderVenueListBlock} for the catalog.
 */
@Service
class VenueContextAssembler {

  private static final Logger log = LoggerFactory.getLogger(VenueContextAssembler.class);

  /** Cap on the excerpt of one source chunk kept for grounding and citation. */
  static final int MAX_SNIPPET_CHARS = 1_200;

  /**
   * The {@code [page N]} note the processing worker writes into persisted venue source text.
   */
  private static final Pattern PAGE_MARKER = Pattern.compile("\\[page (\\d+)\\]");

  /**
   * Word terms a question is split into to score one page against it.
   */
  private static final Pattern TERM = Pattern.compile("[\\p{L}\\p{N}]+");

  /**
   * Question scaffolding that carries no retrieval signal, so a page is not scored on it.
   */
  private static final Set<String> STOP_WORDS = Set.of(
      "the", "a", "an", "is", "are", "was", "were", "of", "in", "on", "at", "for", "to", "and",
      "or", "what", "which", "who", "how", "when", "where", "why", "it", "this", "that", "there",
      "your", "you", "we", "our", "can", "could", "do", "does", "be", "has", "have", "with", "by",
      "from", "as", "any", "all", "please", "about");

  private final VenueService venueService;
  private final VenueTextContentService venueTextContentService;
  private final VenueAssetService venueAssetService;

  VenueContextAssembler(final VenueService venueService,
                        final VenueTextContentService venueTextContentService,
                        final VenueAssetService venueAssetService) {
    this.venueService = venueService;
    this.venueTextContentService = venueTextContentService;
    this.venueAssetService = venueAssetService;
  }

  /**
   * The full context bundle of one venue — profile, source text rows and assets.
   *
   * @throws com.iqkv.docintelligence.catalogservice.venue.VenueNotFoundException when the venue
   *     does not exist in the active tenant schema
   */
  VenueAskDtos.VenueContextResponse assemble(final UUID venueId, final UUID userId) {
    return new VenueAskDtos.VenueContextResponse(
        venueService.getPublicVenue(venueId, userId),
        venueTextContentService.listByVenue(venueId),
        venueAssetService.listAssets(venueId));
  }

  /**
   * Ranks the venue's source chunks against {@code question} and returns up to {@code maxSources}
   * of them as citations. Falls back to the venue's rows in creation order when the venue has no
   * embedded chunk (or the embedding model is unreachable) — the answer is then grounded on the
   * earliest sources rather than nothing.
   */
  List<VenueAskDtos.VenueAskSource> retrieveSources(final UUID venueId,
                                                    final String question,
                                                    final int maxSources) {
    final List<VenueTextContentDtos.VenueTextContentSemanticSearchResult> ranked;
    try {
      ranked = venueTextContentService.semanticSearch(question, venueId, maxSources).items();
    } catch (final RuntimeException ex) {
      log.warn("Venue source semantic search failed, falling back to creation order: venueId={}, error={}",
          venueId, ex.getMessage());
      return firstRows(venueId, question, maxSources);
    }
    if (ranked.isEmpty()) {
      return firstRows(venueId, question, maxSources);
    }
    return ranked.stream()
        .map(row -> toSource(row.id(), row.sourceAssetId(), row.label(), row.content(),
            row.similarity(), question))
        .toList();
  }

  /**
   * Ranks the tenant's venues against {@code question}, degrading to an empty page when the vector
   * search is unavailable so callers still answer the request.
   */
  VenueDtos.VenuePublicSemanticSearchResponse retrieveVenues(final String question,
                                                            final String city,
                                                            final int limit,
                                                            final UUID userId) {
    try {
      return venueService.semanticSearchPublic(question, city, limit, userId);
    } catch (final RuntimeException ex) {
      log.warn("Venue list semantic search failed, degrading to no candidates: {}", ex.getMessage());
      return new VenueDtos.VenuePublicSemanticSearchResponse(List.of(), 0L, emptyFilters());
    }
  }

  /**
   * Renders one venue's profile and retrieved source chunks into the grounding block.
   */
  static String renderVenueBlock(final VenueDtos.VenuePublicResponse venue,
                                 final List<VenueAskDtos.VenueAskSource> sources) {
    final var sb = new StringBuilder("VENUE\n");
    appendIfPresent(sb, "Name", venue.name());
    appendIfPresent(sb, "City", venue.city());
    appendIfPresent(sb, "Address", venue.address());
    appendIfPresent(sb, "Description", venue.description());
    appendIfPresent(sb, "Metadata", venue.metadata());
    if (!sources.isEmpty()) {
      sb.append("\nSOURCES\n");
      for (int i = 0; i < sources.size(); i++) {
        final VenueAskDtos.VenueAskSource source = sources.get(i);
        sb.append('[').append(i + 1).append(']');
        if (source.label() != null && !source.label().isBlank()) {
          sb.append(' ').append(source.label());
        }
        sb.append('\n').append(source.snippet()).append('\n');
      }
    }
    return sb.toString();
  }

  /**
   * Renders the ranked catalogue of venues into the grounding block.
   */
  static String renderVenueListBlock(final List<VenueDtos.VenuePublicSemanticSearchResult> venues) {
    final var sb = new StringBuilder("VENUES\n");
    for (int i = 0; i < venues.size(); i++) {
      final VenueDtos.VenuePublicSemanticSearchResult venue = venues.get(i);
      sb.append('[').append(i + 1).append(']').append(' ')
          .append(venue.name() == null || venue.name().isBlank() ? "Unnamed venue" : venue.name());
      if (venue.city() != null && !venue.city().isBlank()) {
        sb.append(" - ").append(venue.city());
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  private List<VenueAskDtos.VenueAskSource> firstRows(final UUID venueId,
                                                      final String question,
                                                      final int maxSources) {
    return venueTextContentService.listByVenue(venueId).items().stream()
        .limit(maxSources)
        .map(row -> toSource(row.id(), row.sourceAssetId(), row.label(), row.content(), 0.0, question))
        .toList();
  }

  private static VenueAskDtos.VenueAskSource toSource(final UUID sourceTextId,
                                                      final UUID assetId,
                                                      final String label,
                                                      final String content,
                                                      final double similarity,
                                                      final String question) {
    final PageExcerpt excerpt = bestExcerpt(content, question);
    return new VenueAskDtos.VenueAskSource(
        sourceTextId, assetId, label, excerpt.text(), excerpt.page(), similarity);
  }

  /**
   * Selects the passage of {@code content} to cite together with the page it starts on.
   *
   * <p>Text carrying {@code [page N]} notes is scored page by page against the question's terms, so
   * the snippet points at the passage an answer is expected to come from rather than at the
   * document head. Text without page notes — or a question with no scoring term — keeps the leading
   * excerpt, and {@code page} is then {@code null}.
   */
  static PageExcerpt bestExcerpt(final String content, final String question) {
    if (content == null || content.isBlank()) {
      return new PageExcerpt(null, "");
    }
    final List<PageExcerpt> sections = pages(content);
    if (sections.isEmpty()) {
      return new PageExcerpt(null, snippet(content));
    }
    final List<String> terms = terms(question);
    PageExcerpt best = sections.getFirst();
    int bestScore = score(best.text(), terms);
    for (int i = 1; i < sections.size(); i++) {
      final int score = score(sections.get(i).text(), terms);
      if (score > bestScore) {
        bestScore = score;
        best = sections.get(i);
      }
    }
    return new PageExcerpt(best.page(), snippet(best.text().strip()));
  }

  /**
   * Splits persisted text into its {@code [page N]} sections; empty when it carries no page note.
   */
  private static List<PageExcerpt> pages(final String content) {
    final List<PageExcerpt> sections = new ArrayList<>();
    final Matcher matcher = PAGE_MARKER.matcher(content);
    Integer page = null;
    int start = -1;
    while (matcher.find()) {
      if (start >= 0) {
        sections.add(new PageExcerpt(page, content.substring(start, matcher.start())));
      }
      page = Integer.parseInt(matcher.group(1));
      start = matcher.end();
    }
    if (start >= 0) {
      sections.add(new PageExcerpt(page, content.substring(start)));
    }
    return sections;
  }

  /** The distinct question terms a page is scored on — single characters and scaffolding dropped. */
  private static List<String> terms(final String question) {
    if (question == null || question.isBlank()) {
      return List.of();
    }
    final List<String> terms = new ArrayList<>();
    final Matcher matcher = TERM.matcher(question.toLowerCase(Locale.ROOT));
    while (matcher.find()) {
      final String term = matcher.group();
      if (term.length() > 1 && !STOP_WORDS.contains(term) && !terms.contains(term)) {
        terms.add(term);
      }
    }
    return terms;
  }

  /** How many of the question's terms the page mentions. */
  private static int score(final String text, final List<String> terms) {
    if (terms.isEmpty() || text.isEmpty()) {
      return 0;
    }
    final String lower = text.toLowerCase(Locale.ROOT);
    int hits = 0;
    for (final String term : terms) {
      if (lower.contains(term)) {
        hits++;
      }
    }
    return hits;
  }

  /**
   * One page of a source document: its 1-based number ({@code null} when unpaged) and its text.
   */
  record PageExcerpt(Integer page, String text) {
  }

  private static VenueDtos.VenueSemanticSearchFilters emptyFilters() {
    return new VenueDtos.VenueSemanticSearchFilters(null, List.of(), null, null, null, List.of(), null);
  }

  private static void appendIfPresent(final StringBuilder sb, final String name, final String value) {
    if (value != null && !value.isBlank()) {
      sb.append(name).append(": ").append(value).append('\n');
    }
  }

  private static String snippet(final String content) {
    if (content == null) {
      return "";
    }
    if (content.length() <= MAX_SNIPPET_CHARS) {
      return content;
    }
    // Walk back to the nearest whitespace so the excerpt does not cut mid-word.
    int cut = MAX_SNIPPET_CHARS;
    while (cut > 0 && !Character.isWhitespace(content.charAt(cut))) {
      cut--;
    }
    return cut > 0 ? content.substring(0, cut) : content.substring(0, MAX_SNIPPET_CHARS);
  }
}
