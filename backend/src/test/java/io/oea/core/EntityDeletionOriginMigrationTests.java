package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.config.migration.MigrationSet;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Migration db/migration/V49__entity_deletion_origin.sql (s17-entity-delete, Data Model): the
 * column {@code deleted_with_entity_id}, its check and its partial index, and the rows that were
 * deleted before.
 */
class EntityDeletionOriginMigrationTests {

	private static final String INSTANCE_CONFIG = "00000000-0000-0000-0000-000000000001";

	private static OeaDatabase postgres;

	private static long personId;

	private static String metatype;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		personId = Long.parseLong(single(postgres, "INSERT INTO persons (name, email) VALUES ('Kurt', "
				+ "'kurt-deletion@example.org') RETURNING id"));
		metatype = single(postgres, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG
				+ "', 'Application deletion') RETURNING id::text");
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void anActiveEntityCannotHaveAnOrigin() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			int origin = entity(statement, "Origin");
			int active = entity(statement, "Active");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement
					.executeUpdate("UPDATE entities SET deleted_with_entity_id = " + origin + " WHERE id = " + active))
				.withMessageContaining("entities_deleted_with_deleted");
		}
	}

	@Test
	void aDeletedEntityMayHaveAnOriginOrNone() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			int origin = entity(statement, "Origin");
			int child = entity(statement, "Child");
			int alone = entity(statement, "Alone");
			assertThat(statement.executeUpdate("UPDATE entities SET deleted_at = now(), deleted_by = " + personId
					+ ", deleted_with_entity_id = " + origin + " WHERE id = " + child)).isOne();
			assertThat(statement.executeUpdate(
					"UPDATE entities SET deleted_at = now(), deleted_by = " + personId + " WHERE id = " + alone))
				.isOne();
			assertThat(single(postgres, "SELECT deleted_with_entity_id FROM entities WHERE id = " + child))
				.isEqualTo(Integer.toString(origin));
			assertThat(single(postgres, "SELECT deleted_with_entity_id IS NULL FROM entities WHERE id = " + alone))
				.isEqualTo("t");
		}
	}

	@Test
	void theOriginIsAnExistingEntity() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			int child = entity(statement, "Orphan");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("UPDATE entities SET deleted_at = now(), deleted_by = "
						+ personId + ", deleted_with_entity_id = 2000000000 WHERE id = " + child))
				.withMessageContaining("deleted_with_entity_id");
		}
	}

	@Test
	void thePartialIndexExists() throws SQLException {
		assertThat(single(postgres, "SELECT count(*) FROM pg_indexes WHERE indexname = 'entities_deleted_with' "
				+ "AND indexdef LIKE '%WHERE (deleted_with_entity_id IS NOT NULL)%'")).isEqualTo("1");
	}

	@Test
	void entitiesDeletedBeforeKeepNoOrigin() throws Exception {
		OeaDatabase old = OeaDatabases.freshProvisioned();
		try {
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.target("48")
				.load()
				.migrate();
			long person = Long.parseLong(single(old, "INSERT INTO persons (name, email) VALUES ('Kurt', "
					+ "'kurt-before-deletion@example.org') RETURNING id"));
			String type = single(old, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG
					+ "', 'Application') RETURNING id::text");
			String entity = single(old, "INSERT INTO entities (metatype_id, name, created_by, deleted_at, deleted_by) "
					+ "VALUES ('" + type + "', 'SAP ERP', " + person + ", now(), " + person + ") RETURNING id");
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.load()
				.migrate();
			assertThat(single(old, "SELECT deleted_with_entity_id IS NULL FROM entities WHERE id = " + entity))
				.isEqualTo("t");
			assertThat(single(old, "SELECT deleted_at IS NOT NULL FROM entities WHERE id = " + entity)).isEqualTo("t");
		}
		finally {
			old.stop();
		}
	}

	private static int entity(Statement statement, String name) throws SQLException {
		try (ResultSet rs = statement.executeQuery("INSERT INTO entities (metatype_id, name, created_by) VALUES ('"
				+ metatype + "', '" + name + "', " + personId + ") RETURNING id")) {
			rs.next();
			return rs.getInt(1);
		}
	}

	private static String single(OeaDatabase database, String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(database); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

}
