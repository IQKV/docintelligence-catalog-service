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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Security contract for the two master venue API tiers.
 *
 * <p>MockMvc is built from the web application context directly rather than through the web test
 * autoconfiguration, so the full security chain — {@code CorrelationIdFilter},
 * {@code TenantExtractionFilter} and the resource-server chain — is exercised in the real order.
 */
@SpringBootTest(classes = {CatalogServiceApplication.class})
@ActiveProfiles("test")
class MasterVenueRestResourceSecurityTest {

  private static final String TENANT_PATH = "/api/v1/catalog/master-venues";
  private static final String ADMIN_PATH = "/api/v1/catalog/admin/master-venues";
  private static final String TENANT_HEADER = "X-Tenant-ID";
  private static final String TENANT_KEY = "xk7f2b9a";

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
  @DisplayName("Admin search rejects an anonymous caller")
  void adminSearchRejectsAnonymous() throws Exception {
    mockMvc.perform(get(ADMIN_PATH))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin create rejects an anonymous caller")
  void adminCreateRejectsAnonymous() throws Exception {
    mockMvc.perform(post(ADMIN_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Grand Hall\",\"source\":\"platform_seed\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin delete rejects an anonymous caller")
  void adminDeleteRejectsAnonymous() throws Exception {
    mockMvc.perform(delete(ADMIN_PATH + "/00000000-0000-0000-0000-000000000001"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin endpoints reject a caller without PLATFORM_ADMIN")
  @WithMockUser(authorities = "MEMBER")
  void adminEndpointsRejectNonPlatformAdmin() throws Exception {
    mockMvc.perform(get(ADMIN_PATH))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Admin asset listing rejects an anonymous caller")
  void adminAssetsRejectAnonymous() throws Exception {
    mockMvc.perform(get(ADMIN_PATH + "/00000000-0000-0000-0000-000000000001/assets"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin photo listing rejects an anonymous caller")
  void adminPhotosRejectAnonymous() throws Exception {
    mockMvc.perform(get(ADMIN_PATH + "/00000000-0000-0000-0000-000000000001/photos"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Tenant search requires a resolved tenant identifier")
  void tenantSearchRejectsMissingTenant() throws Exception {
    mockMvc.perform(get(TENANT_PATH))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Tenant search rejects an anonymous caller once the tenant is resolved")
  void tenantSearchRejectsAnonymous() throws Exception {
    mockMvc.perform(get(TENANT_PATH).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Tenant asset listing rejects an anonymous caller once the tenant is resolved")
  void tenantAssetsRejectAnonymous() throws Exception {
    mockMvc.perform(get(TENANT_PATH + "/00000000-0000-0000-0000-000000000001/assets")
            .header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Tenant photo listing rejects an anonymous caller once the tenant is resolved")
  void tenantPhotosRejectAnonymous() throws Exception {
    mockMvc.perform(get(TENANT_PATH + "/00000000-0000-0000-0000-000000000001/photos")
            .header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Tenant search rejects an authenticated caller without MEMBER authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void tenantSearchRejectsMissingMemberAuthority() throws Exception {
    mockMvc.perform(get(TENANT_PATH).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isForbidden());
  }
}
