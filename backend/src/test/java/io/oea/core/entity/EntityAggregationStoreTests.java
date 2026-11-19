package io.oea.core.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.core.entity.EntityAggregationStore.GroupRow;
import io.oea.core.entity.EntityAggregationStore.Grouping;
import io.oea.core.entity.EntityAggregationStore.ValueColumn;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The aggregation query of design D3 of us-056-kpi-and-chart-widgets, on the runtime role: scope,
 * partial aggregates per group and the keys of each value column.
 */
class EntityAggregationStoreTests {

	private static OeaDatabase postgres;

	private static JdbcTemplate owner;

	private static EntityAggregationStore store;

	private static long person;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		owner = new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asSuperuser(postgres), true));
		store = new EntityAggregationStore(
				new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asRole(postgres, "oea_app"), true)));
		person = owner.queryForObject("INSERT INTO persons (name, email) VALUES ('Kurt', ?) RETURNING id", Long.class,
				"kurt-" + UUID.randomUUID() + "@example.org");
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	private static UUID metatype() {
		return owner.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?)
				RETURNING id""", UUID.class, "Type " + UUID.randomUUID());
	}

	private static UUID property(String dataType) {
		String key = "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		UUID reference = "reference".equals(dataType) ? metatype() : null;
		return owner.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, ref_metatype_id)
				VALUES (?, ?, ?, ?) RETURNING id""", UUID.class, "Property " + key, key, dataType, reference);
	}

	private static int entity(UUID metatype, String name) {
		return owner.queryForObject(
				"INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id", Integer.class,
				metatype, name, person);
	}

	private static void value(int entity, UUID property, String column, Object value) {
		owner.update("INSERT INTO entity_property_values (entity_id, property_def_id, " + column + ") VALUES (?, ?, ?)",
				entity, property, value);
	}

	private static Map<String, GroupRow> byKey(List<GroupRow> rows) {
		return rows.stream().collect(Collectors.toMap((row) -> String.valueOf(row.key()), Function.identity()));
	}

	@Test
	void countsTheEntitiesOfTheGivenTypesThatAreNotDeleted() {
		UUID type = metatype();
		UUID other = metatype();
		entity(type, "A");
		entity(type, "B");
		entity(other, "Elsewhere");
		int gone = entity(type, "Gone");
		owner.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", person, gone);

		List<GroupRow> rows = store.aggregate(Set.of(type), null, Grouping.none());
		assertThat(rows).containsExactly(new GroupRow(null, null, 2, null, null, null));
		assertThat(store.aggregate(Set.of(type, other), null, Grouping.none()).get(0).count()).isEqualTo(3);
	}

	@Test
	void noTypesAndNoEntitiesGiveAZeroCountWithoutGroups() {
		assertThat(store.aggregate(Set.of(), null, Grouping.none()))
			.containsExactly(new GroupRow(null, null, 0, null, null, null));
		assertThat(store.aggregate(Set.of(), null, Grouping.byMetatype())).isEmpty();
		UUID type = metatype();
		assertThat(store.aggregate(Set.of(type), null, Grouping.none()))
			.containsExactly(new GroupRow(null, null, 0, null, null, null));
		UUID cost = property("integer");
		assertThat(store.aggregate(Set.of(type), cost, Grouping.none()))
			.containsExactly(new GroupRow(null, null, 0, null, null, null));
		assertThat(store.aggregate(Set.of(type), null, Grouping.byProperty(property("string"), ValueColumn.TEXT)))
			.isEmpty();
	}

	@Test
	void theAggregatedPropertyLeavesOutEntitiesWithoutAValueAndDeletedOnes() {
		UUID type = metatype();
		UUID cost = property("integer");
		for (long value : new long[] { 5, 12, -3 }) {
			value(entity(type, "A" + value), cost, "value_int", value);
		}
		entity(type, "No value");
		int gone = entity(type, "Gone");
		value(gone, cost, "value_int", 1000);
		owner.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", person, gone);
		// the value of an entity of another type is not counted
		value(entity(metatype(), "Other"), cost, "value_int", 7);

		GroupRow row = store.aggregate(Set.of(type), cost, Grouping.none()).get(0);
		assertThat(row.count()).isEqualTo(3);
		assertThat(row.sum()).isEqualTo(14);
		assertThat(row.min()).isEqualTo(-3);
		assertThat(row.max()).isEqualTo(12);
	}

	@Test
	void countOfANonIntegerPropertyCountsTheEntitiesWithAValueAndHasNoSum() {
		UUID type = metatype();
		UUID vendor = property("string");
		value(entity(type, "A"), vendor, "value_text", "SAP");
		value(entity(type, "B"), vendor, "value_text", "Oracle");
		entity(type, "C");
		GroupRow row = store.aggregate(Set.of(type), vendor, Grouping.none()).get(0);
		assertThat(row.count()).isEqualTo(2);
		assertThat(row.sum()).isNull();
		assertThat(row.min()).isNull();
		assertThat(row.max()).isNull();
	}

	@Test
	void groupsByMetatype() {
		UUID first = metatype();
		UUID second = metatype();
		UUID cost = property("integer");
		value(entity(first, "A"), cost, "value_int", 1);
		value(entity(first, "B"), cost, "value_int", 2);
		value(entity(second, "C"), cost, "value_int", 10);
		entity(second, "Without value");
		Map<String, GroupRow> rows = byKey(store.aggregate(Set.of(first, second), cost, Grouping.byMetatype()));
		assertThat(rows).hasSize(2);
		assertThat(rows.get(first.toString())).isEqualTo(new GroupRow(first.toString(), null, 2, 3L, 1L, 2L));
		assertThat(rows.get(second.toString())).isEqualTo(new GroupRow(second.toString(), null, 1, 10L, 10L, 10L));
		Map<String, GroupRow> counts = byKey(store.aggregate(Set.of(first, second), null, Grouping.byMetatype()));
		assertThat(counts.get(second.toString()).count()).isEqualTo(2);
		assertThat(counts.get(second.toString()).sum()).isNull();
	}

	@Test
	void groupsByTextAndHasTheKeyNullForEntitiesWithoutAValue() {
		UUID type = metatype();
		UUID vendor = property("string");
		UUID cost = property("integer");
		int[] entities = { entity(type, "A"), entity(type, "B"), entity(type, "C"), entity(type, "D") };
		String[] vendors = { "SAP", "SAP", "Oracle", null };
		long[] costs = { 1, 2, 4, 8 };
		for (int i = 0; i < entities.length; i++) {
			value(entities[i], cost, "value_int", costs[i]);
			if (vendors[i] != null) {
				value(entities[i], vendor, "value_text", vendors[i]);
			}
		}
		Map<String, GroupRow> rows = byKey(
				store.aggregate(Set.of(type), cost, Grouping.byProperty(vendor, ValueColumn.TEXT)));
		assertThat(rows).containsOnlyKeys("SAP", "Oracle", "null");
		assertThat(rows.get("SAP")).isEqualTo(new GroupRow("SAP", null, 2, 3L, 1L, 2L));
		assertThat(rows.get("Oracle").sum()).isEqualTo(4);
		assertThat(rows.get("null")).isEqualTo(new GroupRow(null, null, 1, 8L, 8L, 8L));
		assertThat(rows.get("null").key()).isNull();
	}

	@Test
	void groupsByBooleanAndByEnum() {
		UUID type = metatype();
		UUID critical = property("boolean");
		UUID plateau = property("enum");
		boolean[] flags = { true, true, false };
		String[] plateaus = { "P1", "P2", "P1" };
		for (int i = 0; i < flags.length; i++) {
			int entity = entity(type, "E" + i);
			value(entity, critical, "value_bool", flags[i]);
			value(entity, plateau, "value_enum", plateaus[i]);
		}
		Map<String, GroupRow> bools = byKey(store.aggregate(Set.of(type), null,
				Grouping.byProperty(critical, ValueColumn.BOOL)));
		assertThat(bools.get("true").count()).isEqualTo(2);
		assertThat(bools.get("false").count()).isEqualTo(1);
		Map<String, GroupRow> enums = byKey(store.aggregate(Set.of(type), null,
				Grouping.byProperty(plateau, ValueColumn.ENUM)));
		assertThat(enums.get("P1").count()).isEqualTo(2);
		assertThat(enums.get("P2").count()).isEqualTo(1);
	}

	@Test
	void groupsByReferenceWithTheNameOfTheReferencedEntity() {
		UUID type = metatype();
		UUID hosting = property("reference");
		UUID targets = owner.queryForObject("SELECT ref_metatype_id FROM property_definitions WHERE id = ?", UUID.class,
				hosting);
		int datacenter = entity(targets, "Data Center");
		int cloud = entity(targets, "Cloud");
		value(entity(type, "A"), hosting, "value_ref", datacenter);
		value(entity(type, "B"), hosting, "value_ref", datacenter);
		value(entity(type, "C"), hosting, "value_ref", cloud);
		entity(type, "D");
		Map<String, GroupRow> rows = byKey(store.aggregate(Set.of(type), null,
				Grouping.byProperty(hosting, ValueColumn.REF)));
		assertThat(rows).containsOnlyKeys(Integer.toString(datacenter), Integer.toString(cloud), "null");
		assertThat(rows.get(Integer.toString(datacenter)).name()).isEqualTo("Data Center");
		assertThat(rows.get(Integer.toString(datacenter)).count()).isEqualTo(2);
		assertThat(rows.get(Integer.toString(cloud)).name()).isEqualTo("Cloud");
		assertThat(rows.get("null").name()).isNull();
	}

	// ---- the state at a point in time (design D2 of us-056-kpi-previous-value)

	private static final Instant AT = Instant.parse("2026-10-20T12:00:00Z");

	private static int entityAt(UUID metatype, String name, Instant createdAt) {
		return owner.queryForObject(
				"INSERT INTO entities (metatype_id, name, created_by, created_at) VALUES (?, ?, ?, ?) RETURNING id",
				Integer.class, metatype, name, person, Timestamp.from(createdAt));
	}

	private static void deleteAt(int entity, Instant deletedAt) {
		owner.update("UPDATE entities SET deleted_at = ?, deleted_by = ? WHERE id = ?", Timestamp.from(deletedAt), person,
				entity);
	}

	/** A history row: the value before the change of the given version; a null value is "had none". */
	private static void history(int entity, UUID property, int version, Instant changedAt, Long old) {
		owner.update("""
				INSERT INTO entity_property_value_history (entity_id, property_def_id, entity_version, value_int,
				    changed_at, changed_by) VALUES (?, ?, ?, ?, ?, ?)""", entity, property, version, old,
				Timestamp.from(changedAt), person);
	}

	private static GroupRow at(UUID type, UUID property) {
		return store.aggregateAt(Set.of(type), property, AT);
	}

	/** created offset (seconds from AT), deleted offset (null: not deleted), expected count */
	@ParameterizedTest
	@CsvSource(value = { "0, , 1", "-1, , 1", "1, , 0", "-100, 0, 0", "-100, -1, 0", "-100, 1, 1", "-100, 100, 1",
			"-100, -100, 0" })
	void anEntityExistsAtTheTimeIfCreatedAtOrBeforeAndNotDeletedAtOrBefore(long created, Long deleted, long expected) {
		UUID type = metatype();
		int entity = entityAt(type, "E", AT.plusSeconds(created));
		if (deleted != null) {
			deleteAt(entity, AT.plusSeconds(deleted));
		}
		assertThat(at(type, null)).isEqualTo(new GroupRow(null, null, expected, null, null, null));
		// an entity of the same type that is current still counts for the current state
		assertThat(store.aggregate(Set.of(type), null, Grouping.none()).get(0).count()).isEqualTo((deleted == null) ? 1 : 0);
	}

	@Test
	void theValueAtTheTimeIsTheCurrentValueIfItHasNotChangedSince() {
		UUID type = metatype();
		UUID cost = property("integer");
		value(entityAt(type, "A", AT.minusSeconds(60)), cost, "value_int", 5);
		value(entityAt(type, "B", AT.minusSeconds(60)), cost, "value_int", 7);
		// history rows of changes before or at the time do not matter
		int changedBefore = entityAt(type, "C", AT.minusSeconds(600));
		value(changedBefore, cost, "value_int", 100);
		history(changedBefore, cost, 2, AT.minusSeconds(10), 99L);
		entityAt(type, "No value", AT.minusSeconds(60));
		assertThat(at(type, cost)).isEqualTo(new GroupRow(null, null, 3, 112L, 5L, 100L));
	}

	@Test
	void theValueAtTheTimeIsTheOldValueOfTheFirstLaterChange() {
		UUID type = metatype();
		UUID cost = property("integer");
		// changed once after the time: 5 -> 7 now
		int once = entityAt(type, "Once", AT.minusSeconds(600));
		value(once, cost, "value_int", 7);
		history(once, cost, 2, AT.plusSeconds(10), 5L);
		// changed twice after the time: 3 -> 4 -> 9; the first later row holds the value at the time
		int twice = entityAt(type, "Twice", AT.minusSeconds(600));
		value(twice, cost, "value_int", 9);
		history(twice, cost, 2, AT.plusSeconds(10), 3L);
		history(twice, cost, 3, AT.plusSeconds(20), 4L);
		// changed before and after the time: 1 -> 2 (before) -> 6 (after)
		int mixed = entityAt(type, "Mixed", AT.minusSeconds(600));
		value(mixed, cost, "value_int", 6);
		history(mixed, cost, 2, AT.minusSeconds(20), 1L);
		history(mixed, cost, 3, AT.plusSeconds(20), 2L);
		assertThat(at(type, cost)).isEqualTo(new GroupRow(null, null, 3, 10L, 2L, 5L));
	}

	/** change offset (seconds from AT) of 12 -> 20, the value at AT, and whether it counts */
	@ParameterizedTest
	@CsvSource({ "0, 20, 1", "1, 12, 1", "-1, 20, 1" })
	void aChangeExactlyAtTheTimeHasTakenEffect(long change, long expected, long count) {
		UUID type = metatype();
		UUID cost = property("integer");
		int entity = entityAt(type, "E", AT.minusSeconds(600));
		value(entity, cost, "value_int", 20);
		history(entity, cost, 2, AT.plusSeconds(change), 12L);
		assertThat(at(type, cost)).isEqualTo(new GroupRow(null, null, count, expected, expected, expected));
	}

	@Test
	void aValueRemovedAfterTheTimeCountsAndOneSetForTheFirstTimeAfterItDoesNot() {
		UUID type = metatype();
		UUID cost = property("integer");
		int removed = entityAt(type, "Removed", AT.minusSeconds(600));
		history(removed, cost, 2, AT.plusSeconds(10), 8L);
		int added = entityAt(type, "Added", AT.minusSeconds(600));
		value(added, cost, "value_int", 50);
		history(added, cost, 2, AT.plusSeconds(10), null);
		assertThat(at(type, cost)).isEqualTo(new GroupRow(null, null, 1, 8L, 8L, 8L));
		// without a property both entities count
		assertThat(at(type, null).count()).isEqualTo(2);
	}

	@Test
	void anEntityCreatedAfterTheTimeNeverCountsAndADeletedOneCountsWithItsValue() {
		UUID type = metatype();
		UUID cost = property("integer");
		value(entityAt(type, "Later", AT.plusSeconds(1)), cost, "value_int", 1000);
		int deletedLater = entityAt(type, "Deleted later", AT.minusSeconds(60));
		value(deletedLater, cost, "value_int", 4);
		deleteAt(deletedLater, AT.plusSeconds(1));
		int deletedBefore = entityAt(type, "Deleted before", AT.minusSeconds(60));
		value(deletedBefore, cost, "value_int", 2000);
		deleteAt(deletedBefore, AT);
		assertThat(at(type, cost)).isEqualTo(new GroupRow(null, null, 1, 4L, 4L, 4L));
	}

	@Test
	void subtypesAreIncludedAndOtherTypesAreNot() {
		UUID type = metatype();
		UUID subtype = metatype();
		UUID other = metatype();
		UUID cost = property("integer");
		value(entityAt(type, "A", AT.minusSeconds(60)), cost, "value_int", 1);
		value(entityAt(subtype, "B", AT.minusSeconds(60)), cost, "value_int", 2);
		value(entityAt(other, "C", AT.minusSeconds(60)), cost, "value_int", 4);
		assertThat(store.aggregateAt(Set.of(type, subtype), cost, AT)).isEqualTo(new GroupRow(null, null, 2, 3L, 1L, 2L));
		assertThat(store.aggregateAt(Set.of(type), cost, AT).count()).isEqualTo(1);
	}

	@Test
	void noEntitiesGiveACountOfZeroAndNoSum() {
		UUID type = metatype();
		UUID cost = property("integer");
		entityAt(type, "Later", AT.plusSeconds(1));
		assertThat(at(type, null)).isEqualTo(new GroupRow(null, null, 0, null, null, null));
		assertThat(at(type, cost)).isEqualTo(new GroupRow(null, null, 0, null, null, null));
		assertThat(store.aggregateAt(Set.of(), cost, AT)).isEqualTo(new GroupRow(null, null, 0, null, null, null));
		assertThat(store.aggregateAt(Set.of(), null, AT)).isEqualTo(new GroupRow(null, null, 0, null, null, null));
	}

	@Test
	void countOfANonIntegerPropertyCountsTheEntitiesThatHadAValueThen() {
		UUID type = metatype();
		UUID vendor = property("string");
		int changed = entityAt(type, "Changed", AT.minusSeconds(600));
		value(changed, vendor, "value_text", "Oracle");
		owner.update("""
				INSERT INTO entity_property_value_history (entity_id, property_def_id, entity_version, value_text,
				    changed_at, changed_by) VALUES (?, ?, 2, 'SAP', ?, ?)""", changed, vendor, Timestamp.from(AT.plusSeconds(5)),
				person);
		entityAt(type, "None", AT.minusSeconds(600));
		assertThat(at(type, vendor)).isEqualTo(new GroupRow(null, null, 1, null, null, null));
	}

}
