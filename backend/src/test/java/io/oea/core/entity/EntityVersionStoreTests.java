package io.oea.core.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.core.entity.EntityVersionStore.State;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The reconstruction of design D1 of us-099-version-diff (spec repository/entity-versions,
 * requirements "Complete state of a version" and "Properties of a state"), on the runtime role.
 * Version {@code n} of a test entity is at 2026-11-02T08:00Z + {@code n} days.
 */
class EntityVersionStoreTests {

	private static final Instant START = Instant.parse("2026-11-02T08:00:00Z");

	private static OeaDatabase postgres;

	private static JdbcTemplate owner;

	private static EntityVersionStore store;

	private static long person;

	private static UUID element;

	private static UUID other;

	private static UUID lifecycleStatus;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		owner = new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asSuperuser(postgres), true));
		store = new EntityVersionStore(
				new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asRole(postgres, "oea_app"), true)));
		person = owner.queryForObject("INSERT INTO persons (name, email) VALUES ('Kurt', ?) RETURNING id", Long.class,
				"kurt-" + UUID.randomUUID() + "@example.org");
		element = metatype("Element");
		other = metatype("Other");
		lifecycleStatus = property("lifecycleStatus", "string");
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void valueBeforeAndAfterAChange() {
		// lifecycleStatus is 'planned' up to version 3 and 'active' from version 4
		int entity = entity(5, element);
		value(entity, lifecycleStatus, "active");
		history(entity, lifecycleStatus, 4, "planned");

		assertThat(text(entity, 3, lifecycleStatus)).isEqualTo("planned");
		assertThat(text(entity, 4, lifecycleStatus)).isEqualTo("active");
		assertThat(text(entity, 5, lifecycleStatus)).isEqualTo("active");
	}

	@Test
	void removedValue() {
		// the value 'retired' was removed in version 3: version 2 has it, version 3 has none
		int entity = entity(3, element);
		history(entity, lifecycleStatus, 3, "retired");

		assertThat(text(entity, 2, lifecycleStatus)).isEqualTo("retired");
		assertThat(state(entity, 3).values()).doesNotContainKey(lifecycleStatus);
	}

	@Test
	void addedValueHasNoneBefore() {
		// a value set in version 2 had no value before: the history row holds no value
		int entity = entity(2, element);
		value(entity, lifecycleStatus, "planned");
		owner.update("""
				INSERT INTO entity_property_value_history (entity_id, property_def_id, entity_version, changed_by)
				VALUES (?, ?, 2, ?)""", entity, lifecycleStatus, person);

		assertThat(state(entity, 1).values()).doesNotContainKey(lifecycleStatus);
		assertThat(text(entity, 2, lifecycleStatus)).isEqualTo("planned");
	}

	@Test
	void systemFields() {
		// the name changed from 'Old' to the current name in version 2
		int entity = entity(3, element);
		String current = owner.queryForObject("SELECT name FROM entities WHERE id = ?", String.class, entity);
		owner.update("""
				INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value, changed_by)
				VALUES (?, 2, 'name', 'Old', ?, ?)""", entity, current, person);

		assertThat(state(entity, 1).systemFields()).containsEntry(EntityVersionStore.NAME, "Old")
			.containsEntry(EntityVersionStore.LOGICAL, "true")
			.containsEntry(EntityVersionStore.METATYPE, element.toString())
			.doesNotContainKey(EntityVersionStore.SOURCE);
		assertThat(state(entity, 2).systemFields()).containsEntry(EntityVersionStore.NAME, current);
		assertThat(state(entity, 3).systemFields()).containsEntry(EntityVersionStore.NAME, current);
		assertThat(state(entity, 3).header().changedByName()).isEqualTo("Kurt");
	}

	@ParameterizedTest
	@CsvSource({ "0, true", "1, false" })
	void mappingsByEffectiveDate(int daysAfterVersion, boolean mapped) {
		UUID owner = property("owner-" + UUID.randomUUID(), "string");
		UUID metatype = metatype("Mapped " + UUID.randomUUID());
		int entity = entity(4, metatype);
		map(metatype, owner, LocalDate.of(2026, 11, 6).plusDays(daysAfterVersion));

		// version 4 is on 2026-11-06
		assertThat(state(entity, 4).mapped().containsKey(owner)).isEqualTo(mapped);
		assertThat(state(entity, 4).values()).doesNotContainKey(owner);
	}

	@Test
	void mappingsOfTheMetatypeAtTheVersion() {
		// the metatype changed from Element to Other in version 3
		UUID ofElement = property("ofElement-" + UUID.randomUUID(), "string");
		UUID ofOther = property("ofOther-" + UUID.randomUUID(), "string");
		map(element, ofElement, LocalDate.of(2026, 1, 1));
		map(other, ofOther, LocalDate.of(2026, 1, 1));
		int entity = entity(3, other);
		owner.update("""
				INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value, changed_by)
				VALUES (?, 3, 'metatype_id', ?, ?, ?)""", entity, element.toString(), other.toString(), person);

		assertThat(state(entity, 2).metatypeId()).isEqualTo(element);
		assertThat(state(entity, 2).mapped()).containsKey(ofElement).doesNotContainKey(ofOther);
		assertThat(state(entity, 3).mapped()).containsKey(ofOther).doesNotContainKey(ofElement);
	}

	@ParameterizedTest
	@CsvSource({ "1, true", "5, true", "6, false", "0, false" })
	void versionBoundaries(int version, boolean exists) {
		int entity = entity(5, element);
		assertThat(store.state(entity, version).isPresent()).isEqualTo(exists);
	}

	@Test
	void namesAndDefinitions() {
		int entity = entity(1, element);
		assertThat(store.definitions(java.util.List.of(lifecycleStatus))).containsEntry(lifecycleStatus,
				new EntityVersionStore.Definition("lifecycleStatus", "string"));
		assertThat(store.metatypeNames(java.util.List.of(element))).containsEntry(element, "Element");
		assertThat(store.entityNames(java.util.List.of(entity))).containsKey(entity);
	}

	private static State state(int entity, int version) {
		return store.state(entity, version).orElseThrow();
	}

	private static String text(int entity, int version, UUID property) {
		return state(entity, version).values().get(property).text();
	}

	private static int entity(int versions, UUID metatype) {
		Integer id = owner.queryForObject(
				"INSERT INTO entities (metatype_id, name, version, created_by) VALUES (?, ?, ?, ?) RETURNING id",
				Integer.class, metatype, "Entity " + UUID.randomUUID(), versions, person);
		IntStream.rangeClosed(1, versions)
			.forEach((version) -> owner.update(
					"INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at) VALUES (?, ?, ?, ?)",
					id, version, person, Timestamp.from(START.plus(Duration.ofDays(version)))));
		return id;
	}

	private static void value(int entity, UUID property, String text) {
		owner.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, ?)",
				entity, property, text);
	}

	private static void history(int entity, UUID property, int version, String before) {
		owner.update("""
				INSERT INTO entity_property_value_history (entity_id, property_def_id, entity_version, value_text, changed_by)
				VALUES (?, ?, ?, ?, ?)""", entity, property, version, before, person);
	}

	private static UUID metatype(String name) {
		return owner.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?)
				RETURNING id""", UUID.class, name);
	}

	private static UUID property(String name, String dataType) {
		return owner.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type)
				VALUES (?, ?, ?) RETURNING id""", UUID.class, name, name.replace('-', '_').toLowerCase(), dataType);
	}

	private static void map(UUID metatype, UUID property, LocalDate validFrom) {
		owner.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', ?, 'organization')""", metatype, property, Date.valueOf(validFrom));
	}

}
