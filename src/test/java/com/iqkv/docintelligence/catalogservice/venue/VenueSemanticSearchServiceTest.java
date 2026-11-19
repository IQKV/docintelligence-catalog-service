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
 * Tests JSONB filter generation for metadata fields used by tenant venue search.
 */
@DisplayName("VenueSemanticSearchService filters")
class VenueSemanticSearchServiceTest {

  @Test
  @DisplayName("matches new profile categories and legacy venue-type representations")
  void matchesProfileAndLegacyVenueCategories() {
    final VenueSemanticSearchService.Filter filter = VenueSemanticSearchService.buildFilter(
        null, List.of("Warehouse or industrial space"), null, null, null, List.of(), null);

    assertThat(filter.sql())
        .contains("'group', 'profile', 'field', 'category', 'value', ?")
        .contains("'group', 'venue_type', 'field', 'venue_type', 'value', ?")
        .contains("'group', 'venue_type', 'field', 'venue_type', 'value', jsonb_build_array(?)");
    assertThat(filter.binders()).hasSize(3);
  }

  @Test
  @DisplayName("omits category predicates when no category was requested")
  void omitsCategoryPredicatesWithoutFilterValues() {
    final VenueSemanticSearchService.Filter filter = VenueSemanticSearchService.buildFilter(
        null, List.of(), null, null, null, List.of(), null);

    assertThat(filter.sql()).isEmpty();
    assertThat(filter.binders()).isEmpty();
  }
}
