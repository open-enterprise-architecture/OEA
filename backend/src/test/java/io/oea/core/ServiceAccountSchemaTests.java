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
 * Migration db/migration/V30__service_accounts.sql (us-142-service-accounts D1, D5): owner and
 * grants of machine_credentials, one active secret per client ID, secrets of service accounts
 * only, the expiry check, the description length and the unique names of service accounts.
 */
class ServiceAccountSchemaTests {

	private static OeaDatabase postgres;

	@BeforeAll
	static void start() {
		postgres = OeaDatabases.freshMigrated();
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void tableIsOwnedByTheOwnerRole() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(
						"SELECT tableowner FROM pg_tables WHERE schemaname = 'public' AND tablename = 'machine_credentials'")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getString(1)).isEqualTo("oea_owner");
		}
	}

	@Test
	void runtimeRoleCannotDeleteSecrets() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("DELETE FROM machine_credentials"))
				.withMessageContaining("permission denied");
		}
	}

	@Test
	void oneActiveSecretPerClientId() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			long person = serviceAccount(statement, "schema-one-active");
			UUID client = UUID.randomUUID();
			insertSecret(statement, person, client, "active");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insertSecret(statement, person, client, "active"))
				.withMessageContaining("machine_credentials_one_active");
			assertThat(insertSecret(statement, person, client, "rotated")).isEqualTo(1);
		}
	}

	@Test
	void secretsBelongToServiceAccountsOnly() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			long human = person(statement, "human", "Schema Human", "active");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insertSecret(statement, human, UUID.randomUUID(), "active"))
				.withMessageContaining("service accounts only");
		}
	}

	@Test
	void clientIdBelongsToOneServiceAccount() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			UUID client = UUID.randomUUID();
			insertSecret(statement, serviceAccount(statement, "schema-first"), client, "active");
			long second = serviceAccount(statement, "schema-second");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insertSecret(statement, second, client, "rotated"))
				.withMessageContaining("one service account");
		}
	}

	@Test
	void expiryMustFollowTheCreation() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			long person = serviceAccount(statement, "schema-expiry");
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate("""
					INSERT INTO machine_credentials (client_id, person_id, client_secret_hash, created_at, expires_at)
					VALUES ('%s', %d, 'hash', TIMESTAMPTZ '2026-11-14 10:00Z', TIMESTAMPTZ '2026-11-14 10:00Z')"""
				.formatted(UUID.randomUUID(), person))).withMessageContaining("machine_credentials_expiry");
			assertThat(statement.executeUpdate("""
					INSERT INTO machine_credentials (client_id, person_id, client_secret_hash, created_at, expires_at)
					VALUES ('%s', %d, 'hash', TIMESTAMPTZ '2026-11-14 10:00Z', TIMESTAMPTZ '2026-11-14 10:00:01Z')"""
				.formatted(UUID.randomUUID(), person))).isEqualTo(1);
		}
	}

	@Test
	void descriptionLengthBoundaries() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			long person = serviceAccount(statement, "schema-description");
			assertThat(statement.executeUpdate(
					"UPDATE persons SET description = repeat('d', 1000) WHERE id = " + person)).isEqualTo(1);
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement
					.executeUpdate("UPDATE persons SET description = repeat('d', 1001) WHERE id = " + person))
				.withMessageContaining("persons_description_check");
		}
	}

	@Test
	void serviceAccountNamesAreUniqueIgnoringCase() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			serviceAccount(statement, "ci-scanner");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> serviceAccount(statement, "CI-Scanner"))
				.withMessageContaining("persons_service_account_name_unique");
			person(statement, "human", "Same Name", "active");
			assertThat(person(statement, "human", "same name", "active")).isPositive();
		}
	}

	private static long serviceAccount(Statement statement, String name) throws SQLException {
		return person(statement, "service-account", name, "active");
	}

	private static long person(Statement statement, String type, String name, String status) throws SQLException {
		try (ResultSet rs = statement.executeQuery("INSERT INTO persons (person_type, name, status) VALUES ('" + type
				+ "', '" + name + "', '" + status + "') RETURNING id")) {
			rs.next();
			return rs.getLong(1);
		}
	}

	private static int insertSecret(Statement statement, long person, UUID client, String status) throws SQLException {
		String revoked = "active".equals(status) ? "NULL" : "now()";
		return statement.executeUpdate("""
				INSERT INTO machine_credentials (client_id, person_id, client_secret_hash, status, expires_at, revoked_at)
				VALUES ('%s', %d, 'hash', '%s', now() + interval '90 days', %s)""".formatted(client, person, status,
				revoked));
	}

}
