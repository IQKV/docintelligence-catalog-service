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

import java.util.UUID;

/**
 * Published after a venue asset upload is confirmed and committed, announcing work for
 * {@code docintelligence-catalog-processing-worker}: read the stored object and derive a venue
 * record from it.
 *
 * <p>{@code venueId} is {@code null} for the import flow (the venue does not exist yet) and the
 * worker sets it once it has created and linked the venue. {@code tenantKey} is carried because the
 * consumer has no request context to resolve it from.
 */
public record VenueAssetUploadedEvent(
    String tenantKey,
    UUID assetId,
    UUID venueId,
    String assetType,
    String contentType,
    String fileName,
    String s3Key) {
}
