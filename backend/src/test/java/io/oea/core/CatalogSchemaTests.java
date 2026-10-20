package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.testsupport.containers.OeaDatabases;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Migration db/migration/V7__catalogs.sql: owner, grants, the case-insensitive unique name and
 * the checks of scope, join mode and name.
 */
class CatalogSchemaTests {

	private static PostgreSQLContainer postgres;

	private static long personId;

	private static String metatypeId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			try (ResultSet rs = statement.executeQuery(
					"INSERT INTO persons (name, email) VALUES ('Kurt', 'kurt@example.org') RETURNING id")) {
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
	void tableIsOwnedByTheOwnerRole() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement
					.executeQuery("SELECT tableowner FROM pg_tables WHERE schemaname = 'public' AND tablename = 'catalogs'")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getString(1)).isEqualTo("oea_owner");
		}
	}

	@Test
	void runtimeRoleCannotDeleteCatalogs() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			insert(statement, "'Deletable'", "'instance'", "'aggregate'");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("DELETE FROM catalogs"))
				.withMessageContaining("permission denied");
		}
	}

	@Test
	void namesAreUniqueIgnoringCaseAndSurroundingWhitespace() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			insert(statement, "'Application Inventory'", "'instance'", "'aggregate'");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insert(statement, "btrim(' application inventory ')", "'personal'", "'rows'"))
				.withMessageContaining("catalogs_name");
		}
	}

	@Test
	void namesAreStoredTrimmedAndNotBlank() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insert(statement, "' Padded '", "'instance'", "'aggregate'"))
				.withMessageContaining("catalogs_name_check");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insert(statement, "''", "'instance'", "'aggregate'"))
				.withMessageContaining("catalogs_name_check");
		}
	}

	@Test
	void scopeAndJoinModeAreRestricted() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insert(statement, "'Scoped'", "'team'", "'aggregate'"))
				.withMessageContaining("catalogs_scope_check");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insert(statement, "'Joined'", "'instance'", "'columns'"))
				.withMessageContaining("catalogs_default_join_mode_check");
		}
	}

	@Test
	void configurationStartsWithEmptyLists() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String id = insert(statement, "'Empty Configuration'", "'instance'", "'aggregate'");
			try (ResultSet rs = statement.executeQuery("SELECT config::text, description FROM catalogs WHERE id = '"
					+ id + "'")) {
				rs.next();
				assertThat(rs.getString(1))
					.isEqualTo("{\"columns\": [], \"savedViews\": [], \"savedFilters\": [], \"joinDefinitions\": []}");
				assertThat(rs.getString(2)).isEmpty();
			}
		}
	}

	private static String insert(Statement statement, String name, String scope, String joinMode)
			throws SQLException {
		try (ResultSet rs = statement.executeQuery("""
				INSERT INTO catalogs (name, primary_metatype_id, scope, default_join_mode, created_by, last_modified_by)
				VALUES (%s, '%s', %s, %s, %d, %d) RETURNING id::text""".formatted(name, metatypeId, scope, joinMode,
				personId, personId))) {
			rs.next();
			return rs.getString(1);
		}
	}

}
