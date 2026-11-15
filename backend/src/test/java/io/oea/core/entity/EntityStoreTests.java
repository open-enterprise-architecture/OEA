package io.oea.core.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.core.entity.EntityStore.Fields;
import io.oea.core.entity.EntityStore.Restore;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The restore header of design D1 of us-101-entity-restore (migration V32, spec
 * repository/entity-restore, requirement "Version and result of a restore") and the per-entity
 * mark of D2, on the runtime role.
 */
class EntityStoreTests {

	private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");

	private static OeaDatabase postgres;

	private static JdbcTemplate owner;

	private static JdbcTemplate app;

	private static EntityStore store;

	private static PropertyValueStore values;

	private static long person;

	private static UUID element;

	private static UUID ownerProperty;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		owner = new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asSuperuser(postgres), true));
		app = new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asRole(postgres, "oea_app"), true));
		store = new EntityStore(app);
		values = new PropertyValueStore(app);
		person = owner.queryForObject("INSERT INTO persons (name, email) VALUES ('Kurt', ?) RETURNING id", Long.class,
				"kurt-" + UUID.randomUUID() + "@example.org");
		element = owner.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Element')
				RETURNING id""", UUID.class);
		ownerProperty = owner.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type)
				VALUES ('owner', 'owner', 'string') RETURNING id""", UUID.class);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void restoreHeaderRecordsTargetAndReason() {
		StoredEntity entity = entityAtVersion(3);

		int version = update(entity, new Restore(2, "Wrong owner"));

		assertThat(version).isEqualTo(4);
		assertThat(header(entity.id(), 4)).isEqualTo(Map.of("restored_from_version", 2, "change_reason", "Wrong owner"));
	}

	@Test
	void normalUpdateRecordsNeither() {
		StoredEntity entity = entityAtVersion(1);

		update(entity, null);

		assertThat(owner.queryForObject("""
				SELECT restored_from_version IS NULL AND change_reason IS NULL FROM entity_version_headers
				WHERE entity_id = ? AND version = 2""", Boolean.class, entity.id())).isTrue();
	}

	@Test
	void restoreWithoutReason() {
		StoredEntity entity = entityAtVersion(2);

		update(entity, new Restore(1, null));

		assertThat(owner.queryForObject(
				"SELECT change_reason IS NULL FROM entity_version_headers WHERE entity_id = ? AND version = 3",
				Boolean.class, entity.id())).isTrue();
	}

	@Test
	void restoredFromTheVersionBeforeIsAccepted() {
		// the new version 2 restores version 1: the last valid target
		StoredEntity entity = entityAtVersion(1);

		update(entity, new Restore(1, null));

		assertThat(header(entity.id(), 2)).containsEntry("restored_from_version", 1);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 2, 3 })
	void restoredFromOutsideTheRangeIsRejected(int fromVersion) {
		// the new version is 2: 0 is below 1, 2 is the new version itself, 3 is after it
		StoredEntity entity = entityAtVersion(1);

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> update(entity, new Restore(fromVersion, null)))
			.withMessageContaining("entity_version_headers_restored_from");
	}

	@Test
	void reasonOf1000CharactersIsAccepted() {
		StoredEntity entity = entityAtVersion(2);

		update(entity, new Restore(1, "r".repeat(1000)));

		assertThat(header(entity.id(), 3).get("change_reason")).asString().hasSize(1000);
	}

	@Test
	void reasonOf1001CharactersIsRejected() {
		StoredEntity entity = entityAtVersion(2);

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> update(entity, new Restore(1, "r".repeat(1001))))
			.withMessageContaining("entity_version_headers_reason");
	}

	@Test
	void reasonWithoutRestoreIsRejected() {
		StoredEntity entity = entityAtVersion(1);

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> app.update("""
					INSERT INTO entity_version_headers (entity_id, version, changed_by, change_reason)
					VALUES (?, 2, ?, 'Reason')""", entity.id(), person))
			.withMessageContaining("entity_version_headers_reason");
	}

	@Test
	void runtimeRoleCannotChangeARestoreHeader() {
		StoredEntity entity = entityAtVersion(2);
		update(entity, new Restore(1, "Reason"));

		assertThatExceptionOfType(Exception.class)
			.isThrownBy(() -> app.update("UPDATE entity_version_headers SET change_reason = 'x' WHERE entity_id = ?",
					entity.id()))
			.havingRootCause()
			.withMessageContaining("permission denied");
		assertThatExceptionOfType(Exception.class)
			.isThrownBy(() -> app.update("DELETE FROM entity_version_headers WHERE entity_id = ?", entity.id()))
			.havingRootCause()
			.withMessageContaining("permission denied");
	}

	@Test
	void lockReadsTheEntityById() {
		StoredEntity entity = entityAtVersion(3);

		assertThat(store.lock(entity.id())).map(StoredEntity::version).contains(3);
		assertThat(store.lock(Integer.MAX_VALUE)).isEmpty();
	}

	@Test
	void markOneEntity() {
		StoredEntity marked = entityAtVersion(1);
		StoredEntity other = entityAtVersion(1);

		values.mark(marked.id(), Set.of(ownerProperty), NOW);
		values.mark(marked.id(), Set.of(ownerProperty), NOW);

		assertThat(values.marks(marked.id())).containsExactly(ownerProperty);
		assertThat(values.marks(other.id())).isEmpty();
		assertThat(owner.queryForObject("SELECT count(*) FROM entity_mandatory_marks WHERE entity_id = ?",
				Integer.class, marked.id())).isEqualTo(1);
	}

	private static StoredEntity entityAtVersion(int version) {
		int id = store.create(new Fields(element, "Entity " + UUID.randomUUID(), "", true, null), Map.of(), person,
				NOW);
		StoredEntity entity = store.lock(id).orElseThrow();
		for (int i = 1; i < version; i++) {
			update(entity, null);
			entity = store.lock(id).orElseThrow();
		}
		return entity;
	}

	private static int update(StoredEntity entity, Restore restore) {
		return store.update(entity, new Fields(element, entity.name(), "v" + (entity.version() + 1), true, null),
				List.of(new EntityStore.FieldChange("description", entity.description(),
						"v" + (entity.version() + 1))),
				Map.of(), Map.of(), person, NOW, restore);
	}

	private static Map<String, Object> header(int entityId, int version) {
		Map<String, Object> row = owner.queryForMap("""
				SELECT restored_from_version, change_reason FROM entity_version_headers
				WHERE entity_id = ? AND version = ?""", entityId, version);
		row.values().removeIf((value) -> value == null);
		return row;
	}

}
