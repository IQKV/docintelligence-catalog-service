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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure, model-independent parts of {@link VenueQueryPlanner} — the
 * {@link VenueQueryPlanner.QueryPlan#empty(String)} fallback and {@link VenueQueryPlanner#sanitize}
 * validation. The LLM-backed {@code plan} path is not exercised here; it is covered by the
 * semantic-search service tests against the running model.
 */
@DisplayName("VenueQueryPlanner Unit Tests")
class VenueQueryPlannerTest {

  @Test
  @DisplayName("empty plan embeds the query unchanged with no filters")
  void emptyPlanEmbedsQueryUnchanged() {
    final var plan = VenueQueryPlanner.QueryPlan.empty("loft rooftop bar");

    assertThat(plan.semanticQuery()).isEqualTo("loft rooftop bar");
    assertThat(plan.city()).isNull();
    assertThat(plan.venueTypes()).isEmpty();
    assertThat(plan.amenities()).isEmpty();
    assertThat(plan.minCapacity()).isNull();
    assertThat(plan.maxCapacity()).isNull();
    assertThat(plan.cateringPolicy()).isNull();
    assertThat(plan.accessible()).isNull();
  }

  @Test
  @DisplayName("sanitize drops unknown venue types, clamps non-positive capacities and trims amenities")
  void sanitizeDropsUnknownAndClamps() {
    // Arrange — "ufo" is not a canonical venue type, -5 capacity is non-positive, amenities
    // carry a blank entry that must be dropped and a padded entry that must be trimmed. The
    // lowercase type and shouting catering policy must come back in their canonical casing.
    final var raw = new VenueQueryPlanner.QueryPlan(
        "Paris", List.of("restaurant", "ufo"), -5, 1000, "IN-HOUSE CATERING ONLY",
        List.of("  wifi  ", ""), false, "loft rooftop");

    // Act
    final var sanitized = VenueQueryPlanner.sanitize(raw, "loft rooftop");

    // Assert
    assertThat(sanitized.city()).isEqualTo("Paris");
    assertThat(sanitized.venueTypes()).containsExactly("Restaurant");
    assertThat(sanitized.minCapacity()).isNull();
    assertThat(sanitized.maxCapacity()).isEqualTo(1000);
    assertThat(sanitized.amenities()).containsExactly("wifi");
    assertThat(sanitized.cateringPolicy()).isEqualTo("In-house catering only");
    assertThat(sanitized.semanticQuery()).isEqualTo("loft rooftop");
  }

  @Test
  @DisplayName("sanitize matches a multi-word venue type regardless of casing")
  void sanitizeMatchesMultiWordVenueType() {
    final var raw = new VenueQueryPlanner.QueryPlan(
        null, List.of("bar or lounge"), null, null, null, List.of(), null, "bar");

    final var sanitized = VenueQueryPlanner.sanitize(raw, "bar");

    assertThat(sanitized.venueTypes()).containsExactly("Bar or lounge");
  }

  @Test
  @DisplayName("sanitize drops a non-canonical catering policy")
  void sanitizeDropsNonCanonicalCateringPolicy() {
    final var raw = new VenueQueryPlanner.QueryPlan(
        null, List.of(), null, null, "free_for_all", List.of(), null, "vibe");

    final var sanitized = VenueQueryPlanner.sanitize(raw, "vibe");

    assertThat(sanitized.cateringPolicy()).isNull();
  }

  @Test
  @DisplayName("sanitize falls back to the raw query when the model returns a blank semanticQuery")
  void sanitizeFallsBackToQueryWhenSemanticBlank() {
    final var raw = new VenueQueryPlanner.QueryPlan(
        null, List.of(), null, null, null, List.of(), null, "   ");

    final var sanitized = VenueQueryPlanner.sanitize(raw, "rooftop with a view");

    assertThat(sanitized.semanticQuery()).isEqualTo("rooftop with a view");
  }

  @Test
  @DisplayName("sanitize returns an empty plan when the model reply is null")
  void sanitizeReturnsEmptyPlanForNull() {
    final var sanitized = VenueQueryPlanner.sanitize(null, "loft");

    assertThat(sanitized.semanticQuery()).isEqualTo("loft");
    assertThat(sanitized.venueTypes()).isEmpty();
  }
}
