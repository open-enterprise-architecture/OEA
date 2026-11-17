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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Migration db/migration/V36__open_issue_resolution.sql (design D1 of us-153-open-issues): the
 * resolution and the kept value of open issues, and the snapshots of solutions.
 */
class OpenIssueSchemaTests {

	private static OeaDatabase postgres;

	private static String personId;

	private static String metatypeId;

	private static String propertyId;

	private static String entityId;

	private static String solutionId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			personId = single(statement,
					"INSERT INTO persons (name, email, status) VALUES ('Michael', 'michael@example.org', 'active') RETURNING id::text");
			metatypeId = single(statement, """
					INSERT INTO metatypes (config_id, name, is_connection)
					VALUES ('00000000-0000-0000-0000-000000000001', 'Issue Application', false) RETURNING id::text""");
			propertyId = single(statement, """
					INSERT INTO property_definitions (name, sql_column_name, data_type)
					VALUES ('Issue Contact', 'issue_contact', 'string') RETURNING id::text""");
			entityId = single(statement, "INSERT INTO entities (metatype_id, name, created_by) VALUES ('" + metatypeId
					+ "', 'Salesforce', " + personId + ") RETURNING id::text");
			solutionId = single(statement, "INSERT INTO solutions (id, name, owner_id, created_by) VALUES (gen_random_uuid(), "
					+ "'Schema', " + personId + ", " + personId + ") RETURNING id::text");
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void resolutionIsAllOrNothing() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String issue = single(statement, issue("f1", "'mandatory-missing'", "NULL"));
			statement.execute("UPDATE solution_open_issues SET resolution = 'value', resolved_at = now(), resolved_by = "
					+ personId + " WHERE id = '" + issue + "'");
			String partial = single(statement, issue("f2", "'mandatory-missing'", "NULL"));
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.execute(
						"UPDATE solution_open_issues SET resolution = 'value' WHERE id = '" + partial + "'"))
				.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23514"));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "value", "removed", "automatic" })
	void storedResolutions(String resolution) throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String issue = single(statement, issue("r-" + resolution, "'invalid-value'", "NULL"));
			String update = "UPDATE solution_open_issues SET resolution = '" + resolution + "', resolved_at = now(), "
					+ "resolved_by = " + personId + " WHERE id = '" + issue + "'";
			if ("automatic".equals(resolution)) {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.execute(update))
					.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23514"));
			}
			else {
				statement.execute(update);
			}
		}
	}

	@Test
	void keptValueExactlyForNotInMetamodel() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			single(statement, issue("k1", "'not-in-metamodel'", "'CC-100'"));
			for (String invalid : List.of(issue("k2", "'not-in-metamodel'", "NULL"),
					issue("k3", "'invalid-value'", "'CC-100'"), issue("k4", "'unknown'", "NULL"))) {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.execute(invalid))
					.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23514"));
			}
		}
	}

	@Test
	void runtimeUpdatesOnlyTheResolution() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			assertThat(strings(statement, """
					SELECT column_name FROM information_schema.column_privileges
					WHERE table_name = 'solution_open_issues' AND grantee = 'oea_app' AND privilege_type = 'UPDATE'
					ORDER BY column_name""")).containsExactly("resolution", "resolved_at", "resolved_by");
			assertThat(strings(statement, """
					SELECT privilege_type FROM information_schema.role_table_grants
					WHERE table_name = 'solution_metamodel_snapshots' AND grantee = 'oea_app' ORDER BY privilege_type"""))
				.containsExactly("DELETE", "INSERT", "SELECT");
		}
	}

	@Test
	void snapshotIsUniqueAndKeepsItsDefinition() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.execute(snapshot("NULL"));
			statement.execute(snapshot("'high'"));
			for (String duplicate : List.of(snapshot("NULL"), snapshot("'high'"))) {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.execute(duplicate))
					.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23505"));
			}
		}
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.execute("DELETE FROM property_definitions WHERE id = '" + propertyId + "'"))
				.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23503"));
		}
	}

	private static String issue(String field, String kind, String keptValue) {
		return "INSERT INTO solution_open_issues (id, solution_id, entity_id, field, property_def_id, kind, kept_value)"
				+ " VALUES (gen_random_uuid(), '" + solutionId + "', " + entityId + ", '" + field + "', '" + propertyId
				+ "', " + kind + ", " + keptValue + ") RETURNING id::text";
	}

	private static String snapshot(String enumValue) {
		return "INSERT INTO solution_metamodel_snapshots (id, solution_id, metatype_id, property_def_id, enum_value,"
				+ " created_by) VALUES (gen_random_uuid(), '" + solutionId + "', '" + metatypeId + "', '" + propertyId
				+ "', " + enumValue + ", " + personId + ")";
	}

	private static List<String> strings(Statement statement, String sql) throws SQLException {
		List<String> values = new ArrayList<>();
		try (ResultSet rs = statement.executeQuery(sql)) {
			while (rs.next()) {
				values.add(rs.getString(1));
			}
		}
		return values;
	}

	private static String single(Statement statement, String sql) throws SQLException {
		try (ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

}
