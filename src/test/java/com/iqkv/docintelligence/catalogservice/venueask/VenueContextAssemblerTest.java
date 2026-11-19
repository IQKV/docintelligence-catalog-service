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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the page-aware citation excerpt of {@link VenueContextAssembler}: which passage of
 * a persisted venue source is cited, and the page it starts on.
 */
@DisplayName("VenueContextAssembler.bestExcerpt")
class VenueContextAssemblerTest {

  private static final String PAGED = """
      [page 1]
      Grand Hall — venue brochure

      [page 2]
      The hall seats 200 guests for a banquet and 300 standing.

      [page 3]
      Parking: two levels of underground parking, 80 spaces.
      """;

  @Test
  @DisplayName("cites the page whose text mentions the question's terms")
  void citesPageMatchingTheQuestion() {
    // Act
    final var excerpt = VenueContextAssembler.bestExcerpt(PAGED, "Do you have parking?");

    // Assert
    assertThat(excerpt.page()).isEqualTo(3);
    assertThat(excerpt.text()).contains("two levels of underground parking");
  }

  @Test
  @DisplayName("cites the first page when no page mentions the question's terms")
  void citesFirstPageWhenNothingMatches() {
    // Act
    final var excerpt = VenueContextAssembler.bestExcerpt(PAGED, "Is there a helipad?");

    // Assert
    assertThat(excerpt.page()).isEqualTo(1);
  }

  @Test
  @DisplayName("leaves the excerpt unpaged when the text carries no page notes")
  void unpagedTextKeepsItsExcerpt() {
    // Act
    final var excerpt = VenueContextAssembler.bestExcerpt("A short note about parking.", "parking");

    // Assert
    assertThat(excerpt.page()).isNull();
    assertThat(excerpt.text()).isEqualTo("A short note about parking.");
  }

  @Test
  @DisplayName("cites nothing when the source has no text")
  void blankSourceCitesNothing() {
    // Act
    final var excerpt = VenueContextAssembler.bestExcerpt("   ", "parking");

    // Assert
    assertThat(excerpt.page()).isNull();
    assertThat(excerpt.text()).isEmpty();
  }
}
