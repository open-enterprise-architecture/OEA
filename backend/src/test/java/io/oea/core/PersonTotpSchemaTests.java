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

/** Migration db/migration/V12__person_totp.sql (design D4 of us-021-person-totp). */
class PersonTotpSchemaTests {

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
	void aLoginTransactionHasExactlyOneOwner() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate("INSERT INTO system_admin_accounts (login_name) VALUES ('admin')");
			statement.executeUpdate("INSERT INTO login_transactions (token_hash, person_id, expires_at) VALUES "
					+ "(sha256('a'), " + anna + ", now() + interval '5 minutes')");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(
						"INSERT INTO login_transactions (token_hash, expires_at) VALUES (sha256('b'), now())"))
				.withMessageContaining("login_transactions_owner");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO login_transactions (token_hash, person_id, "
						+ "system_admin_account_id, expires_at) SELECT sha256('c'), " + anna + ", id, now() "
						+ "FROM system_admin_accounts"))
				.withMessageContaining("login_transactions_owner");
		}
	}

}
