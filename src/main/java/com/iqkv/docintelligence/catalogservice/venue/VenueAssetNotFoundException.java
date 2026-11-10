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

import com.iqkv.docintelligence.catalogservice.shared.exception.ResourceNotFoundException;

/**
 * Thrown when a requested venue asset does not exist in the active tenant schema, or exists but is
 * not owned by the venue named in the request path.
 *
 * <p>Caught by {@code GlobalExceptionHandler}, which maps it to HTTP 404 with an RFC-7807 problem
 * detail body.
 */
public class VenueAssetNotFoundException extends ResourceNotFoundException {

  public VenueAssetNotFoundException(final Object id) {
    super("Venue asset not found: " + id);
  }
}
