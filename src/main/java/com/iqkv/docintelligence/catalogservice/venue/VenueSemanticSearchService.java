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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.venue.dto.VenueDtos;
import com.iqkv.foundation.mybatis.util.PgVectorTypeHandler;
import com.iqkv.foundation.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Semantic (vector) search over the tenant venues, mirroring
 * {@code MasterVenueSemanticSearchService} for the tenant-scoped {@code venues} table.
 *
 * <p>The incoming query is first handed to {@link VenueQueryPlanner}, which asks the chat model
 * to split it into hard filters (city, venue type, capacity, catering policy, amenities,
 * accessibility) and a residual free-text query. The hard filters become JSONB {@code WHERE}
 * predicates that narrow the candidate set, and only the residual text is embedded — so a long,
 * multi-constraint query no longer dilutes the vector match. Only venues already carrying an
 * embedding are considered; the planner disabled or unreachable degrades to embedding the raw
 * query with no hard filters.
 *
 * <p>Unlike the master catalog (a single {@code public.master_venue} table), tenant venues live in
 * a {@code t_{tenantKey}} schema. The query is schema-qualified with the active tenant key read
 * from {@link TenantContext} and validated against the same identifier rule the
 * {@code MyBatisSchemaInterceptor} enforces, so the pgvector {@code <=>} operator resolves the
 * per-tenant HNSW index. The controller sets the tenant context from the {@code {tenantKey}}
 * path variable before this service runs.
 */
@Service
class VenueSemanticSearchService {

  private static final Logger log = LoggerFactory.getLogger(VenueSemanticSearchService.class);

  /**
   * Identifier rule shared with {@code MyBatisSchemaInterceptor}; the tenant key is interpolated.
   */
  private static final java.util.regex.Pattern SAFE_TENANT_KEY =
      java.util.regex.Pattern.compile("^[a-zA-Z0-9_]+$");

  private static final String SEARCH_SELECT = """
      SELECT id, name, address, city, location_lat, location_lng,
             metadata, confidence, source, master_venue_id,
             created_at, updated_at,
             1 - (embedding <=> ?::vector) AS similarity
      FROM %s
      WHERE embedding IS NOT NULL
      """;

  private static final String SEARCH_ORDER = "\nORDER BY embedding <=> ?::vector\nLIMIT ?";

  /**
   * Guards the {@code max_total} cast: metadata is free-form JSONB, so a non-numeric value would
   * otherwise abort the whole query. The {@code CASE} yields SQL {@code NULL} for anything that is
   * not a positive number, which the {@code >=} comparison then treats as "no match".
   */
  private static final String CAPACITY_NUMERIC =
      "CASE WHEN metadata->'capacity'->>'max_total' ~ '^[0-9]+(\\.[0-9]+)?$' "
          + "THEN (metadata->'capacity'->>'max_total')::numeric END";

  private final JdbcTemplate jdbcTemplate;

  private final EmbeddingModel embeddingModel;

  private final VenueQueryPlanner queryPlanner;

  VenueSemanticSearchService(final JdbcTemplate jdbcTemplate,
                             final EmbeddingModel embeddingModel,
                             final VenueQueryPlanner queryPlanner) {
    this.jdbcTemplate = jdbcTemplate;
    this.embeddingModel = embeddingModel;
    this.queryPlanner = queryPlanner;
  }

  /**
   * Runs a semantic search for {@code query} and returns up to {@code limit} results
   * ranked by cosine similarity descending.
   *
   * @param query natural-language search query
   * @param city  optional explicit city filter; takes precedence over the city the planner
   *              extracts; {@code null} leaves the decision to the planner
   * @param limit maximum number of results to return
   * @return search response with ranked items, a total count of matching venues, and the hard
   *     filters that were applied
   */
  public VenueDtos.VenueSemanticSearchResponse search(final String query,
                                                      final String city,
                                                      final int limit) {
    log.info("Semantic search started: query='{}', city={}, limit={}", query, city, limit);
    final VenueQueryPlanner.QueryPlan plan = queryPlanner.plan(query);
    final String effectiveCity = city != null ? city : plan.city();
    final Boolean accessible = Boolean.TRUE.equals(plan.accessible()) ? Boolean.TRUE : null;
    final Filter filter = buildFilter(effectiveCity, plan.venueTypes(), plan.minCapacity(),
        plan.maxCapacity(), plan.cateringPolicy(), plan.amenities(), accessible);

    log.info("Query plan resolved: city={}, venueTypes={}, capacity=[{}, {}], cateringPolicy={}, "
             + "amenities={}, accessible={}, residualQuery='{}'",
        effectiveCity, plan.venueTypes(), plan.minCapacity(), plan.maxCapacity(),
        plan.cateringPolicy(), plan.amenities(), accessible, plan.semanticQuery());

    final float[] embedding = embeddingModel.embed(plan.semanticQuery());
    final String vectorLiteral = PgVectorTypeHandler.toVectorLiteral(embedding);
    log.info("Embedded the residual query into a {}-dimensional vector", embedding.length);
    if (filter.sql().isEmpty()) {
      log.info("No hard filters resolved — every embedded venue is ranked by cosine similarity");
    } else {
      log.info("Applying hard-filter predicates:{}", filter.sql());
    }

    final String venuesTable = tenantVenuesTable();
    final String searchSelect = SEARCH_SELECT.formatted(venuesTable);
    final String countSelect = "SELECT COUNT(*) FROM " + venuesTable + " WHERE embedding IS NOT NULL";

    final List<Binder> binders = new ArrayList<>();
    binders.add((ps, index) -> ps.setObject(index, vectorLiteral, Types.OTHER)); // similarity projection
    binders.addAll(filter.binders());
    binders.add((ps, index) -> ps.setObject(index, vectorLiteral, Types.OTHER)); // ORDER BY distance
    binders.add((ps, index) -> ps.setInt(index, limit));

    final List<VenueDtos.VenueSemanticSearchResult> items = jdbcTemplate.query(
        searchSelect + filter.sql() + SEARCH_ORDER,
        ps -> bindAll(ps, binders),
        (rs, rowNum) -> {
          final String masterVenueId = rs.getString("master_venue_id");
          return new VenueDtos.VenueSemanticSearchResult(
              UUID.fromString(rs.getString("id")),
              rs.getString("name"),
              rs.getString("address"),
              rs.getString("city"),
              rs.getBigDecimal("location_lat"),
              rs.getBigDecimal("location_lng"),
              rs.getString("metadata"),
              rs.getBigDecimal("confidence"),
              rs.getString("source"),
              masterVenueId == null ? null : UUID.fromString(masterVenueId),
              toInstant(rs.getTimestamp("created_at")),
              toInstant(rs.getTimestamp("updated_at")),
              rs.getDouble("similarity"));
        }
    );

    final List<Long> counts = jdbcTemplate.query(
        countSelect + filter.sql(),
        ps -> bindAll(ps, filter.binders()),
        (rs, rowNum) -> rs.getLong(1));

    final VenueDtos.VenueSemanticSearchFilters appliedFilters = new VenueDtos.VenueSemanticSearchFilters(
        effectiveCity, plan.venueTypes(), plan.minCapacity(), plan.maxCapacity(),
        plan.cateringPolicy(), plan.amenities(), accessible);

    final long totalElements = counts.isEmpty() ? 0L : counts.get(0);
    log.info("Semantic search finished: {} item(s) returned out of {} venue(s) matching the filters",
        items.size(), totalElements);

    return new VenueDtos.VenueSemanticSearchResponse(items, totalElements, appliedFilters);
  }

  // ── Filter construction ───────────────────────────────────────────────────

  private static Filter buildFilter(final String city,
                                     final List<String> venueTypes,
                                     final Integer minCapacity,
                                     final Integer maxCapacity,
                                     final String cateringPolicy,
                                     final List<String> amenities,
                                     final Boolean accessible) {
    final StringBuilder sql = new StringBuilder();
    final List<Binder> binders = new ArrayList<>();

    if (city != null) {
      sql.append("\n  AND LOWER(city) = LOWER(?)");
      binders.add((ps, index) -> ps.setObject(index, city, Types.VARCHAR));
    }
    addArrayFilter(sql, binders, "metadata->'venue_type'", venueTypes);
    if (minCapacity != null) {
      sql.append("\n  AND (").append(CAPACITY_NUMERIC).append(") >= ?");
      binders.add((ps, index) -> ps.setObject(index, minCapacity, Types.NUMERIC));
    }
    if (maxCapacity != null) {
      sql.append("\n  AND (").append(CAPACITY_NUMERIC).append(") <= ?");
      binders.add((ps, index) -> ps.setObject(index, maxCapacity, Types.NUMERIC));
    }
    if (cateringPolicy != null) {
      sql.append("\n  AND metadata->'catering'->>'policy' = ?");
      binders.add((ps, index) -> ps.setObject(index, cateringPolicy, Types.VARCHAR));
    }
    addArrayFilter(sql, binders, "metadata->'amenities'", amenities);
    if (Boolean.TRUE.equals(accessible)) {
      // Compared as text so no cast is attempted on malformed metadata.
      sql.append("\n  AND metadata->'accessibility'->>'ada_compliant' = 'true'");
    }
    return new Filter(sql.toString(), List.copyOf(binders));
  }

  /**
   * Adds an "array contains any of" predicate via {@code jsonb_exists_any}. The function form is
   * used instead of the {@code ?|} operator because the bare {@code ?} is consumed by the JDBC
   * driver as a bind placeholder.
   */
  private static void addArrayFilter(final StringBuilder sql,
                                     final List<Binder> binders,
                                     final String jsonPath,
                                     final List<String> values) {
    if (values == null || values.isEmpty()) {
      return;
    }
    sql.append("\n  AND jsonb_exists_any(").append(jsonPath)
        .append(", ARRAY[").append(joinPlaceholders(values.size())).append("]::text[])");
    for (final String value : values) {
      binders.add((ps, index) -> ps.setObject(index, value, Types.VARCHAR));
    }
  }

  private static String joinPlaceholders(final int count) {
    return String.join(", ", Collections.nCopies(count, "?"));
  }

  private static void bindAll(final PreparedStatement ps, final List<Binder> binders) throws SQLException {
    int index = 1;
    for (final Binder binder : binders) {
      binder.bind(ps, index++);
    }
  }

  private static java.time.Instant toInstant(final Timestamp ts) {
    return ts != null ? ts.toInstant() : null;
  }

  private String tenantVenuesTable() {
    final String tenantKey = TenantContext.getCurrentTenant();
    if (tenantKey == null || !SAFE_TENANT_KEY.matcher(tenantKey).matches()) {
      throw new IllegalStateException("No valid tenant context for venue semantic search");
    }
    return "t_" + tenantKey + ".venues";
  }

  /**
   * A parameterised SQL fragment and the binders that populate its placeholders, in order.
   */
  private record Filter(String sql, List<Binder> binders) {
  }

  /**
   * Sets one positional parameter; kept as a named type so a {@link SQLException} can propagate.
   */
  @FunctionalInterface
  private interface Binder {

    void bind(PreparedStatement ps, int index) throws SQLException;
  }
}
