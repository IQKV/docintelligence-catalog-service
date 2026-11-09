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

/**
 * Venue deck (venue document) upload feature slice.
 *
 * <p>Implements the two-phase presigned MinIO upload for tenant venue deck PDFs: initiate mints a
 * tenant-scoped object key and a presigned PUT URL; the client uploads directly to MinIO; confirm
 * validates the object key, verifies the object exists, persists file metadata with status
 * {@code UPLOADED}, and later transitions to {@code PARSING} / {@code EXTRACTED} / {@code FAILED}
 * as the processing worker picks the document up. The tenant venue and venue document domain
 * models and MyBatis mappers live in the shared {@code com.iqkv:docintelligence-venue} artifact;
 * this slice holds the HTTP surface, the service contract and its implementation, and the DTOs.
 */
package com.iqkv.docintelligence.catalogservice.venuedeck;
