package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Migration db/migration/V43__dashboards.sql (US-055, US-060, US-062; design D1 of
 * us-055-dashboards): owners and grants, the unique names per scope and owner, the grid checks and
 * the cascade of widgets.
 */
class DashboardSchemaTests {

	private static OeaDatabase postgres;

	private static long kurt;

	private static long michael;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			kurt = person(statement, "Kurt", "kurt@example.org");
			michael = person(statement, "Michael", "michael@example.org");
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	private static long person(Statement statement, String name, String email) throws SQLException {
		try (ResultSet rs = statement
			.executeQuery("INSERT INTO persons (name, email) VALUES ('" + name + "', '" + email + "') RETURNING id")) {
			rs.next();
			return rs.getLong(1);
		}
	}

	private static UUID dashboard(Statement statement, String name, String scope, long owner) throws SQLException {
		try (ResultSet rs = statement.executeQuery("""
				INSERT INTO dashboards (name, scope, owner_id, created_by, updated_by)
				VALUES (%s, '%s', %d, %d, %d) RETURNING id::text""".formatted(name, scope, owner, owner, owner))) {
			rs.next();
			return UUID.fromString(rs.getString(1));
		}
	}

	private static void widget(Statement statement, UUID dashboard, int col, int width, int row, int height)
			throws SQLException {
		statement.executeUpdate("""
				INSERT INTO dashboard_widgets (dashboard_id, type, title, col, width, grid_row, height, config, created_by)
				VALUES ('%s', 'text', 'Notes', %d, %d, %d, %d, '{"content": ""}', %d)""".formatted(dashboard, col, width,
				row, height, kurt));
	}

	@Test
	void tablesAreOwnedByTheOwnerRoleAndTheRuntimeRoleMayDelete() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("""
						SELECT count(*) FROM pg_tables WHERE schemaname = 'public'
						AND tablename IN ('dashboards', 'dashboard_widgets') AND tableowner = 'oea_owner'""")) {
			rs.next();
			assertThat(rs.getInt(1)).isEqualTo(2);
		}
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			UUID id = dashboard(statement, "'Deletable'", "instance", kurt);
			assertThat(statement.executeUpdate("DELETE FROM dashboards WHERE id = '" + id + "'")).isEqualTo(1);
		}
	}

	@Test
	void sharedNamesAreUniqueIgnoringCaseAndSurroundingWhitespace() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			dashboard(statement, "'IT Investment'", "instance", kurt);
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> dashboard(statement, "btrim(' it investment ')", "instance", michael))
				.withMessageContaining("dashboards_instance_name");
			// the same name as a personal dashboard of someone is another namespace
			dashboard(statement, "'IT Investment'", "personal", michael);
		}
	}

	@Test
	void personalNamesAreUniquePerOwner() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			dashboard(statement, "'My analysis'", "personal", michael);
			dashboard(statement, "'My analysis'", "personal", kurt);
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> dashboard(statement, "'MY ANALYSIS'", "personal", michael))
				.withMessageContaining("dashboards_personal_name");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "' Padded '", "''" })
	void namesAreStoredTrimmedAndNotBlank(String name) throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> dashboard(statement, name, "instance", kurt))
				.withMessageContaining("dashboards_name_check");
		}
	}

	@Test
	void nameLengthIsLimitedTo200() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			dashboard(statement, "'" + "n".repeat(200) + "'", "instance", kurt);
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> dashboard(statement, "'" + "m".repeat(201) + "'", "instance", kurt))
				.withMessageContaining("dashboards_name_check");
		}
	}

	@Test
	void scopeIsInstanceOrPersonal() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> dashboard(statement, "'Scope'", "team", kurt))
				.withMessageContaining("dashboards_scope_check");
		}
	}

	/** col, width, row, height, accepted */
	@ParameterizedTest
	@CsvSource({ "1, 12, 1, 1, true", "12, 1, 500, 6, true", "5, 8, 1, 1, true", "5, 9, 1, 1, false",
			"1, 13, 1, 1, false", "0, 3, 1, 1, false", "13, 1, 1, 1, false", "1, 0, 1, 1, false", "1, 3, 0, 1, false",
			"1, 3, 501, 1, false", "1, 3, 1, 0, false", "1, 3, 1, 7, false" })
	void gridPositionsAreChecked(int col, int width, int row, int height, boolean accepted) throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			UUID id = dashboard(statement, "'Grid " + UUID.randomUUID() + "'", "instance", kurt);
			if (accepted) {
				widget(statement, id, col, width, row, height);
			}
			else {
				assertThatExceptionOfType(SQLException.class)
					.isThrownBy(() -> widget(statement, id, col, width, row, height))
					.withMessageContaining("dashboard_widgets_");
			}
		}
	}

	@Test
	void widgetsAreDeletedWithTheirDashboard() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			UUID id = dashboard(statement, "'Cascade'", "instance", kurt);
			widget(statement, id, 1, 3, 1, 1);
			widget(statement, id, 4, 9, 1, 3);
			assertThat(statement.executeUpdate("DELETE FROM dashboards WHERE id = '" + id + "'")).isEqualTo(1);
			try (ResultSet rs = statement
				.executeQuery("SELECT count(*) FROM dashboard_widgets WHERE dashboard_id = '" + id + "'")) {
				rs.next();
				assertThat(rs.getInt(1)).isZero();
			}
		}
	}

	@Test
	void widgetTypeIsKnown() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			UUID id = dashboard(statement, "'Types'", "instance", kurt);
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate("""
					INSERT INTO dashboard_widgets (dashboard_id, type, title, col, width, grid_row, height, config, created_by)
					VALUES ('%s', 'gauge', 'X', 1, 1, 1, 1, '{}', %d)""".formatted(id, kurt)))
				.withMessageContaining("dashboard_widgets_type_check");
		}
	}

}
