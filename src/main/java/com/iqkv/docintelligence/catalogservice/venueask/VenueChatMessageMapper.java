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

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence over the tenant {@code venue_chat_messages} table.
 */
@Mapper
public interface VenueChatMessageMapper {

  void insert(VenueChatMessage message);

  List<VenueChatMessage> findBySessionId(@Param("sessionId") UUID sessionId,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

  /**
   * The newest turns of one session, newest first — the caller reverses them into speaking order.
   */
  List<VenueChatMessage> findRecentBySessionId(@Param("sessionId") UUID sessionId,
                                               @Param("limit") int limit);

  long countBySessionId(@Param("sessionId") UUID sessionId);

  void deleteBySessionId(@Param("sessionId") UUID sessionId);
}
