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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Security contract for the admin smart-note endpoints.
 *
 * <p>The path is under {@code /admin/}, so {@code TenantExtractionFilter} does not run and the
 * class-level {@code @PreAuthorize} enforces {@code PLATFORM_ADMIN}. Anonymous callers are
 * rejected; a {@code TENANT_OWNER} caller is forbidden.
 */
@SpringBootTest(classes = {CatalogServiceApplication.class})
@ActiveProfiles("test")
class AdminVenueSmartNoteRestResourceSecurityTest {

  private static final String ADMIN_PATH = "/api/v1/catalog/admin/xk7f2b9a/venue-smart-notes";
  private static final String UUID_ZERO = "00000000-0000-0000-0000-000000000001";
  private static final String JSON_CREATE =
      "{\"venueId\":\"" + UUID_ZERO + "\",\"content\":\"note\"}";

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
  @DisplayName("Admin list smart notes rejects an anonymous caller")
  void adminListRejectsAnonymous() throws Exception {
    mockMvc.perform(get(ADMIN_PATH + "/venue/" + UUID_ZERO))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin get smart note rejects an anonymous caller")
  void adminGetRejectsAnonymous() throws Exception {
    mockMvc.perform(get(ADMIN_PATH + "/" + UUID_ZERO))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin create smart note rejects an anonymous caller")
  void adminCreateRejectsAnonymous() throws Exception {
    mockMvc.perform(post(ADMIN_PATH)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_CREATE))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin patch smart note rejects an anonymous caller")
  void adminPatchRejectsAnonymous() throws Exception {
    mockMvc.perform(patch(ADMIN_PATH + "/" + UUID_ZERO)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin delete smart note rejects an anonymous caller")
  void adminDeleteRejectsAnonymous() throws Exception {
    mockMvc.perform(delete(ADMIN_PATH + "/" + UUID_ZERO))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin list smart notes rejects a non-PLATFORM_ADMIN caller")
  @WithMockUser(authorities = "TENANT_OWNER")
  void adminListRejectsNonAdmin() throws Exception {
    mockMvc.perform(get(ADMIN_PATH + "/venue/" + UUID_ZERO))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Admin delete smart note rejects a non-PLATFORM_ADMIN caller")
  @WithMockUser(authorities = "TENANT_OWNER")
  void adminDeleteRejectsNonAdmin() throws Exception {
    mockMvc.perform(delete(ADMIN_PATH + "/" + UUID_ZERO))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Admin pin smart note rejects an anonymous caller")
  void adminPinRejectsAnonymous() throws Exception {
    mockMvc.perform(put(ADMIN_PATH + "/" + UUID_ZERO + "/pinned")
            .contentType(MediaType.APPLICATION_JSON).content("{\"pinned\":true}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin get linked asset rejects an anonymous caller")
  void adminGetAssetRejectsAnonymous() throws Exception {
    mockMvc.perform(get(ADMIN_PATH + "/" + UUID_ZERO + "/asset"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin get linked asset rejects a non-PLATFORM_ADMIN caller")
  @WithMockUser(authorities = "TENANT_OWNER")
  void adminGetAssetRejectsNonAdmin() throws Exception {
    mockMvc.perform(get(ADMIN_PATH + "/" + UUID_ZERO + "/asset"))
        .andExpect(status().isForbidden());
  }
}
