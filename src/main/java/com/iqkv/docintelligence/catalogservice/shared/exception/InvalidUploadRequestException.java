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

package com.iqkv.docintelligence.catalogservice.shared.exception;

/**
 * Thrown when a venue deck upload request fails validation at a trust boundary — unsupported
 * content type, file size over the configured maximum, an object key that does not match the
 * document being confirmed, or an object that was not actually uploaded to storage.
 *
 * <p>Caught by {@code GlobalExceptionHandler}, which maps it to HTTP 400 with an RFC-7807 problem
 * detail body.
 */
public class InvalidUploadRequestException extends RuntimeException {

  public InvalidUploadRequestException(final String message) {
    super(message);
  }
}
