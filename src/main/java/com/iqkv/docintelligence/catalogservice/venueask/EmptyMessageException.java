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

import com.iqkv.docintelligence.catalogservice.shared.exception.BadRequestException;

/**
 * Thrown when a chat message or question carries no visible text.
 *
 * <p>Covers input that is null, blank, or made only of invisible characters that normalisation
 * strips away. Mapped to HTTP 400 by {@code GlobalExceptionHandler}.
 */
public class EmptyMessageException extends BadRequestException {

  public EmptyMessageException() {
    super("Message must contain visible text");
  }
}
