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

import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venuedeck.dto.VenueDeckDtos;

/**
 * Venue deck (uploaded venue PDF) operations.
 *
 * <p>Implements the two-phase presigned upload flow: {@link #initiateUpload} mints a
 * tenant-scoped object key and a presigned PUT URL (no row is persisted), then
 * {@link #confirmUpload} validates the object key, verifies the object exists in storage, and
 * persists a {@code venue_documents} row with status {@code UPLOADED}. List and detail reads are
 * tenant-scoped (the active tenant schema isolates rows).
 */
public interface VenueDeckService {

  VenueDeckDtos.InitiateUploadResponse initiateUpload(VenueDeckDtos.InitiateUploadRequest request,
                                                      UUID uploadedBy);

  VenueDeckDtos.VenueDocumentResponse confirmUpload(UUID documentId,
                                                     VenueDeckDtos.ConfirmUploadRequest request,
                                                     UUID uploadedBy);

  VenueDeckDtos.VenueDocumentListResponse listDocuments(int limit, int offset);

  VenueDeckDtos.VenueDocumentResponse getDocument(UUID id);
}
