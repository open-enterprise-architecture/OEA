package io.oea.core.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.core.entity.EntityHistoryStore.Actor;
import io.oea.core.entity.EntityHistoryStore.ChangedField;
import io.oea.core.entity.EntityHistoryStore.Filter;
import io.oea.core.entity.EntityHistoryStore.Version;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * The history read of design D1 of us-098-entity-history (spec repository/entity-history,
 * requirements "Version history", "Filters", "Actors" and "Paging" of the history), on the
 * runtime role.
 */
class EntityHistoryStoreTests {

	private static final Instant START = Instant.parse("2026-11-02T08:00:00Z");

	private static OeaDatabase postgres;

	private static JdbcTemplate owner;

	private static EntityHistoryStore store;

	private static long michael;

	private static long kurt;

	private static UUID metatype;

	private static UUID lifecycleStatus;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		owner = new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asSuperuser(postgres), true));
		store = new EntityHistoryStore(
				new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asRole(postgres, "oea_app"), true)));
		michael = person("Michael");
		kurt = person("Kurt");
		metatype = owner.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Element')
				RETURNING id""", UUID.class);
		lifecycleStatus = owner.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type)
				VALUES ('lifecycleStatus', 'lifecycle_status', 'string') RETURNING id""", UUID.class);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void versionsNewestFirstWithTheirChangedFields() {
		int entity = entity(5, michael);
		owner.update("""
				INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value, changed_by)
				VALUES (?, 3, 'name', 'Old', 'New', ?)""", entity, michael);
		owner.update("""
				INSERT INTO entity_property_value_history (entity_id, property_def_id, entity_version, value_text, changed_by)
				VALUES (?, ?, 3, 'active', ?)""", entity, lifecycleStatus, michael);

		List<Version> versions = store.versions(entity, none(), null, 0, 50);
		assertThat(versions).extracting(Version::version).containsExactly(5, 4, 3, 2, 1);
		assertThat(versions.get(0).changedBy()).isEqualTo(new Actor(michael, "Michael"));

		Map<Integer, List<ChangedField>> changes = store.changes(entity, List.of(5, 4, 3, 2, 1));
		assertThat(changes.get(3)).containsExactly(new ChangedField("name", null),
				new ChangedField(null, lifecycleStatus));
		assertThat(changes).doesNotContainKey(1);
		assertThat(store.count(entity, none())).isEqualTo(5);
	}

	@Test
	void creationIsVersionOne() {
		int entity = entity(3, kurt);
		Version created = store.creation(entity).orElseThrow();
		assertThat(created.changedAt()).isEqualTo(START.plus(Duration.ofHours(1)));
		assertThat(created.changedBy().name()).isEqualTo("Kurt");
		assertThat(store.entity(entity)).isPresent();
		assertThat(store.entity(Integer.MAX_VALUE)).isEmpty();
	}

	@Test
	void filterByActorAndActorsIndependentOfTheFilter() {
		int entity = entity(5, michael);
		owner.update("UPDATE entity_version_headers SET changed_by = ? WHERE entity_id = ? AND version IN (2, 4)", kurt,
				entity);

		assertThat(store.versions(entity, new Filter(null, null, michael), null, 0, 50)).extracting(Version::version)
			.containsExactly(5, 3, 1);
		assertThat(store.count(entity, new Filter(null, null, michael))).isEqualTo(3);
		assertThat(store.actors(entity)).extracting(Actor::name).containsExactly("Kurt", "Michael");
		// an actor without versions gives an empty list
		long nobody = person("Nobody");
		assertThat(store.versions(entity, new Filter(null, null, nobody), null, 0, 50)).isEmpty();
	}

	@ParameterizedTest
	@CsvSource({ "0, 0, '4,3,2'", "1, -1, '3'" })
	void periodBoundsAreInclusive(long fromShiftMillis, long toShiftMillis, String expected) {
		int entity = entity(5, michael);
		Filter filter = new Filter(time(2).plusMillis(fromShiftMillis), time(4).plusMillis(toShiftMillis), null);
		assertThat(store.versions(entity, filter, null, 0, 50)).extracting(Version::version)
			.containsExactly(java.util.Arrays.stream(expected.split(",")).map(Integer::valueOf).toArray(Integer[]::new));
	}

	@ParameterizedTest
	@CsvSource({ "50, 50, 0", "51, 50, 1" })
	void pagingByVersion(int count, int firstPage, int secondPage) {
		int entity = entity(count, michael);
		List<Version> first = store.versions(entity, none(), null, 0, 50);
		assertThat(first).hasSize(firstPage);
		assertThat(first.get(0).version()).isEqualTo(count);
		int last = first.get(first.size() - 1).version();
		List<Version> next = store.versions(entity, none(), last, 0, 50);
		assertThat(next).hasSize(secondPage);
		if (secondPage > 0) {
			assertThat(next.get(0).version()).isEqualTo(1);
			// the offset reads the same page
			assertThat(store.versions(entity, none(), null, 50, 50)).extracting(Version::version).containsExactly(1);
		}
	}

	private static Filter none() {
		return new Filter(null, null, null);
	}

	/** Version {@code n} is at START + n hours. */
	private static Instant time(int version) {
		return START.plus(Duration.ofHours(version));
	}

	private static int entity(int versions, long person) {
		Integer id = owner.queryForObject(
				"INSERT INTO entities (metatype_id, name, version, created_by) VALUES (?, ?, ?, ?) RETURNING id",
				Integer.class, metatype, "Entity " + UUID.randomUUID(), versions, person);
		IntStream.rangeClosed(1, versions)
			.forEach((version) -> owner.update(
					"INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at) VALUES (?, ?, ?, ?)",
					id, version, person, java.sql.Timestamp.from(time(version))));
		return id;
	}

	private static long person(String name) {
		return owner.queryForObject("INSERT INTO persons (name, email) VALUES (?, ?) RETURNING id", Long.class, name,
				name.toLowerCase() + "-" + UUID.randomUUID() + "@example.org");
	}

}
