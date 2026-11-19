package io.oea.core.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.core.entity.EntityAggregationStore.GroupRow;
import io.oea.core.entity.EntityAggregationStore.Grouping;
import io.oea.core.entity.EntityAggregationStore.ValueColumn;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

}
