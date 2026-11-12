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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueTextContentDtoMapper;
import com.iqkv.docintelligence.catalogservice.venue.dto.VenueTextContentDtos;
import com.iqkv.docintelligence.venue.VenueTextContent;
import com.iqkv.docintelligence.venue.VenueTextContentMapper;
import com.iqkv.foundation.mybatis.util.PgVectorTypeHandler;
import com.iqkv.foundation.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases over tenant venue text-content rows, backed by {@link VenueTextContentMapper} and, for
 * semantic search, by a {@link JdbcTemplate} with the pgvector {@code <=>} cosine-distance
 * operator.
 *
 * <p>Embedding is refreshed on every write that changes {@code content}, mirroring the pattern
 * used by {@link VenueServiceImpl}. The backfill scheduler in
 * {@code docintelligence-catalog-processing-worker} recovers rows whose embedding is still
 * {@code NULL}.
 */
@Service
@Transactional
public class VenueTextContentServiceImpl implements VenueTextContentService {

  private static final Logger log = LoggerFactory.getLogger(VenueTextContentServiceImpl.class);

  private static final Pattern SAFE_TENANT_KEY = Pattern.compile("^[a-zA-Z0-9_]+$");

  private static final String SEARCH_SELECT = """
      SELECT id, venue_id, source_asset_id, content, created_at, updated_at,
             1 - (embedding <=> ?::vector) AS similarity
      FROM %s
      WHERE embedding IS NOT NULL
      """;

  private static final String SEARCH_ORDER = "\nORDER BY embedding <=> ?::vector\nLIMIT ?";

  private final VenueTextContentMapper mapper;
  private final EmbeddingModel embeddingModel;
  private final JdbcTemplate jdbcTemplate;

  public VenueTextContentServiceImpl(final VenueTextContentMapper mapper,
                                     final EmbeddingModel embeddingModel,
                                     final JdbcTemplate jdbcTemplate) {
    this.mapper = mapper;
    this.embeddingModel = embeddingModel;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public VenueTextContentDtos.VenueTextContentResponse create(
      final VenueTextContentDtos.VenueTextContentWriteRequest request) {
    final VenueTextContent tc = VenueTextContentDtoMapper.toEntity(request);
    mapper.insert(tc);
    embedQuietly(tc);
    return VenueTextContentDtoMapper.toResponse(require(tc.getId()));
  }

  @Override
  @Transactional(readOnly = true)
  public VenueTextContentDtos.VenueTextContentResponse getById(final UUID id) {
    return VenueTextContentDtoMapper.toResponse(require(id));
  }

  @Override
  @Transactional(readOnly = true)
  public VenueTextContentDtos.VenueTextContentListResponse listByVenue(final UUID venueId) {
    final var items = mapper.findByVenueId(venueId);
    return new VenueTextContentDtos.VenueTextContentListResponse(
        items.stream().map(VenueTextContentDtoMapper::toResponse).toList(),
        items.size());
  }

  @Override
  @Transactional(readOnly = true)
  public VenueTextContentDtos.VenueTextContentListResponse search(
      final UUID venueId, final String search, final int limit, final int offset) {
    final var items = mapper.search(venueId, search, limit, offset);
    return new VenueTextContentDtos.VenueTextContentListResponse(
        items.stream().map(VenueTextContentDtoMapper::toResponse).toList(),
        mapper.countSearch(venueId, search));
  }

  @Override
  public VenueTextContentDtos.VenueTextContentResponse patch(
      final UUID id, final VenueTextContentDtos.VenueTextContentPatchRequest request) {
    final VenueTextContent tc = require(id);
    final String previousContent = tc.getContent();
    VenueTextContentDtoMapper.applyPatch(tc, request);
    mapper.updateContent(id, tc.getContent());
    if (!tc.getContent().equals(previousContent)) {
      embedQuietly(tc);
    }
    return VenueTextContentDtoMapper.toResponse(require(id));
  }

  @Override
  public VenueTextContentDtos.VenueTextContentResponse linkVenue(final UUID id, final UUID venueId) {
    require(id);
    mapper.linkVenue(id, venueId);
    return VenueTextContentDtoMapper.toResponse(require(id));
  }

  @Override
  public void delete(final UUID id) {
    require(id);
    mapper.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public VenueTextContentDtos.VenueTextContentSemanticSearchResponse semanticSearch(
      final String query, final UUID venueId, final int limit) {
    log.info("VenueTextContent semantic search: query='{}', venueId={}, limit={}", query, venueId, limit);
    final float[] embedding = embeddingModel.embed(query);
    final String vectorLiteral = PgVectorTypeHandler.toVectorLiteral(embedding);

    final String table = tenantTable();
    final String selectSql = SEARCH_SELECT.formatted(table);
    final String countSql = "SELECT COUNT(*) FROM " + table + " WHERE embedding IS NOT NULL";

    final List<Binder> binders = new ArrayList<>();
    binders.add((ps, idx) -> ps.setObject(idx, vectorLiteral, Types.OTHER)); // similarity projection
    final String venueFilter = venueId != null ? "\n  AND venue_id = ?" : "";
    if (venueId != null) {
      final UUID capturedVenueId = venueId;
      binders.add((ps, idx) -> ps.setObject(idx, capturedVenueId.toString(), Types.OTHER));
    }
    binders.add((ps, idx) -> ps.setObject(idx, vectorLiteral, Types.OTHER)); // ORDER BY distance
    binders.add((ps, idx) -> ps.setInt(idx, limit));

    final List<Binder> countBinders = venueId != null
        ? List.of((ps, idx) -> ps.setObject(idx, venueId.toString(), Types.OTHER))
        : List.of();

    final List<VenueTextContentDtos.VenueTextContentSemanticSearchResult> items =
        jdbcTemplate.query(
            selectSql + venueFilter + SEARCH_ORDER,
            ps -> bindAll(ps, binders),
            (rs, rowNum) -> {
              final String vid = rs.getString("venue_id");
              final String said = rs.getString("source_asset_id");
              return new VenueTextContentDtos.VenueTextContentSemanticSearchResult(
                  UUID.fromString(rs.getString("id")),
                  vid == null ? null : UUID.fromString(vid),
                  said == null ? null : UUID.fromString(said),
                  rs.getString("content"),
                  toInstant(rs.getTimestamp("created_at")),
                  toInstant(rs.getTimestamp("updated_at")),
                  rs.getDouble("similarity"));
            });

    final List<Long> counts = jdbcTemplate.query(
        countSql + (venueId != null ? "\n  AND venue_id = ?" : ""),
        ps -> bindAll(ps, countBinders),
        (rs, rowNum) -> rs.getLong(1));

    final long total = counts.isEmpty() ? 0L : counts.get(0);
    log.info("VenueTextContent semantic search finished: {} item(s) of {} total", items.size(), total);
    return new VenueTextContentDtos.VenueTextContentSemanticSearchResponse(items, total);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private VenueTextContent require(final UUID id) {
    return mapper.findById(id)
        .orElseThrow(() -> new VenueTextContentNotFoundException(id));
  }

  private void embedQuietly(final VenueTextContent tc) {
    try {
      final float[] vector = embeddingModel.embed(tc.getContent());
      mapper.updateEmbedding(tc.getId(), vector);
      log.debug("embedded VenueTextContent {} ({} dims)", tc.getId(), vector.length);
    } catch (final RuntimeException ex) {
      log.warn("embedding skipped for VenueTextContent {} — Ollama unavailable or slow: {}",
          tc.getId(), ex.getMessage());
    }
  }

  private String tenantTable() {
    final String tenantKey = TenantContext.getCurrentTenant();
    if (tenantKey == null || !SAFE_TENANT_KEY.matcher(tenantKey).matches()) {
      throw new IllegalStateException("No valid tenant context for VenueTextContent semantic search");
    }
    return "t_" + tenantKey + ".venue_text_contents";
  }

  private static void bindAll(final PreparedStatement ps, final List<Binder> binders) throws SQLException {
    int index = 1;
    for (final Binder binder : binders) {
      binder.bind(ps, index++);
    }
  }

  private static Instant toInstant(final Timestamp ts) {
    return ts != null ? ts.toInstant() : null;
  }

  @FunctionalInterface
  private interface Binder {

    void bind(PreparedStatement ps, int index) throws SQLException;
  }
}
