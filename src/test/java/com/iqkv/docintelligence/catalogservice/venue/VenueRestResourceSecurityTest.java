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
 * Security contract for the tenant-facing venue endpoints.
 *
 * <p>MockMvc is built from the web application context directly so the full security chain —
 * {@code CorrelationIdFilter}, {@code TenantExtractionFilter} and the resource-server chain —
 * runs in the real order. Venue endpoints are member-tier and tenant-scoped: they are not under
 * {@code /admin/} so {@code TenantExtractionFilter} resolves the tenant from the
 * {@code X-Tenant-ID} header or the JWT {@code tenant_id} claim, and the class-level
 * {@code @PreAuthorize("hasAuthority('MEMBER')")} guards every method.
 */
@SpringBootTest(classes = {CatalogServiceApplication.class})
@ActiveProfiles("test")
class VenueRestResourceSecurityTest {

  private static final String TENANT_PATH = "/api/v1/catalog/venues";
  private static final String TENANT_HEADER = "X-Tenant-ID";
  private static final String TENANT_KEY = "xk7f2b9a";
  private static final String UUID_ZERO = "00000000-0000-0000-0000-000000000001";

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
  @DisplayName("Tenant venue search requires a resolved tenant identifier")
  void tenantSearchRejectsMissingTenant() throws Exception {
    mockMvc.perform(get(TENANT_PATH))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Tenant venue search rejects an anonymous caller once the tenant is resolved")
  void tenantSearchRejectsAnonymous() throws Exception {
    mockMvc.perform(get(TENANT_PATH).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Tenant venue detail rejects an anonymous caller once the tenant is resolved")
  void tenantDetailRejectsAnonymous() throws Exception {
    mockMvc.perform(get(TENANT_PATH + "/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Tenant venue search rejects an authenticated caller without MEMBER authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void tenantSearchRejectsMissingMemberAuthority() throws Exception {
    mockMvc.perform(get(TENANT_PATH).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isForbidden());
  }

  // ─── Write surface: anonymous callers are rejected ────────────────────────

  private static final String JSON_WRITE = "{\"name\":\"Test Venue\"}";
  private static final String JSON_BULK = "{\"ids\":[\"" + UUID_ZERO + "\"]}";

  @Test
  @DisplayName("Create venue rejects an anonymous caller")
  void createRejectsAnonymous() throws Exception {
    mockMvc.perform(post(TENANT_PATH).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_WRITE))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Replace venue rejects an anonymous caller")
  void replaceRejectsAnonymous() throws Exception {
    mockMvc.perform(put(TENANT_PATH + "/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_WRITE))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Patch venue rejects an anonymous caller")
  void patchRejectsAnonymous() throws Exception {
    mockMvc.perform(patch(TENANT_PATH + "/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Delete venue rejects an anonymous caller")
  void deleteRejectsAnonymous() throws Exception {
    mockMvc.perform(delete(TENANT_PATH + "/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Bulk delete venues rejects an anonymous caller")
  void bulkDeleteRejectsAnonymous() throws Exception {
    mockMvc.perform(delete(TENANT_PATH).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_BULK))
        .andExpect(status().isUnauthorized());
  }

  // ─── Write surface: MEMBER authority is not sufficient ───────────────────

  @Test
  @DisplayName("Create venue rejects a MEMBER caller")
  @WithMockUser(authorities = "MEMBER")
  void createRejectsMember() throws Exception {
    mockMvc.perform(post(TENANT_PATH).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_WRITE))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Replace venue rejects a MEMBER caller")
  @WithMockUser(authorities = "MEMBER")
  void replaceRejectsMember() throws Exception {
    mockMvc.perform(put(TENANT_PATH + "/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_WRITE))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Patch venue rejects a MEMBER caller")
  @WithMockUser(authorities = "MEMBER")
  void patchRejectsMember() throws Exception {
    mockMvc.perform(patch(TENANT_PATH + "/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Delete venue rejects a MEMBER caller")
  @WithMockUser(authorities = "MEMBER")
  void deleteRejectsMember() throws Exception {
    mockMvc.perform(delete(TENANT_PATH + "/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Bulk delete venues rejects a MEMBER caller")
  @WithMockUser(authorities = "MEMBER")
  void bulkDeleteRejectsMember() throws Exception {
    mockMvc.perform(delete(TENANT_PATH).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_BULK))
        .andExpect(status().isForbidden());
  }
}
