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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Security contract for the venue ask endpoints.
 *
 * <p>The endpoints are member-tier and tenant-scoped: {@code TenantExtractionFilter} resolves the
 * tenant from the {@code X-Tenant-ID} header or the JWT {@code tenant_id} claim, and every method
 * is guarded by {@code @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'MEMBER')")}.
 */
@SpringBootTest(classes = {CatalogServiceApplication.class})
@ActiveProfiles("test")
class VenueAskRestResourceSecurityTest {

  private static final String TENANT_PATH = "/api/v1/catalog/venues";
  private static final String TENANT_HEADER = "X-Tenant-ID";
  private static final String TENANT_KEY = "xk7f2b9a";
  private static final String UUID_ZERO = "00000000-0000-0000-0000-000000000001";
  private static final String JSON_ASK = "{\"question\":\"what is the capacity?\"}";

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
  @DisplayName("Ask about one venue requires a resolved tenant identifier")
  void askVenueRejectsMissingTenant() throws Exception {
    mockMvc.perform(post(TENANT_PATH + "/" + UUID_ZERO + "/ask")
            .contentType(MediaType.APPLICATION_JSON).content(JSON_ASK))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Ask about one venue rejects an anonymous caller once the tenant is resolved")
  void askVenueRejectsAnonymous() throws Exception {
    mockMvc.perform(post(TENANT_PATH + "/" + UUID_ZERO + "/ask").header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_ASK))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Ask about one venue rejects an authenticated caller without tenant authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void askVenueRejectsMissingTenantAuthority() throws Exception {
    mockMvc.perform(post(TENANT_PATH + "/" + UUID_ZERO + "/ask").header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_ASK))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Venue context requires a resolved tenant identifier")
  void venueContextRejectsMissingTenant() throws Exception {
    mockMvc.perform(get(TENANT_PATH + "/" + UUID_ZERO + "/context"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Venue context rejects an anonymous caller once the tenant is resolved")
  void venueContextRejectsAnonymous() throws Exception {
    mockMvc.perform(get(TENANT_PATH + "/" + UUID_ZERO + "/context").header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Venue context rejects an authenticated caller without tenant authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void venueContextRejectsMissingTenantAuthority() throws Exception {
    mockMvc.perform(get(TENANT_PATH + "/" + UUID_ZERO + "/context").header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Ask about the venue list requires a resolved tenant identifier")
  void askVenuesRejectsMissingTenant() throws Exception {
    mockMvc.perform(post(TENANT_PATH + "/ask")
            .contentType(MediaType.APPLICATION_JSON).content(JSON_ASK))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Ask about the venue list rejects an anonymous caller once the tenant is resolved")
  void askVenuesRejectsAnonymous() throws Exception {
    mockMvc.perform(post(TENANT_PATH + "/ask").header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_ASK))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Ask about the venue list rejects an authenticated caller without tenant authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void askVenuesRejectsMissingTenantAuthority() throws Exception {
    mockMvc.perform(post(TENANT_PATH + "/ask").header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_ASK))
        .andExpect(status().isForbidden());
  }
}
