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
 * Security contract for the admin venue asset endpoints.
 *
 * <p>MockMvc is built from the web application context directly so the full security chain runs
 * in the real order. The path is under {@code /admin/}, so {@code TenantExtractionFilter} does not
 * run and {@code SecurityConfig} enforces the {@code PLATFORM_ADMIN} authority — the
 * {@code {tenantKey}} path variable is handled by the controller, not the tenant filter.
 */
@SpringBootTest(classes = {CatalogServiceApplication.class})
@ActiveProfiles("test")
class AdminVenueAssetRestResourceSecurityTest {

  private static final String VENUE_ID = "00000000-0000-0000-0000-000000000001";
  private static final String ASSET_ID = "00000000-0000-0000-0000-000000000002";
  private static final String ADMIN_PATH =
      "/api/v1/catalog/admin/xk7f2b9a/venues/" + VENUE_ID + "/assets";

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
  @DisplayName("Admin venue asset list rejects an anonymous caller")
  void adminListRejectsAnonymous() throws Exception {
    mockMvc.perform(get(ADMIN_PATH))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin venue asset photos rejects an anonymous caller")
  void adminPhotosRejectsAnonymous() throws Exception {
    mockMvc.perform(get(ADMIN_PATH + "/photos"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin venue asset detail rejects an anonymous caller")
  void adminDetailRejectsAnonymous() throws Exception {
    mockMvc.perform(get(ADMIN_PATH + "/" + ASSET_ID))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin venue asset initiate rejects an anonymous caller")
  void adminInitiateRejectsAnonymous() throws Exception {
    mockMvc.perform(post(ADMIN_PATH + "/initiate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fileName\":\"asset.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":1024}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin venue asset confirm rejects an anonymous caller")
  void adminConfirmRejectsAnonymous() throws Exception {
    mockMvc.perform(post(ADMIN_PATH + "/" + ASSET_ID + "/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"objectKey\":\"k\",\"fileName\":\"asset.pdf\",\"assetType\":\"PHOTO\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin venue asset endpoints reject a caller without PLATFORM_ADMIN")
  @WithMockUser(authorities = "MEMBER")
  void adminEndpointsRejectNonPlatformAdmin() throws Exception {
    mockMvc.perform(get(ADMIN_PATH))
        .andExpect(status().isForbidden());
  }
}
