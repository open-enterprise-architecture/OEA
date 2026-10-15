package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Migration db/migration/V2__bootstrapping.sql: owners, runtime grants, constraints. */
class BootstrappingSchemaTests {

	private static final List<String> TABLES = List.of("system_admin_accounts", "system_settings", "local_credentials",
			"password_credentials", "bootstrap_setup_tokens");

	private static PostgreSQLContainer postgres;

	@BeforeAll
	static void start() {
		postgres = OeaDatabases.freshMigrated();
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void tablesAreOwnedByTheOwnerRole() throws SQLException {
		List<String> owners = new ArrayList<>();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("SELECT tablename || ':' || tableowner FROM pg_tables "
						+ "WHERE schemaname = 'public' AND tablename IN ('" + String.join("','", TABLES) + "')")) {
			while (rs.next()) {
				owners.add(rs.getString(1));
			}
		}
		assertThat(owners).hasSize(TABLES.size()).allMatch((row) -> row.endsWith(":oea_owner"));
	}

	@Test
	void runtimeRoleCannotDeleteAccountsOrCredentials() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			for (String table : List.of("system_admin_accounts", "local_credentials", "password_credentials",
					"system_settings")) {
				assertThatExceptionOfType(SQLException.class).as(table)
					.isThrownBy(() -> statement.executeUpdate("DELETE FROM " + table))
					.withMessageContaining("permission denied");
			}
			assertThat(statement.executeUpdate("DELETE FROM bootstrap_setup_tokens")).isZero();
		}
	}

	@Test
	void instanceSlugIsImmutableAndChecked() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO system_settings (id, instance_slug) VALUES (1, 'A')"))
				.withMessageContaining("system_settings_instance_slug_check");
			statement.executeUpdate("INSERT INTO system_settings (id, instance_slug) VALUES (1, 'acme-corp')");
			assertThat(statement.executeUpdate("UPDATE system_settings SET analytics_max_rows = 500")).isEqualTo(1);
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("UPDATE system_settings SET instance_slug = 'other'"))
				.withMessageContaining("immutable");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO system_settings (id, instance_slug) VALUES (2, 'x2y')"))
				.withMessageContaining("system_settings_id_check");
		}
	}

	@Test
	void passwordCredentialsAcceptOnlyArgon2id() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate("INSERT INTO system_admin_accounts (id, login_name) VALUES "
					+ "('00000000-0000-4000-8000-000000000001', 'admin')");
			statement.executeUpdate("INSERT INTO local_credentials (id, system_admin_account_id, type) VALUES "
					+ "('00000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000001', 'password')");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO password_credentials (credential_id, password_hash) "
						+ "VALUES ('00000000-0000-4000-8000-000000000002', '$2a$10$bcrypt')"))
				.withMessageContaining("password_credentials_password_hash_check");
		}
	}

}
