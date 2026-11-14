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

/**
 * Migration db/migration/V29__history_visibility.sql (us-098-entity-history D3): owner, grants,
 * the stored mode {@code hide} only, one row per role and metatype, and the removal of the rows
 * of a deleted role.
 */
class HistoryVisibilitySchemaTests {

	private static OeaDatabase postgres;

	private static String metatypeId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			try (ResultSet rs = statement.executeQuery("""
					INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Element')
					RETURNING id::text""")) {
				rs.next();
				metatypeId = rs.getString(1);
			}
			statement.executeUpdate("INSERT INTO authorization_roles (id, name) VALUES ('schema-auditor', 'Auditor')");
			statement.executeUpdate("INSERT INTO authorization_roles (id, name) VALUES ('schema-removed', 'Removed')");
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void tableIsOwnedByTheOwnerRole() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(
						"SELECT tableowner FROM pg_tables WHERE schemaname = 'public' AND tablename = 'history_visibility_settings'")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getString(1)).isEqualTo("oea_owner");
		}
	}

	@Test
	void hideIsStoredAndDeletedByTheRuntimeRole() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThat(insert(statement, "viewer", "hide")).isEqualTo(1);
			assertThat(count(statement, "viewer")).isEqualTo(1);
			assertThat(statement.executeUpdate("DELETE FROM history_visibility_settings WHERE role_id = 'viewer'"))
				.isEqualTo(1);
		}
	}

	@Test
	void aSecondRowForTheSamePairIsRejected() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			insert(statement, "schema-auditor", "hide");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insert(statement, "schema-auditor", "hide"))
				.withMessageContaining("history_visibility_settings_pkey");
		}
	}

	@Test
	void theDefaultModeIsNotStored() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insert(statement, "enterprise-architect", "show-restricted"))
				.withMessageContaining("history_visibility_settings_mode_check");
		}
	}

	@Test
	void runtimeRoleCannotUpdateRows() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("UPDATE history_visibility_settings SET mode = 'hide'"))
				.withMessageContaining("permission denied");
		}
	}

	@Test
	void deletingARoleRemovesItsRows() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			insert(statement, "schema-removed", "hide");
		}
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			statement.executeUpdate("DELETE FROM authorization_roles WHERE id = 'schema-removed'");
			assertThat(count(statement, "schema-removed")).isZero();
		}
	}

	private static int insert(Statement statement, String role, String mode) throws SQLException {
		return statement.executeUpdate("INSERT INTO history_visibility_settings (role_id, metatype_id, mode) VALUES ('"
				+ role + "', '" + metatypeId + "', '" + mode + "')");
	}

	private static int count(Statement statement, String role) throws SQLException {
		try (ResultSet rs = statement
			.executeQuery("SELECT count(*) FROM history_visibility_settings WHERE role_id = '" + role + "'")) {
			rs.next();
			return rs.getInt(1);
		}
	}

}
