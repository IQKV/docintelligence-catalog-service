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

package com.iqkv.docintelligence.catalogservice.infrastructure.security;

import com.iqkv.foundation.tenancy.TenantContext;

/**
 * Tenant-key helpers for the workspace-type decisions the catalogue makes.
 */
public final class TenantKeys {

  /**
   * Tenant key of the shared platform tenant that backs every user's personal workspace.
   * Mirrors {@code PLATFORM_TENANT_KEY} in {@code foundation-iam-service} so both sides agree on
   * which tenant is the personal one.
   */
  public static final String PLATFORM_TENANT_KEY = "platform";

  private TenantKeys() {
  }

  /**
   * Whether the current request runs in a personal workspace — the shared {@code t_platform}
   * schema, where venues from different users must never mix.
   *
   * @return {@code true} when the active tenant is the platform tenant; {@code false} when another
   *     tenant is active or no tenant context is bound to the thread
   */
  public static boolean isPersonalWorkspace() {
    try {
      return PLATFORM_TENANT_KEY.equals(TenantContext.getCurrentTenant());
    } catch (final IllegalStateException ex) {
      // No request-scoped tenant on this thread (startup, background jobs, unit tests).
      return false;
    }
  }
}
