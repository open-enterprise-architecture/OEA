package io.oea.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.core.catalog.CatalogQueryStore.Filter;
import io.oea.core.catalog.CatalogQueryStore.QueryColumn;
import io.oea.core.catalog.CatalogQueryStore.SystemField;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * The statements of the catalog query with and without {@code includeDeleted} (design D2 of
 * s17-deleted-entities): without it the SQL stays exactly as before, so that its plan does not change;
 * with it the {@code deleted_at} condition of the primary entity is left out and the deletion columns
 * are selected. The statements are captured, not run.
 */
class CatalogQueryStoreSqlTests {

	private static final String ACTIVE_ONLY = " WHERE e.metatype_id = ? AND e.deleted_at IS NULL";

	private static final String ACTIVE_ENDPOINT = " LEFT JOIN entities es ON es.id = ec.source_entity_id AND es.deleted_at IS NULL"
			+ " LEFT JOIN entities et ON et.id = ec.target_entity_id AND et.deleted_at IS NULL";

	private final List<String> statements = new ArrayList<>();

	private final CatalogQueryStore store = new CatalogQueryStore(new JdbcTemplate() {

		@Override
		public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
			CatalogQueryStoreSqlTests.this.statements.add(sql);
			return List.of();
		}

		@Override
		public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
			CatalogQueryStoreSqlTests.this.statements.add(sql);
			return requiredType.cast(0L);
		}

	});

	private static final List<QueryColumn> COLUMNS = List.of(QueryColumn.of(SystemField.NAME));

	private static final List<QueryColumn> ENDPOINT_COLUMNS = List
		.of(QueryColumn.of(SystemField.NAME).at(CatalogQueryStore.Endpoint.SOURCE));

	@Test
	void theDefaultStatementsReadActiveEntitiesOnly() {
		UUID type = UUID.randomUUID();

		this.store.page(type, COLUMNS, List.of(), Filter.NONE, CatalogQueryStore.Sort.DEFAULT, 0, 10);
		this.store.count(type, List.of(), Filter.NONE);

		assertThat(this.statements).hasSize(2).allSatisfy((sql) -> {
			assertThat(sql).contains(ACTIVE_ONLY);
			assertThat(sql).doesNotContain("e.deleted_at, e.version");
		});
	}

	@Test
	void theStatementsWithTheFlagLeaveOutTheDeletionCondition() {
		UUID type = UUID.randomUUID();
		Filter filter = new Filter(List.of(), List.of(), false, true);

		this.store.page(type, COLUMNS, List.of(), filter, CatalogQueryStore.Sort.DEFAULT, 0, 10);
		this.store.count(type, List.of(), filter);

		assertThat(this.statements).hasSize(2).allSatisfy((sql) -> {
			assertThat(sql).contains(" WHERE e.metatype_id = ?");
			assertThat(sql).doesNotContain("e.deleted_at IS NULL");
		});
		assertThat(this.statements.get(0)).contains(", e.deleted_at, e.version FROM entities e");
		assertThat(this.statements.get(1)).doesNotContain("e.version");
	}

	@Test
	void theEndpointsOfADeletedConnectionAreJoinedAlsoWhenDeletedOnlyWithTheFlag() {
		UUID type = UUID.randomUUID();

		this.store.page(type, ENDPOINT_COLUMNS, List.of(), Filter.NONE, CatalogQueryStore.Sort.DEFAULT, 0, 10);
		this.store.page(type, ENDPOINT_COLUMNS, List.of(), new Filter(List.of(), List.of(), false, true),
				CatalogQueryStore.Sort.DEFAULT, 0, 10);

		assertThat(this.statements.get(0)).contains(ACTIVE_ENDPOINT);
		assertThat(this.statements.get(1)).doesNotContain(ACTIVE_ENDPOINT)
			.contains("es.deleted_at IS NULL OR e.deleted_at IS NOT NULL")
			.contains("et.deleted_at IS NULL OR e.deleted_at IS NOT NULL");
	}

	@Test
	void theThreeArgumentFilterDoesNotIncludeDeleted() {
		assertThat(new Filter(List.of(), List.of(), true).includeDeleted()).isFalse();
		assertThat(Filter.NONE.includeDeleted()).isFalse();
	}

}
