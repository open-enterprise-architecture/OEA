package io.oea.core.solution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.core.catalog.CatalogQueryStore;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.LabeledValue;
import io.oea.core.entity.PropertyValueStore;
import io.oea.core.entity.PropertyValueStore.NamedValue;
import io.oea.core.entity.TypedValue;
import io.oea.core.metamodel.BuiltInMetatypes;
import io.oea.core.metamodel.MetatypeStore;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The stores of the solution feature that moved out of {@code app.solution} (change
 * s17-sql-into-core-repository) against a migrated database on the runtime role: solutions,
 * deltas and their values, the shared labeled value read, and the entity and metatype reads that
 * the service tests reach only through the whole application.
 */
class SolutionRepositoryStoresTests {

	private static OeaDatabase postgres;

	private static JdbcTemplate owner;

	private static JdbcTemplate app;

	private static long person;

	private static UUID metatype;

	private static UUID connectionMetatype;

	private static UUID referenceProperty;

	private static UUID stringProperty;

	private static int alpha;

	private static int beta;

	private static int connection;

	private static int aid;

	private static int deleted;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		owner = new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asSuperuser(postgres), true));
		app = new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asRole(postgres, "oea_app"), true));
		person = owner.queryForObject("INSERT INTO persons (name, email) VALUES ('Kurt', 'kurt@example.org') RETURNING id",
				Long.class);
		metatype = owner.queryForObject("""
				INSERT INTO metatypes (config_id, name, abbreviation, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', 'Store Application', 'SAP', false) RETURNING id""",
				UUID.class);
		connectionMetatype = owner.queryForObject("""
				INSERT INTO metatypes (config_id, name, abbreviation, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', 'Store Flow', 'SFL', true) RETURNING id""", UUID.class);
		referenceProperty = owner.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, ref_metatype_id)
				VALUES ('Predecessor', 'store_predecessor', 'reference', ?) RETURNING id""", UUID.class, metatype);
		stringProperty = owner.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type)
				VALUES ('alpha note', 'store_note', 'string') RETURNING id""", UUID.class);
		alpha = entity(metatype, "Alpha");
		beta = entity(metatype, "Beta");
		connection = entity(connectionMetatype, "Flow");
		aid = entity(BuiltInMetatypes.STRUCTURING_AID, "Folder");
		deleted = entity(metatype, "Gone");
		owner.update("UPDATE entities SET deleted_at = now(), deleted_by = ?, deletion_reason = 'test' WHERE id = ?",
				person, deleted);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	private static int entity(UUID metatypeId, String name) {
		return owner.queryForObject("INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id",
				Integer.class, metatypeId, name, person);
	}

	private static UUID solution(String name) {
		UUID id = UUID.randomUUID();
		assertThat(new SolutionStore(app).insert(id, name, "", person, null, null)).isTrue();
		return id;
	}

	@Test
	void solutionStoreInsertsListsFindsAndReadsTheStatus() {
		SolutionStore store = new SolutionStore(app);
		UUID second = UUID.randomUUID();
		assertThat(store.insert(second, "store-solution zed", "Described", person, null, null)).isTrue();
		UUID first = solution("Store-Solution alpha");
		// a taken name (regardless of case) inserts nothing
		assertThat(store.insert(UUID.randomUUID(), "STORE-SOLUTION ALPHA", "", person, null, null)).isFalse();

		List<String> names = store.list().stream().map(SolutionStore.SolutionRow::name).toList();
		assertThat(names).containsSubsequence("Store-Solution alpha", "store-solution zed");
		SolutionStore.SolutionRow row = store.find(second).orElseThrow();
		assertThat(row.description()).isEqualTo("Described");
		assertThat(row.status()).isEqualTo("draft");
		assertThat(row.ownerName()).isEqualTo("Kurt");
		assertThat(row.createdById()).isEqualTo(person);
		assertThat(store.find(UUID.randomUUID())).isEmpty();
		assertThat(store.status(first)).contains("draft");
		assertThat(store.status(UUID.randomUUID())).isEmpty();
	}

	@Test
	void deltaStoreWritesLocksAndPages() {
		DeltaStore store = new DeltaStore(app);
		UUID solution = solution("Store Deltas");
		UUID created = UUID.randomUUID();
		UUID modified = UUID.randomUUID();
		UUID retiring = UUID.randomUUID();
		store.insert(retiring, solution, "retiring", beta, null, null, null, person);
		store.insert(modified, solution, "modified", alpha, null, null, null, person);
		store.insert(created, solution, "new", null, metatype, "Zeta", "", person);
		// one delta per entity and solution
		assertThatExceptionOfType(DuplicateKeyException.class)
			.isThrownBy(() -> store.insert(UUID.randomUUID(), solution, "modified", alpha, null, null, null, person));

		assertThat(store.countsByType(solution)).containsOnly(Map.entry("new", 1L), Map.entry("modified", 1L),
				Map.entry("retiring", 1L));
		assertThat(store.countsByType(UUID.randomUUID())).isEmpty();

		// new, then modified, then retiring
		List<DeltaStore.DeltaRow> page = store.page(solution, null, null, 10, 0);
		assertThat(page).extracting(DeltaStore.DeltaRow::id).containsExactly(created, modified, retiring);
		assertThat(page.get(0).name()).isEqualTo("Zeta");
		assertThat(page.get(1).name()).isEqualTo("Alpha");
		assertThat(page.get(1).metatypeName()).isEqualTo("Store Application");
		assertThat(page.get(0).updatedAt()).isNull();
		// both sides of the page boundary: the last page holds the rest, the page after it nothing
		assertThat(store.page(solution, null, null, 2, 0)).hasSize(2);
		assertThat(store.page(solution, null, null, 2, 2)).extracting(DeltaStore.DeltaRow::id)
			.containsExactly(retiring);
		assertThat(store.page(solution, null, null, 2, 3)).isEmpty();
		// one delta of the solution, and none of another solution
		assertThat(store.page(solution, modified, null, 0, 0)).extracting(DeltaStore.DeltaRow::id)
			.containsExactly(modified);
		assertThat(store.page(UUID.randomUUID(), modified, null, 0, 0)).isEmpty();
		// the keyset after the new delta: rank 0, its sort name, its ID
		assertThat(store.page(solution, null, List.of(0, "zeta", created.toString()), 10, 0))
			.extracting(DeltaStore.DeltaRow::id)
			.containsExactly(modified, retiring);
		assertThat(store.page(solution, null, List.of(2, "beta", retiring.toString()), 10, 0)).isEmpty();

		assertThat(store.lock(modified, solution)).contains(new DeltaStore.Locked("modified", alpha));
		assertThat(store.lock(created, solution).orElseThrow().entityId()).isNull();
		assertThat(store.lock(modified, UUID.randomUUID())).isEmpty();

		store.touch(modified, person);
		assertThat(store.page(solution, modified, null, 0, 0).getFirst().updatedAt()).isNotNull();

		store.delete(modified, UUID.randomUUID());
		assertThat(store.countsByType(solution)).containsKey("modified");
		store.delete(modified, solution);
		assertThat(store.countsByType(solution)).doesNotContainKey("modified");
	}

	@Test
	void deltaStoreFindsTheConflictsWithOtherActiveSolutions() {
		DeltaStore store = new DeltaStore(app);
		int entity = entity(metatype, "Contested");
		UUID mine = solution("Store Conflicts mine");
		UUID other = solution("Store Conflicts other");
		UUID done = solution("Store Conflicts done");
		store.insert(UUID.randomUUID(), mine, "modified", entity, null, null, null, person);
		assertThat(store.conflicts(mine, List.of(entity))).isEmpty();
		assertThat(store.conflicted(mine)).isZero();

		store.insert(UUID.randomUUID(), other, "retiring", entity, null, null, null, person);
		store.insert(UUID.randomUUID(), done, "modified", entity, null, null, null, person);
		owner.update("UPDATE solutions SET status = 'implemented' WHERE id = ?", done);

		List<DeltaStore.ConflictRow> conflicts = store.conflicts(mine, List.of(entity, alpha));
		assertThat(conflicts).hasSize(1);
		assertThat(conflicts.getFirst().solutionId()).isEqualTo(other);
		assertThat(conflicts.getFirst().solutionName()).isEqualTo("Store Conflicts other");
		assertThat(conflicts.getFirst().entityId()).isEqualTo(entity);
		assertThat(conflicts.getFirst().deltaType()).isEqualTo("retiring");
		assertThat(conflicts.getFirst().ownerName()).isEqualTo("Kurt");
		assertThat(store.conflicts(mine, List.of())).isEmpty();
		assertThat(store.conflicted(mine)).isEqualTo(1);
		assertThat(store.conflicted(done)).isEqualTo(1);
	}

	@Test
	void deltaValueStoreInsertsReadsAndDeletesValues() {
		DeltaStore deltas = new DeltaStore(app);
		DeltaValueStore values = new DeltaValueStore(app);
		UUID solution = solution("Store Delta Values");
		UUID created = UUID.randomUUID();
		UUID modified = UUID.randomUUID();
		deltas.insert(created, solution, "new", null, metatype, "Valued", "", person);
		deltas.insert(modified, solution, "modified", alpha, null, null, null, person);

		values.insertValue(created, null, stringProperty, null, TypedValue.ofText("hello"));
		values.insertValue(created, null, referenceProperty, null, TypedValue.ofReference(beta));
		// a change: the name, the description, a property, a removed property value
		values.insertValue(modified, "description", null, TypedValue.ofText("old"), TypedValue.ofText("new"));
		values.insertValue(modified, "name", null, TypedValue.ofText("Alpha"), TypedValue.ofText("Alpha 2"));
		values.insertValue(modified, null, referenceProperty, TypedValue.ofReference(beta), null);

		Map<UUID, List<NamedValue>> news = values.newValues(List.of(created, modified));
		assertThat(news).containsOnlyKeys(created);
		// ordered by property name regardless of case
		assertThat(news.get(created)).extracting(NamedValue::propertyName).containsExactly("alpha note", "Predecessor");
		assertThat(news.get(created).get(0).value()).isEqualTo(new LabeledValue(TypedValue.ofText("hello"), null, null));
		assertThat(news.get(created).get(1).value()).isEqualTo(new LabeledValue(TypedValue.ofReference(beta), "Beta", "SAP"));
		assertThat(news.get(created).get(1).dataType()).isEqualTo("reference");
		assertThat(values.newValues(List.of())).isEmpty();

		Map<UUID, List<DeltaValueStore.ChangeRow>> changes = values.changes(List.of(created, modified));
		assertThat(changes).containsOnlyKeys(modified);
		// name, description, then properties
		assertThat(changes.get(modified)).extracting(DeltaValueStore.ChangeRow::systemField)
			.containsExactly("name", "description", null);
		DeltaValueStore.ChangeRow property = changes.get(modified).get(2);
		assertThat(property.entityId()).isEqualTo(alpha);
		assertThat(property.propertyName()).isEqualTo("Predecessor");
		assertThat(property.before()).isEqualTo(new LabeledValue(TypedValue.ofReference(beta), "Beta", "SAP"));
		// no after value: no value, not an empty one
		assertThat(property.after().value()).isNull();
		assertThat(changes.get(modified).get(0).propertyDefId()).isNull();
		assertThat(values.changes(List.of())).isEmpty();

		List<DeltaValueStore.RecordedValue> recorded = values.recorded(modified);
		assertThat(recorded).hasSize(3);
		assertThat(recorded).filteredOn((value) -> "name".equals(value.systemField()))
			.singleElement()
			.satisfies((value) -> {
				assertThat(value.before()).isEqualTo(TypedValue.ofText("Alpha"));
				assertThat(value.after()).isEqualTo(TypedValue.ofText("Alpha 2"));
				assertThat(value.propertyName()).isNull();
			});
		assertThat(recorded).filteredOn((value) -> value.propertyDefId() != null)
			.singleElement()
			.satisfies((value) -> {
				assertThat(value.propertyName()).isEqualTo("Predecessor");
				assertThat(value.after()).isNull();
			});

		values.deleteValues(modified);
		assertThat(values.recorded(modified)).isEmpty();
		assertThat(values.newValues(List.of(created))).containsKey(created);
	}

	@Test
	void propertyValueStoreReadsLabeledValuesOfSeveralEntities() {
		int first = entity(metatype, "Labeled one");
		int second = entity(metatype, "Labeled two");
		int without = entity(metatype, "Labeled none");
		owner.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, 'note')",
				first, stringProperty);
		owner.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_ref) VALUES (?, ?, ?)", first,
				referenceProperty, beta);
		owner.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_ref) VALUES (?, ?, ?)",
				second, referenceProperty, alpha);

		PropertyValueStore store = new PropertyValueStore(app);
		Map<Integer, List<NamedValue>> values = store.labeledValues(List.of(first, second, without));
		assertThat(values).containsOnlyKeys(first, second);
		// "alpha note" before "Predecessor": by name regardless of case
		assertThat(values.get(first)).extracting(NamedValue::propertyName).containsExactly("alpha note", "Predecessor");
		assertThat(values.get(first).get(1).value()).isEqualTo(new LabeledValue(TypedValue.ofReference(beta), "Beta", "SAP"));
		assertThat(values.get(second).getFirst().value())
			.isEqualTo(new LabeledValue(TypedValue.ofReference(alpha), "Alpha", "SAP"));
		assertThat(store.labeledValues(List.of(first)).getOrDefault(second, List.of())).isEmpty();
		assertThat(store.labeledValues(List.of(without))).isEmpty();
		assertThat(store.labeledValues(List.of())).isEmpty();
	}

	@Test
	void entityStoreChecksTheBaselineAndReadsNames() {
		EntityStore store = new EntityStore(app);
		assertThat(store.isBaselineElement(alpha)).isTrue();
		// a connection, a structuring aid, a deleted element and an unknown ID are no baseline
		assertThat(store.isBaselineElement(connection)).isFalse();
		assertThat(store.isBaselineElement(aid)).isFalse();
		assertThat(store.isBaselineElement(deleted)).isFalse();
		assertThat(store.isBaselineElement(Integer.MAX_VALUE)).isFalse();

		Map<Integer, EntityStore.NameRow> names = store.activeElementNames(List.of(alpha, connection, deleted, Integer.MAX_VALUE));
		assertThat(names).containsOnlyKeys(alpha);
		assertThat(names.get(alpha)).isEqualTo(new EntityStore.NameRow("Alpha", ""));
		assertThat(store.activeElementNames(List.of())).isEmpty();
	}

	@Test
	void metatypeStoreReadsNameAndAbbreviation() {
		MetatypeStore store = new MetatypeStore(app);
		assertThat(store.nameAndAbbreviation(metatype)).isEqualTo(new MetatypeStore.NameRow("Store Application", "SAP"));
		assertThatExceptionOfType(EmptyResultDataAccessException.class)
			.isThrownBy(() -> store.nameAndAbbreviation(UUID.randomUUID()));
	}

	@Test
	void cancelledRecognizesTheQueryCancellationAlsoAsCause() {
		assertThat(CatalogQueryStore.cancelled(new SQLException("canceled", "57014"))).isTrue();
		assertThat(CatalogQueryStore.cancelled(new IllegalStateException(new RuntimeException(new SQLException("x", "57014")))))
			.isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "57013", "23505", "" })
	void cancelledIgnoresOtherStatesAndOtherCauses(String state) {
		assertThat(CatalogQueryStore.cancelled(new SQLException("other", state))).isFalse();
		assertThat(CatalogQueryStore.cancelled(new IllegalStateException("no cause"))).isFalse();
	}

}
