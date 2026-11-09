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

package com.iqkv.docintelligence.catalogservice.venuedeck;

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
 * Security contract for the venue deck endpoints.
 *
 * <p>MockMvc is built from the web application context directly so the full security chain —
 * {@code CorrelationIdFilter}, {@code TenantExtractionFilter} and the resource-server chain — runs
 * in the real order. Venue deck endpoints are member-tier and tenant-scoped: they are not under
 * {@code /admin/} so {@code TenantExtractionFilter} does not skip them, and the class-level
 * {@code @PreAuthorize("hasAuthority('MEMBER')")} guards every method.
 */
@SpringBootTest(classes = {CatalogServiceApplication.class})
@ActiveProfiles("test")
class VenueDeckRestResourceSecurityTest {

  private static final String BASE_PATH = "/api/v1/catalog/venue-decks";
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
  @DisplayName("Initiate requires a resolved tenant identifier")
  void initiateRejectsMissingTenant() throws Exception {
    mockMvc.perform(post(BASE_PATH + "/initiate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fileName\":\"deck.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":1024}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Initiate rejects an anonymous caller once the tenant is resolved")
  void initiateRejectsAnonymous() throws Exception {
    mockMvc.perform(post(BASE_PATH + "/initiate")
            .header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fileName\":\"deck.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":1024}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Confirm rejects an anonymous caller once the tenant is resolved")
  void confirmRejectsAnonymous() throws Exception {
    mockMvc.perform(post(BASE_PATH + "/" + UUID_ZERO + "/confirm")
            .header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"objectKey\":\"k\",\"fileName\":\"deck.pdf\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("List rejects an anonymous caller once the tenant is resolved")
  void listRejectsAnonymous() throws Exception {
    mockMvc.perform(get(BASE_PATH).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Detail rejects an anonymous caller once the tenant is resolved")
  void detailRejectsAnonymous() throws Exception {
    mockMvc.perform(get(BASE_PATH + "/" + UUID_ZERO).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("List rejects an authenticated caller without MEMBER authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void listRejectsMissingMemberAuthority() throws Exception {
    mockMvc.perform(get(BASE_PATH).header(TENANT_HEADER, TENANT_KEY))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Initiate rejects an authenticated caller without MEMBER authority")
  @WithMockUser(authorities = "PLATFORM_ADMIN")
  void initiateRejectsMissingMemberAuthority() throws Exception {
    mockMvc.perform(post(BASE_PATH + "/initiate")
            .header(TENANT_HEADER, TENANT_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fileName\":\"deck.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":1024}"))
        .andExpect(status().isForbidden());
  }
}
