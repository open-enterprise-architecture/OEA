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

/**
 * Migration db/migration/V31__access_tokens.sql (s12-client-credentials-token D2): owner, grants,
 * the unique token hash and the lifetime of exactly 900 seconds.
 */
class AccessTokenSchemaTests {

	private static OeaDatabase postgres;

	private static UUID client;

	private static UUID credential;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			long person;
			try (ResultSet rs = statement.executeQuery(
					"INSERT INTO persons (person_type, name, status) VALUES ('service-account', 'schema-tokens', 'active') RETURNING id")) {
				rs.next();
				person = rs.getLong(1);
			}
			client = UUID.randomUUID();
			try (ResultSet rs = statement.executeQuery("""
					INSERT INTO machine_credentials (client_id, person_id, client_secret_hash, expires_at)
					VALUES ('%s', %d, 'hash', now() + interval '90 days') RETURNING id""".formatted(client, person))) {
				rs.next();
				credential = rs.getObject(1, UUID.class);
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
				ResultSet rs = statement.executeQuery(
						"SELECT tableowner FROM pg_tables WHERE schemaname = 'public' AND tablename = 'access_tokens'")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getString(1)).isEqualTo("oea_owner");
		}
	}

	@Test
	void aSecondRowWithTheSameHashIsRejected() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThat(insert(statement, "a1", 900)).isEqualTo(1);
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> insert(statement, "a1", 900))
				.withMessageContaining("access_tokens_token_hash_key");
		}
	}

	@Test
	void lifetimeIsExactly900Seconds() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThat(insert(statement, "b1", 900)).isEqualTo(1);
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> insert(statement, "b2", 901))
				.withMessageContaining("access_tokens_lifetime");
		}
	}

	@Test
	void runtimeRoleRevokesAndDeletes() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			insert(statement, "c1", 900);
			assertThat(statement.executeUpdate("UPDATE access_tokens SET revoked_at = now(), revocation_reason = 'rotation' "
					+ "WHERE token_hash = sha256('c1'::bytea)")).isEqualTo(1);
			assertThat(statement.executeUpdate("DELETE FROM access_tokens WHERE token_hash = sha256('c1'::bytea)")).isEqualTo(1);
		}
	}

	private static int insert(Statement statement, String token, int lifetimeSeconds) throws SQLException {
		return statement.executeUpdate("""
				INSERT INTO access_tokens (token_hash, client_id, credential_id, issued_at, expires_at)
				VALUES (sha256('%s'::bytea), '%s', '%s', TIMESTAMPTZ '2026-11-15 10:00Z',
				        TIMESTAMPTZ '2026-11-15 10:00Z' + interval '%d seconds')""".formatted(token, client, credential,
				lifetimeSeconds));
	}

}
