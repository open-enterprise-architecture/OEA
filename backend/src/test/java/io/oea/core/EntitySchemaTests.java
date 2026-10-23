package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Migration db/migration/V6__entities.sql: owners, grants (append-only history), the unique
 * external ID and the one-value checks.
 */
class EntitySchemaTests {

	private static final List<String> TABLES = List.of("entities", "entity_property_values",
			"entity_version_headers", "entity_system_field_history", "entity_property_value_history");

	private static final List<String> HISTORY = List.of("entity_version_headers", "entity_system_field_history",
			"entity_property_value_history");

	private static OeaDatabase postgres;

	private static long personId;

	private static String metatypeId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			try (ResultSet rs = statement.executeQuery(
					"INSERT INTO persons (name, email) VALUES ('Writer', 'writer@example.org') RETURNING id")) {
				rs.next();
				personId = rs.getLong(1);
			}
			try (ResultSet rs = statement.executeQuery("""
					INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Element')
					RETURNING id::text""")) {
				rs.next();
				metatypeId = rs.getString(1);
			}
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void tablesAreOwnedByTheOwnerRole() throws SQLException {
		assertThat(query("SELECT tablename || ':' || tableowner FROM pg_tables WHERE schemaname = 'public' "
				+ "AND tablename IN ('" + String.join("','", TABLES) + "')"))
			.hasSize(TABLES.size())
			.allMatch((row) -> row.endsWith(":oea_owner"));
	}

	@Test
	void historyIsAppendOnlyForTheRuntimeRole() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			int entityId = insertEntity(statement, null);
			statement.executeUpdate("INSERT INTO entity_version_headers (entity_id, version, changed_by) VALUES ("
					+ entityId + ", 1, " + personId + ")");
			for (String table : HISTORY) {
				assertThatExceptionOfType(SQLException.class).as(table)
					.isThrownBy(() -> statement.executeUpdate("UPDATE " + table + " SET changed_by = changed_by"))
					.withMessageContaining("permission denied");
				assertThatExceptionOfType(SQLException.class).as(table)
					.isThrownBy(() -> statement.executeUpdate("DELETE FROM " + table))
					.withMessageContaining("permission denied");
			}
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("DELETE FROM entities"))
				.withMessageContaining("permission denied");
			assertThat(statement.executeUpdate("DELETE FROM entity_property_values")).isZero();
		}
	}

	@Test
	void externalIdIsUniqueWhereSet() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			insertEntity(statement, "'git:org/unique'");
			insertEntity(statement, null);
			insertEntity(statement, null);
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insertEntity(statement, "'git:org/unique'"))
				.withMessageContaining("entities_external_id");
		}
	}

	@Test
	void namesMustNotBeBlank() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate(
					"INSERT INTO entities (metatype_id, name, created_by) VALUES ('" + metatypeId + "', '  ', "
							+ personId + ")"))
				.withMessageContaining("entities_name_check");
		}
	}

	@Test
	void propertyValuesHoldAtMostOneValue() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			int entityId = insertEntity(statement, null);
			String definition;
			try (Connection su = OeaDatabases.asSuperuser(postgres); Statement owner = su.createStatement();
					ResultSet rs = owner.executeQuery("""
							INSERT INTO property_definitions (name, sql_column_name, data_type)
							VALUES ('Cost', 'cost', 'integer') RETURNING id::text""")) {
				rs.next();
				definition = rs.getString(1);
			}
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate(
					"INSERT INTO entity_property_values (entity_id, property_def_id, value_text, value_int) VALUES ("
							+ entityId + ", '" + definition + "', 'x', 1)"))
				.withMessageContaining("entity_property_values_one_value");
			statement.executeUpdate("INSERT INTO entity_property_values (entity_id, property_def_id, value_int) VALUES ("
					+ entityId + ", '" + definition + "', 1)");
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate(
					"INSERT INTO entity_property_values (entity_id, property_def_id, value_int) VALUES (" + entityId
							+ ", '" + definition + "', 2)"))
				.withMessageContaining("entity_property_values_unique");
		}
	}

	private static int insertEntity(Statement statement, String externalId) throws SQLException {
		try (ResultSet rs = statement.executeQuery("INSERT INTO entities (metatype_id, name, created_by, external_id) "
				+ "VALUES ('" + metatypeId + "', 'Entity', " + personId + ", " + externalId + ") RETURNING id")) {
			rs.next();
			return rs.getInt(1);
		}
	}

	private static List<String> query(String sql) throws SQLException {
		List<String> rows = new ArrayList<>();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			while (rs.next()) {
				rows.add(rs.getString(1));
			}
		}
		return rows;
	}

}
