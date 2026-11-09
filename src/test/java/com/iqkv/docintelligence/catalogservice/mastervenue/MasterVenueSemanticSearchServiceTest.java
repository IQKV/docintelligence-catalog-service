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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.iqkv.docintelligence.catalogservice.mastervenue.dto.MasterVenueDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class MasterVenueSemanticSearchServiceTest {

  @Mock
  private JdbcTemplate jdbcTemplate;

  @Mock
  private EmbeddingModel embeddingModel;

  @Mock
  private MasterVenueQueryPlanner queryPlanner;

  private MasterVenueSemanticSearchService semanticSearchService;

  @BeforeEach
  void setUp() {
    semanticSearchService = new MasterVenueSemanticSearchService(jdbcTemplate, embeddingModel, queryPlanner);
  }

  @Test
  @DisplayName("search embeds only the residual query and turns the plan into JSONB predicates")
  void shouldApplyPlanAsJsonbPredicates() {
    // Arrange
    final var plan = new MasterVenueQueryPlanner.QueryPlan(
        "Kyiv", List.of("Rooftop", "Outdoor garden or terrace"), 200, 500,
        "External catering only", List.of("Terrace"), Boolean.TRUE, "rooftop party");
    when(queryPlanner.plan("rooftop party in Kyiv for 200 guests")).thenReturn(plan);
    when(embeddingModel.embed("rooftop party")).thenReturn(new float[] {0.1f, 0.2f});

    // Act
    final MasterVenueDtos.SemanticSearchResponse response =
        semanticSearchService.search("rooftop party in Kyiv for 200 guests", null, 20);

    // Assert
    verify(embeddingModel).embed("rooftop party");

    final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate, times(2)).query(sqlCaptor.capture(), any(PreparedStatementSetter.class),
        ArgumentMatchers.<RowMapper<Object>>any());
    final String searchSql = sqlCaptor.getAllValues().get(0);
    final String countSql = sqlCaptor.getAllValues().get(1);

    assertThat(searchSql)
        .contains("LOWER(city) = LOWER(?)")
        .contains("metadata->'items' @> jsonb_build_array(jsonb_build_object('group', 'venue_type', 'field', 'venue_type', 'value', ?))")
        .contains(">= ?")
        .contains("<= ?")
        .contains("metadata->'items' @> jsonb_build_array(jsonb_build_object('group', 'catering', 'field', 'policy', 'value', ?))")
        .contains("metadata->'items' @> jsonb_build_array(jsonb_build_object('group', 'amenities', 'field', 'amenities', 'value', ?))")
        .contains("'[{\"group\":\"accessibility\",\"field\":\"ada_compliant\",\"value\":true}]'::jsonb")
        .contains("ORDER BY embedding <=> ?::vector");
    assertThat(countSql).contains("jsonb_build_object('group', 'venue_type'");

    assertThat(response.appliedFilters()).isEqualTo(new MasterVenueDtos.SemanticSearchFilters(
        "Kyiv", List.of("Rooftop", "Outdoor garden or terrace"), 200, 500,
        "External catering only", List.of("Terrace"), Boolean.TRUE));
    assertThat(response.totalElements()).isZero();
  }

  @Test
  @DisplayName("search falls back to a plain vector scan when the plan is empty")
  void shouldDegradeWhenPlanIsEmpty() {
    // Arrange
    when(queryPlanner.plan("rooftop party")).thenReturn(MasterVenueQueryPlanner.QueryPlan.empty("rooftop party"));
    when(embeddingModel.embed("rooftop party")).thenReturn(new float[] {0.1f});

    // Act
    final MasterVenueDtos.SemanticSearchResponse response =
        semanticSearchService.search("rooftop party", null, 10);

    // Assert
    verify(embeddingModel).embed("rooftop party");
    assertThat(response.appliedFilters()).isEqualTo(new MasterVenueDtos.SemanticSearchFilters(
        null, List.of(), null, null, null, List.of(), null));

    final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate, times(2)).query(sqlCaptor.capture(), any(PreparedStatementSetter.class),
        ArgumentMatchers.<RowMapper<Object>>any());
    assertThat(sqlCaptor.getAllValues().get(0))
        .doesNotContain("metadata->'items'")
        .doesNotContain("LOWER(city)");
  }
}
