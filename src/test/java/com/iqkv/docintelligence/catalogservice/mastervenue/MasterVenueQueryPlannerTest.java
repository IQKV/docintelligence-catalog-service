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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import com.iqkv.docintelligence.catalogservice.infrastructure.config.MasterVenueAiSearchProperties;
import com.iqkv.docintelligence.catalogservice.mastervenue.MasterVenueQueryPlanner.QueryPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class MasterVenueQueryPlannerTest {

  @Mock
  private ObjectProvider<ChatClient.Builder> chatClientBuilder;

  @Mock
  private ChatClient.Builder builder;

  @Mock
  private ChatClient chatClient;

  @Mock
  private ChatClient.ChatClientRequestSpec requestSpec;

  @Mock
  private ChatClient.CallResponseSpec callResponseSpec;

  private MasterVenueAiSearchProperties properties;

  private MasterVenueQueryPlanner planner;

  @BeforeEach
  void setUp() {
    properties = new MasterVenueAiSearchProperties(true);
    planner = new MasterVenueQueryPlanner(chatClientBuilder, properties);
  }

  @Test
  @DisplayName("plan returns an empty plan without calling the model when planning is disabled")
  void shouldNotCallModelWhenDisabled() {
    // Arrange
    planner = new MasterVenueQueryPlanner(chatClientBuilder, new MasterVenueAiSearchProperties(false));
    when(chatClientBuilder.getIfAvailable()).thenReturn(builder);

    // Act
    final QueryPlan plan = planner.plan("rooftop party in Kyiv for 200 guests");

    // Assert
    assertThat(plan).isEqualTo(QueryPlan.empty("rooftop party in Kyiv for 200 guests"));
    verify(builder, never()).build();
  }

  @Test
  @DisplayName("plan returns an empty plan when no chat model is on the context")
  void shouldReturnEmptyPlanWhenNoChatModel() {
    // Arrange
    when(chatClientBuilder.getIfAvailable()).thenReturn(null);

    // Act
    final QueryPlan plan = planner.plan("rooftop party in Kyiv");

    // Assert
    assertThat(plan).isEqualTo(QueryPlan.empty("rooftop party in Kyiv"));
  }

  @Test
  @DisplayName("plan returns an empty plan for a blank query without calling the model")
  void shouldNotCallModelForBlankQuery() {
    // Arrange
    when(chatClientBuilder.getIfAvailable()).thenReturn(builder);

    // Act
    final QueryPlan plan = planner.plan("   ");

    // Assert
    assertThat(plan).isEqualTo(QueryPlan.empty("   "));
    verify(builder, never()).build();
  }

  @Test
  @DisplayName("plan sanitises the structured output the model returns")
  void shouldUseSanitisedModelPlan() {
    // Arrange
    stubModelReturning(new QueryPlan(
        " Kyiv ", List.of("Rooftop", "not_a_type"), 200, 500,
        "EXTERNAL CATERING ONLY", List.of("Terrace"), Boolean.TRUE, "rooftop party"));

    // Act
    final QueryPlan plan = planner.plan("some query");

    // Assert
    assertThat(plan.city()).isEqualTo("Kyiv");
    assertThat(plan.venueTypes()).containsExactly("Rooftop");
    assertThat(plan.minCapacity()).isEqualTo(200);
    assertThat(plan.maxCapacity()).isEqualTo(500);
    assertThat(plan.cateringPolicy()).isEqualTo("External catering only");
    assertThat(plan.amenities()).containsExactly("Terrace");
    assertThat(plan.accessible()).isTrue();
    assertThat(plan.semanticQuery()).isEqualTo("rooftop party");
  }

  @Test
  @DisplayName("plan degrades to an empty plan when the model call fails")
  void shouldFallBackWhenModelFails() {
    // Arrange
    when(chatClientBuilder.getIfAvailable()).thenReturn(builder);
    when(builder.build()).thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);
    when(callResponseSpec.entity(any(StructuredOutputConverter.class)))
        .thenThrow(new RuntimeException("ollama down"));

    // Act
    final QueryPlan plan = planner.plan("rooftop party in Kyiv");

    // Assert
    assertThat(plan).isEqualTo(QueryPlan.empty("rooftop party in Kyiv"));
  }

  @Test
  @DisplayName("sanitize drops unknown venue types and an invalid catering policy")
  void sanitizeShouldDropUnknownValues() {
    // Arrange
    final QueryPlan raw = new QueryPlan(
        null, List.of("gallery", "spaceship", "GALLERY"), null, null,
        "bring_your_own", List.of(), null, "gallery launch");

    // Act
    final QueryPlan plan = MasterVenueQueryPlanner.sanitize(raw, "gallery launch");

    // Assert
    assertThat(plan.venueTypes()).containsExactly("Gallery");
    assertThat(plan.cateringPolicy()).isNull();
    assertThat(plan.city()).isNull();
  }

  @Test
  @DisplayName("sanitize drops non-positive capacities and caps the amenity list")
  void sanitizeShouldClampNumbersAndAmenities() {
    // Arrange
    final List<String> manyAmenities = IntStream.range(0, 12)
        .mapToObj(index -> "amenity-" + index)
        .toList();
    final QueryPlan raw = new QueryPlan(
        "Kyiv", List.of(), -10, 0, null, manyAmenities, null, "party");

    // Act
    final QueryPlan plan = MasterVenueQueryPlanner.sanitize(raw, "party");

    // Assert
    assertThat(plan.minCapacity()).isNull();
    assertThat(plan.maxCapacity()).isNull();
    assertThat(plan.amenities()).hasSize(8);
  }

  @Test
  @DisplayName("sanitize falls back to the original query when the residual text is blank")
  void sanitizeShouldFallBackToOriginalQuery() {
    // Arrange
    final QueryPlan raw = new QueryPlan(
        "Kyiv", List.of(), null, null, null, List.of(), null, "   ");

    // Act
    final QueryPlan plan = MasterVenueQueryPlanner.sanitize(raw, "rooftop party in Kyiv");

    // Assert
    assertThat(plan.semanticQuery()).isEqualTo("rooftop party in Kyiv");
  }

  @Test
  @DisplayName("sanitize returns an empty plan when the model returned nothing")
  void sanitizeShouldHandleNullRawPlan() {
    // Act
    final QueryPlan plan = MasterVenueQueryPlanner.sanitize(null, "rooftop party");

    // Assert
    assertThat(plan).isEqualTo(QueryPlan.empty("rooftop party"));
  }

  @Test
  @DisplayName("the converter keeps the first object of a reply that repeats itself")
  void converterShouldTolerateTrailingTokens() {
    // Arrange: llama3.2 occasionally emits the same JSON object twice.
    final String modelReply = """
        {"city":"Kyiv","venueTypes":["rooftop"],"minCapacity":200,"maxCapacity":null,
         "cateringPolicy":null,"amenities":[],"accessible":null,"semanticQuery":"rooftop party"}
        {"city":"Lviv","venueTypes":[],"minCapacity":null,"maxCapacity":null,
         "cateringPolicy":null,"amenities":[],"accessible":null,"semanticQuery":"rooftop party"}
        """;

    // Act
    final QueryPlan plan = MasterVenueQueryPlanner.QUERY_PLAN_CONVERTER.convert(modelReply);

    // Assert
    assertThat(plan.city()).isEqualTo("Kyiv");
    assertThat(plan.venueTypes()).containsExactly("rooftop");
    assertThat(plan.minCapacity()).isEqualTo(200);
  }

  @Test
  @DisplayName("the converter ignores properties the model invents")
  void converterShouldIgnoreUnknownProperties() {
    // Arrange
    final String modelReply = """
        {"city":"Kyiv","venueTypes":[],"amenities":[],"confidence":"high",
         "reasoning":"the query names a city","semanticQuery":"rooftop party"}
        """;

    // Act
    final QueryPlan plan = MasterVenueQueryPlanner.QUERY_PLAN_CONVERTER.convert(modelReply);

    // Assert
    assertThat(plan.city()).isEqualTo("Kyiv");
    assertThat(plan.semanticQuery()).isEqualTo("rooftop party");
  }

  private void stubModelReturning(final QueryPlan modelOutput) {
    when(chatClientBuilder.getIfAvailable()).thenReturn(builder);
    when(builder.build()).thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);
    when(callResponseSpec.entity(any(StructuredOutputConverter.class))).thenReturn(modelOutput);
  }
}
