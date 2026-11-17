package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Migration db/migration/V39__viewpoints.sql (design D1 of us-090-viewpoints). */
class ViewpointSchemaTests {

	private static OeaDatabase postgres;

	private static String personId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			personId = single(statement,
					"INSERT INTO persons (name, email, status) VALUES ('Kurt', 'kurt@example.org', 'active') RETURNING id::text");
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@ParameterizedTest
	@CsvSource({ "255, 0, true", "256, 0, false", "5, 1000, true", "5, 1001, false" })
	void limits(int name, int description, boolean accepted) throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String sql = "INSERT INTO viewpoints (id, name, description, created_by) VALUES (gen_random_uuid(), "
					+ "left(md5(random()::text) || repeat('n', " + name + "), " + name + "), "
					+ ((description > 0) ? "repeat('d', " + description + ")" : "NULL") + ", " + personId + ")";
			if (accepted) {
				statement.execute(sql);
			}
			else {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.execute(sql))
					.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23514"));
			}
		}
	}

	@Test
	void uniqueNameAndCascadingLinks() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String viewpoint = single(statement, "INSERT INTO viewpoints (id, name, created_by) VALUES "
					+ "(gen_random_uuid(), 'Landscape', " + personId + ") RETURNING id::text");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.execute("INSERT INTO viewpoints (id, name, created_by) VALUES "
						+ "(gen_random_uuid(), 'landscape', " + personId + ")"))
				.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23505"));
			String metatype = single(statement, "INSERT INTO metatypes (config_id, name, is_connection) VALUES "
					+ "('00000000-0000-0000-0000-000000000001', 'Schema Type', false) RETURNING id::text");
			statement.execute("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES ('" + viewpoint + "', '"
					+ metatype + "')");
			statement.execute("DELETE FROM metatypes WHERE id = '" + metatype + "'");
			assertThat(single(statement, "SELECT count(*)::text FROM viewpoint_metatypes WHERE viewpoint_id = '"
					+ viewpoint + "'")).isEqualTo("0");
			statement.execute("DELETE FROM viewpoints WHERE id = '" + viewpoint + "'");
		}
	}

	private static String single(Statement statement, String sql) throws SQLException {
		try (ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

}
