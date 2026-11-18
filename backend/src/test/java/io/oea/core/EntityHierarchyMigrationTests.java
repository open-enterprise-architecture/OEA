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
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Migration db/migration/V42__entity_hierarchy.sql (us-135-component-hierarchy D1): the built-in
 * metatype "Structuring aid", the composition mark, the history field {@code parent_entity_id}
 * and the entities that existed before.
 */
class EntityHierarchyMigrationTests {

	private static final String INSTANCE_CONFIG = "00000000-0000-0000-0000-000000000001";

	private static final String STRUCTURING_AID = "c3f523b8-f07f-5475-9ab0-6f2ea39b537e";

	private static OeaDatabase postgres;

	private static long personId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		personId = Long.parseLong(single(postgres, "INSERT INTO persons (name, email) VALUES ('Kurt', "
				+ "'kurt-hierarchy@example.org') RETURNING id"));
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void theBuiltInMetatypeExistsOnceInTheInstanceMetamodel() throws SQLException {
		assertThat(single(postgres, "SELECT count(*) FROM metatypes WHERE id = '" + STRUCTURING_AID + "'"))
			.isEqualTo("1");
		assertThat(single(postgres, "SELECT count(*) FROM metatypes WHERE lower(name) = 'structuring aid'"))
			.isEqualTo("1");
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("""
						SELECT config_id::text, scope, is_connection, abbreviation, source_package_id, is_composition
						FROM metatypes WHERE id = '%s'""".formatted(STRUCTURING_AID))) {
			rs.next();
			assertThat(rs.getString(1)).isEqualTo(INSTANCE_CONFIG);
			assertThat(rs.getString(2)).isEqualTo("built-in");
			assertThat(rs.getBoolean(3)).isFalse();
			assertThat(rs.getString(4)).isNull();
			assertThat(rs.getString(5)).isNull();
			assertThat(rs.getBoolean(6)).isFalse();
		}
		assertThat(single(postgres, "SELECT count(*) FROM metatype_property_mappings WHERE metatype_id = '"
				+ STRUCTURING_AID + "'")).isEqualTo("0");
	}

	@Test
	void theSolutionArchitectMayWriteEntitiesButNotDeleteThem() throws SQLException {
		assertThat(single(postgres, "SELECT count(*) FROM role_permissions WHERE role_id = 'solution-architect' "
				+ "AND permission_id = 'entity:write'")).isEqualTo("1");
		assertThat(single(postgres, "SELECT count(*) FROM role_permissions WHERE role_id = 'solution-architect' "
				+ "AND permission_id = 'entity:delete'")).isEqualTo("0");
	}

	@Test
	void aSecondCompositionInTheInstanceIsRejected() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String first = metatype(statement, "Composed of " + UUID.randomUUID(), true);
			String second = metatype(statement, "Contains " + UUID.randomUUID(), true);
			assertThat(statement.executeUpdate("UPDATE metatypes SET is_composition = true WHERE id = '" + first + "'"))
				.isOne();
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement
					.executeUpdate("UPDATE metatypes SET is_composition = true WHERE id = '" + second + "'"))
				.withMessageContaining("metatypes_one_composition");
			// moving the mark is possible once it is removed
			statement.executeUpdate("UPDATE metatypes SET is_composition = false WHERE id = '" + first + "'");
			assertThat(statement.executeUpdate("UPDATE metatypes SET is_composition = true WHERE id = '" + second + "'"))
				.isOne();
			statement.executeUpdate("UPDATE metatypes SET is_composition = false WHERE id = '" + second + "'");
		}
	}

	@Test
	void anElementMetatypeCannotBeTheComposition() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String element = metatype(statement, "Element " + UUID.randomUUID(), false);
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement
					.executeUpdate("UPDATE metatypes SET is_composition = true WHERE id = '" + element + "'"))
				.withMessageContaining("metatypes_composition_is_connection");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "name", "description", "is_logical", "metatype_id", "source_entity_id", "target_entity_id",
			"parent_entity_id" })
	void theHistoryAcceptsTheSystemFields(String field) throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			int entity = entity(statement);
			assertThat(statement.executeUpdate(history(entity, field))).isOne();
		}
	}

	@Test
	void theHistoryStillRejectsAnUnknownField() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			int entity = entity(statement);
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate(history(entity, "parent")))
				.withMessageContaining("entity_system_field_history_field_name_check");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(history(entity, "is_structural_grouper")))
				.withMessageContaining("entity_system_field_history_field_name_check");
		}
	}

	@Test
	void existingEntitiesKeepNoParent() throws Exception {
		OeaDatabase old = OeaDatabases.freshProvisioned();
		try {
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.target("41")
				.load()
				.migrate();
			long person = Long.parseLong(single(old, "INSERT INTO persons (name, email) VALUES ('Kurt', "
					+ "'kurt-before@example.org') RETURNING id"));
			String metatype = single(old, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG
					+ "', 'Application') RETURNING id::text");
			String entity = single(old, "INSERT INTO entities (metatype_id, name, created_by) VALUES ('" + metatype
					+ "', 'SAP ERP', " + person + ") RETURNING id");
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.load()
				.migrate();
			assertThat(single(old, "SELECT parent_entity_id IS NULL FROM entities WHERE id = " + entity))
				.isEqualTo("t");
			assertThat(single(old, "SELECT is_structural_grouper OR auto_created FROM entities WHERE id = " + entity))
				.isEqualTo("f");
			assertThat(single(old, "SELECT count(*) FROM metatypes WHERE id = '" + STRUCTURING_AID + "'"))
				.isEqualTo("1");
			assertThat(single(old, "SELECT count(*) FROM metatypes WHERE is_composition")).isEqualTo("0");
		}
		finally {
			old.stop();
		}
	}

	@Test
	void theMigrationFailsWithAClearMessageWhenTheNameIsTaken() throws Exception {
		OeaDatabase old = OeaDatabases.freshProvisioned();
		try {
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.target("41")
				.load()
				.migrate();
			single(old, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG
					+ "', 'Structuring Aid') RETURNING id::text");
			assertThatExceptionOfType(org.flywaydb.core.api.FlywayException.class).isThrownBy(() -> MigrationSet.CORE
				.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.load()
				.migrate()).withMessageContaining("Structuring aid");
		}
		finally {
			old.stop();
		}
	}

	private static String history(int entity, String field) {
		return "INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value, "
				+ "changed_by) VALUES (" + entity + ", 2, '" + field + "', NULL, '1', " + personId + ")";
	}

	private static String metatype(Statement statement, String name, boolean connection) throws SQLException {
		try (ResultSet rs = statement.executeQuery("INSERT INTO metatypes (config_id, name, is_connection) VALUES ('"
				+ INSTANCE_CONFIG + "', '" + name + "', " + connection + ") RETURNING id::text")) {
			rs.next();
			return rs.getString(1);
		}
	}

	private static int entity(Statement statement) throws SQLException {
		String metatype = metatype(statement, "Application " + UUID.randomUUID(), false);
		try (ResultSet rs = statement.executeQuery("INSERT INTO entities (metatype_id, name, created_by) VALUES ('"
				+ metatype + "', 'SAP ERP', " + personId + ") RETURNING id")) {
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
