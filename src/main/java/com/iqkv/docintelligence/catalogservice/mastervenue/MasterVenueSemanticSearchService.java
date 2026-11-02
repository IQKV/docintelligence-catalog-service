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

package com.iqkv.docintelligence.catalogservice.mastervenue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.mastervenue.dto.MasterVenueDtos;
import com.iqkv.foundation.mybatis.util.PgVectorTypeHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Semantic (vector) search over the master venue catalog.
 *
 * <p>Embeds the incoming query string via {@link VenueEmbeddingService}, then runs an
 * approximate nearest-neighbour query using the pgvector {@code <=>} cosine-distance
 * operator against the HNSW index on {@code public.master_venue.embedding}.
 *
 * <p>MyBatis is bypassed here because the {@code ::vector} cast on a bound parameter
 * cannot be expressed in MyBatis XML — the PostgreSQL driver needs the value passed as a
 * plain {@code Types.OTHER} string, which {@link JdbcTemplate} supports natively via a
 * {@code PreparedStatementSetter}.
 *
 * <p>Rows without an embedding ({@code embedding IS NULL}) are excluded from results.
 * The optional {@code city} parameter is applied as a hard SQL predicate before the ANN
 * sort so the index scan is narrowed without degrading recall.
 */
@Service
public class MasterVenueSemanticSearchService {

  private static final String SEARCH_SQL = """
      SELECT id, name, address, city, location_lat, location_lng,
             metadata, confidence, source, primary_photo_url,
             created_at, updated_at,
             1 - (embedding <=> ?::vector) AS similarity
      FROM public.master_venue
      WHERE embedding IS NOT NULL
        AND (? IS NULL OR LOWER(city) = LOWER(?))
      ORDER BY embedding <=> ?::vector
      LIMIT ?
      """;

  private static final String COUNT_SQL = """
      SELECT COUNT(*)
      FROM public.master_venue
      WHERE embedding IS NOT NULL
        AND (? IS NULL OR LOWER(city) = LOWER(?))
      """;

  private final JdbcTemplate jdbcTemplate;

  private final VenueEmbeddingService embeddingService;

  public MasterVenueSemanticSearchService(final JdbcTemplate jdbcTemplate,
                                          final VenueEmbeddingService embeddingService) {
    this.jdbcTemplate = jdbcTemplate;
    this.embeddingService = embeddingService;
  }

  /**
   * Runs a semantic search for {@code query} and returns up to {@code limit} results
   * ranked by cosine similarity descending.
   *
   * @param query natural-language search query
   * @param city  optional exact city filter; {@code null} disables the filter
   * @param limit maximum number of results to return
   * @return search response with ranked items and a total count of eligible venues
   */
  public MasterVenueDtos.SemanticSearchResponse search(final String query,
                                                       final String city,
                                                       final int limit) {
    final float[] queryVector = embeddingService.embedQuery(query);
    final String vectorLiteral = PgVectorTypeHandler.toVectorLiteral(queryVector);

    final List<MasterVenueDtos.SemanticSearchResult> items = jdbcTemplate.query(
        SEARCH_SQL,
        ps -> {
          ps.setObject(1, vectorLiteral, java.sql.Types.OTHER);   // embedding <=> ?::vector (ORDER BY)
          ps.setObject(2, city, java.sql.Types.VARCHAR);           // city IS NULL check
          ps.setObject(3, city, java.sql.Types.VARCHAR);           // LOWER(city) = LOWER(?)
          ps.setObject(4, vectorLiteral, java.sql.Types.OTHER);   // embedding <=> ?::vector (SELECT)
          ps.setInt(5, limit);
        },
        (rs, rowNum) -> new MasterVenueDtos.SemanticSearchResult(
            UUID.fromString(rs.getString("id")),
            rs.getString("name"),
            rs.getString("address"),
            rs.getString("city"),
            rs.getBigDecimal("location_lat"),
            rs.getBigDecimal("location_lng"),
            rs.getString("metadata"),
            rs.getBigDecimal("confidence"),
            rs.getString("source"),
            rs.getString("primary_photo_url"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")),
            rs.getDouble("similarity")
        )
    );

    final long total = countEligible(city);
    return new MasterVenueDtos.SemanticSearchResponse(items, total);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private long countEligible(final String city) {
    final Long count = jdbcTemplate.queryForObject(
        COUNT_SQL,
        Long.class,
        city, city
    );
    return count != null ? count : 0L;
  }

  private static java.time.Instant toInstant(final Timestamp ts) {
    return ts != null ? ts.toInstant() : null;
  }
}
