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
 * Security contract for the tenant-facing smart-note endpoints.
 *
 * <p>Reads, creates and edits (patch/pin) are open to {@code MEMBER}, {@code TENANT_OWNER} and
 * {@code ADMIN}; deletes require {@code TENANT_OWNER} or {@code ADMIN}. Author-level ownership of
 * edits is enforced in {@code VenueSmartNoteServiceImpl} and covered by its unit tests. Anonymous
 * callers are rejected; a {@code PLATFORM_ADMIN} caller is forbidden because it carries no tenant
 * authority.
 */
@SpringBootTest(classes = {CatalogServiceApplication.class})
@ActiveProfiles("test")
class VenueSmartNoteRestResourceSecurityTest {

  private static final String BASE_PATH = "/api/v1/catalog/venue-smart-notes";
  private static final String TENANT_HEADER = "X-Tenant-ID";
  private static final String TENANT_KEY = "xk7f2b9a";
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

  // ─── Reads: anonymous rejected, PLATFORM_ADMIN forbidden ──────────────────

  @Test
  @DisplayName("List smart notes rejects an anonymous caller")
  void listRejectsAnonymous() throws Exception {
    mockMvc.perform(get(BASE_PATH + "/venue/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("List smart notes rejects a caller without a tenant authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void listRejectsNonTenantAuthority() throws Exception {
    mockMvc.perform(get(BASE_PATH + "/venue/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Get smart note rejects an anonymous caller")
  void getRejectsAnonymous() throws Exception {
    mockMvc.perform(get(BASE_PATH + "/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  // ─── Create: anonymous rejected, PLATFORM_ADMIN forbidden ─────────────────

  @Test
  @DisplayName("Create smart note rejects an anonymous caller")
  void createRejectsAnonymous() throws Exception {
    mockMvc.perform(post(BASE_PATH).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_CREATE))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Create smart note rejects a caller without a tenant authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void createRejectsNonTenantAuthority() throws Exception {
    mockMvc.perform(post(BASE_PATH).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_CREATE))
        .andExpect(status().isForbidden());
  }

  // ─── Edit: anonymous rejected, non-tenant authority forbidden ─────────────

  @Test
  @DisplayName("Patch smart note rejects an anonymous caller")
  void patchRejectsAnonymous() throws Exception {
    mockMvc.perform(patch(BASE_PATH + "/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Patch smart note rejects a caller without a tenant authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void patchRejectsNonTenantAuthority() throws Exception {
    mockMvc.perform(patch(BASE_PATH + "/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }

  // ─── Delete: anonymous rejected, MEMBER forbidden ─────────────────────────

  @Test
  @DisplayName("Delete smart note rejects an anonymous caller")
  void deleteRejectsAnonymous() throws Exception {
    mockMvc.perform(delete(BASE_PATH + "/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Delete smart note rejects a MEMBER caller")
  @WithMockUser(authorities = "MEMBER")
  void deleteRejectsMember() throws Exception {
    mockMvc.perform(delete(BASE_PATH + "/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isForbidden());
  }

  // ─── Pin toggle: anonymous rejected, non-tenant authority forbidden ──────

  @Test
  @DisplayName("Pin smart note rejects an anonymous caller")
  void pinRejectsAnonymous() throws Exception {
    mockMvc.perform(put(BASE_PATH + "/" + UUID_ZERO + "/pinned").header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content("{\"pinned\":true}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Pin smart note rejects a caller without a tenant authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void pinRejectsNonTenantAuthority() throws Exception {
    mockMvc.perform(put(BASE_PATH + "/" + UUID_ZERO + "/pinned").header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content("{\"pinned\":true}"))
        .andExpect(status().isForbidden());
  }

  // ─── Linked asset load: anonymous rejected ──────────────────────────────

  @Test
  @DisplayName("Get linked asset rejects an anonymous caller")
  void getAssetRejectsAnonymous() throws Exception {
    mockMvc.perform(get(BASE_PATH + "/" + UUID_ZERO + "/asset").header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Get linked asset rejects a caller without a tenant authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void getAssetRejectsNonTenantAuthority() throws Exception {
    mockMvc.perform(get(BASE_PATH + "/" + UUID_ZERO + "/asset").header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isForbidden());
  }
}
