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
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence over the tenant {@code venue_chat_sessions} table.
 */
@Mapper
public interface VenueChatSessionMapper {

  void insert(VenueChatSession session);

  void update(VenueChatSession session);

  void delete(@Param("id") UUID id);

  Optional<VenueChatSession> findById(@Param("id") UUID id);

  List<VenueChatSession> findByUserId(@Param("userId") UUID userId,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

  long countByUserId(@Param("userId") UUID userId);
}
