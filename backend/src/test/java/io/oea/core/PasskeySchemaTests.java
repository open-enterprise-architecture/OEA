package io.oea.core;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Migration db/migration/V13__passkeys.sql (design D2 of us-022-passkeys). */
class PasskeySchemaTests {

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
	void aWebAuthnCredentialIdIsUnique() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate("INSERT INTO local_credentials (id, person_id, type) VALUES "
					+ "('00000000-0000-0000-0000-000000000001', " + anna + ", 'passkey'), "
					+ "('00000000-0000-0000-0000-000000000002', " + anna + ", 'passkey')");
			statement.executeUpdate("INSERT INTO passkey_credentials (credential_id, webauthn_credential_id, "
					+ "attested_credential_data, backup_eligible, backed_up) VALUES "
					+ "('00000000-0000-0000-0000-000000000001', '\\x01', '\\x02', true, true)");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO passkey_credentials (credential_id, "
						+ "webauthn_credential_id, attested_credential_data, backup_eligible, backed_up) VALUES "
						+ "('00000000-0000-0000-0000-000000000002', '\\x01', '\\x02', true, true)"))
				.withMessageContaining("passkey_credentials_webauthn_credential_id_key");
		}
	}

	@Test
	void aUserHandleHas64Bytes() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(
						"UPDATE persons SET webauthn_user_handle = '\\x0102' WHERE id = " + anna))
				.withMessageContaining("webauthn_user_handle");
		}
	}

	@Test
	void challengesHaveTheOwnerOfTheirPurpose() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatNoException().isThrownBy(() -> statement.executeUpdate("INSERT INTO webauthn_challenges "
					+ "(id, purpose, challenge, expires_at) VALUES (gen_random_uuid(), 'login', "
					+ "sha256('a'::bytea), now())"));
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO webauthn_challenges "
						+ "(id, purpose, challenge, expires_at) VALUES (gen_random_uuid(), 'registration', "
						+ "sha256('a'::bytea), now())"))
				.withMessageContaining("webauthn_challenges_owner");
		}
	}

	@Test
	void sessionsKnowThePasswordPasskeyMethod() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatNoException().isThrownBy(() -> statement.executeUpdate("""
					INSERT INTO sessions (token_hash, person_id, client_type, auth_method, second_factor_verified,
					                      authenticated_at, last_activity_at, absolute_expires_at)
					VALUES (sha256('s'::bytea), %d, 'web-portal', 'password-passkey', true, now(), now(),
					        now() + interval '1 hour')""".formatted(anna)));
		}
	}

}
