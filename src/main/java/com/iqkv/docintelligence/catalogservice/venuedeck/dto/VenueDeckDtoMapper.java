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

package com.iqkv.docintelligence.catalogservice.venuedeck.dto;

import com.iqkv.docintelligence.venue.VenueDocument;

/**
 * Static mapper between the {@link VenueDocument} domain model and the {@link VenueDeckDtos}
 * response records. No Spring bean — stateless conversion only.
 */
public final class VenueDeckDtoMapper {

  private VenueDeckDtoMapper() {
  }

  public static VenueDeckDtos.VenueDocumentResponse toResponse(final VenueDocument document) {
    return new VenueDeckDtos.VenueDocumentResponse(
        document.getId(),
        document.getFileName(),
        document.getContentType(),
        document.getSizeBytes(),
        document.getObjectKey(),
        document.getStatus(),
        document.getUploadedBy(),
        document.getCreatedAt(),
        document.getUpdatedAt());
  }
}
