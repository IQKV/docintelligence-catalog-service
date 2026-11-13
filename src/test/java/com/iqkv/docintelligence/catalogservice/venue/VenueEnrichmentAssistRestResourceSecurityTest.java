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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iqkv.docintelligence.catalogservice.CatalogServiceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Security contract for {@link VenueEnrichmentAssistRestResource}.
 *
 * <p>Verifies that the enrichment suggestions endpoint enforces the same tenant-scoped auth
 * contract as the rest of the venue surface: anonymous callers are rejected (401), callers
 * without a resolved tenant are rejected (400), and callers without {@code TENANT_OWNER} or
 * {@code MEMBER} authority are rejected (403).
 */
@SpringBootTest(classes = {CatalogServiceApplication.class})
@ActiveProfiles("test")
class VenueEnrichmentAssistRestResourceSecurityTest {

  private static final String SUGGESTIONS_PATH =
      "/api/v1/catalog/venues/00000000-0000-0000-0000-000000000001/enrichment/suggestions";
  private static final String TENANT_HEADER = "X-Tenant-ID";
  private static final String TENANT_KEY = "xk7f2b9a";
  private static final String VALID_BODY = """
      {"message":"We are a rooftop bar in central London"}
      """;

  @Autowired
  private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
  }

  @Test
  @DisplayName("Enrichment suggestions endpoint requires a resolved tenant identifier")
  void rejectsMissingTenant() throws Exception {
    mockMvc.perform(post(SUGGESTIONS_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Enrichment suggestions endpoint rejects anonymous caller")
  void rejectsAnonymous() throws Exception {
    mockMvc.perform(post(SUGGESTIONS_PATH)
            .header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  @DisplayName("Enrichment suggestions endpoint rejects PLATFORM_ADMIN — tenant surface only")
  void rejectsPlatformAdmin() throws Exception {
    mockMvc.perform(post(SUGGESTIONS_PATH)
            .header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "MEMBER")
  @DisplayName("Enrichment suggestions endpoint rejects a blank message")
  void rejectsBlankMessage() throws Exception {
    mockMvc.perform(post(SUGGESTIONS_PATH)
            .header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"message":""}
                """))
        .andExpect(status().isBadRequest());
  }

  // ─── /apply ──────────────────────────────────────────────────────────────

  private static final String APPLY_PATH =
      "/api/v1/catalog/venues/00000000-0000-0000-0000-000000000001/enrichment/apply";
  private static final String VALID_APPLY_BODY = """
      {"patches":[{"group":"venue_type","field":"venue_type","value":"Rooftop"}]}
      """;

  @Test
  @DisplayName("Apply endpoint requires a resolved tenant identifier")
  void applyRejectsMissingTenant() throws Exception {
    mockMvc.perform(post(APPLY_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_APPLY_BODY))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Apply endpoint rejects anonymous caller")
  void applyRejectsAnonymous() throws Exception {
    mockMvc.perform(post(APPLY_PATH)
            .header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_APPLY_BODY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  @DisplayName("Apply endpoint rejects PLATFORM_ADMIN — tenant surface only")
  void applyRejectsPlatformAdmin() throws Exception {
    mockMvc.perform(post(APPLY_PATH)
            .header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_APPLY_BODY))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "MEMBER")
  @DisplayName("Apply endpoint rejects empty patches list")
  void applyRejectsEmptyPatches() throws Exception {
    mockMvc.perform(post(APPLY_PATH)
            .header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"patches":[]}
                """))
        .andExpect(status().isBadRequest());
  }
}
