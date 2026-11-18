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
 * Security contract for the venue chat endpoints.
 *
 * <p>The routes are member-tier and tenant-scoped: {@code TenantExtractionFilter} resolves the
 * tenant from the {@code X-Tenant-ID} header or the JWT {@code tenant_id} claim, and every method
 * is guarded by {@code @PreAuthorize("hasAnyAuthority('TENANT_OWNER', 'MEMBER')")}.
 */
@SpringBootTest(classes = {CatalogServiceApplication.class})
@ActiveProfiles("test")
class VenueChatRestResourceSecurityTest {

  private static final String TENANT_PATH = "/api/v1/catalog/chat";
  private static final String TENANT_HEADER = "X-Tenant-ID";
  private static final String TENANT_KEY = "xk7f2b9a";
  private static final String UUID_ZERO = "00000000-0000-0000-0000-000000000001";
  private static final String JSON_MESSAGE = "{\"content\":\"hello\"}";

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
  @DisplayName("Send message requires a resolved tenant identifier")
  void chatRejectsMissingTenant() throws Exception {
    mockMvc.perform(post(TENANT_PATH)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_MESSAGE))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Send message rejects an anonymous caller once the tenant is resolved")
  void chatRejectsAnonymous() throws Exception {
    mockMvc.perform(post(TENANT_PATH).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_MESSAGE))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Send message rejects an authenticated caller without tenant authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void chatRejectsMissingTenantAuthority() throws Exception {
    mockMvc.perform(post(TENANT_PATH).header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(JSON_MESSAGE))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("List sessions rejects an anonymous caller once the tenant is resolved")
  void sessionsRejectAnonymous() throws Exception {
    mockMvc.perform(get(TENANT_PATH + "/sessions").header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("List sessions rejects an authenticated caller without tenant authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void sessionsRejectMissingTenantAuthority() throws Exception {
    mockMvc.perform(get(TENANT_PATH + "/sessions").header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("List messages rejects an anonymous caller once the tenant is resolved")
  void messagesRejectAnonymous() throws Exception {
    mockMvc.perform(get(TENANT_PATH + "/sessions/" + UUID_ZERO + "/messages").header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("List messages rejects an authenticated caller without tenant authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void messagesRejectMissingTenantAuthority() throws Exception {
    mockMvc.perform(get(TENANT_PATH + "/sessions/" + UUID_ZERO + "/messages").header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Delete session rejects an anonymous caller once the tenant is resolved")
  void deleteRejectsAnonymous() throws Exception {
    mockMvc.perform(delete(TENANT_PATH + "/sessions/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Delete session rejects an authenticated caller without tenant authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void deleteRejectsMissingTenantAuthority() throws Exception {
    mockMvc.perform(delete(TENANT_PATH + "/sessions/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isForbidden());
  }
}
