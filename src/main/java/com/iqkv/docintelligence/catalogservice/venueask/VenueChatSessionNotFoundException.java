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

import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.shared.exception.ResourceNotFoundException;

/**
 * Thrown when a chat session does not exist in the active tenant schema, or is not owned by the
 * requesting user. Mapped to HTTP 404 by {@code GlobalExceptionHandler}.
 */
public class VenueChatSessionNotFoundException extends ResourceNotFoundException {

  public VenueChatSessionNotFoundException(final UUID id) {
    super("Chat session not found: " + id);
  }
}
