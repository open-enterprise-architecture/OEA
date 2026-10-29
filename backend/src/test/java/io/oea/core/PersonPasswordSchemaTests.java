package io.oea.core;

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

/** Migration db/migration/V10__person_passwords.sql (design D3 of us-011-local-password). */
class PersonPasswordSchemaTests {

	private static OeaDatabase postgres;

	private static long anna;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement();
				ResultSet rs = statement.executeQuery(
						"INSERT INTO persons (name, email, status) VALUES ('Anna', 'anna@example.org', 'active') RETURNING id")) {
			rs.next();
			anna = rs.getLong(1);
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void aCredentialHasExactlyOneOwner() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate("INSERT INTO system_admin_accounts (login_name) VALUES ('admin')");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO local_credentials (type) VALUES ('password')"))
				.withMessageContaining("local_credentials_owner");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO local_credentials (type, person_id, "
						+ "system_admin_account_id) SELECT 'password', " + anna + ", id FROM system_admin_accounts"))
				.withMessageContaining("local_credentials_owner");
		}
	}

	@Test
	void aPersonHasAtMostOneActivePassword() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate("INSERT INTO local_credentials (type, person_id) VALUES ('password', " + anna + ")");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement
					.executeUpdate("INSERT INTO local_credentials (type, person_id) VALUES ('password', " + anna + ")"))
				.withMessageContaining("local_credentials_one_active_password_per_person");
		}
	}

}
