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

/** Migration db/migration/V4__oidc_login.sql: columns, constraints, owners and grants. */
class OidcLoginSchemaTests {

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
	void newTablesAreOwnedByTheOwnerRoleAndWritableByTheRuntime() throws SQLException {
		assertThat(query("SELECT tablename || ':' || tableowner FROM pg_tables WHERE schemaname = 'public' "
				+ "AND tablename IN ('session_idp_roles', 'oidc_login_states') ORDER BY 1"))
			.containsExactly("oidc_login_states:oea_owner", "session_idp_roles:oea_owner");
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			for (String table : List.of("session_idp_roles", "oidc_login_states")) {
				assertThat(statement.executeUpdate("DELETE FROM " + table)).as(table).isZero();
			}
		}
	}

	@Test
	void sessionColumnsForIdpSessions() throws SQLException {
		assertThat(query("""
				SELECT column_name || ':' || data_type FROM information_schema.columns
				WHERE table_name = 'sessions' AND column_name IN ('idp_group_identifiers', 'idp_token_expires_at')
				ORDER BY 1"""))
			.containsExactly("idp_group_identifiers:ARRAY", "idp_token_expires_at:timestamp with time zone");
	}

	@Test
	void idpFieldsOnlyForOidcSessions() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO system_admin_accounts (id, login_name, status)
					VALUES ('00000000-0000-0000-0000-000000000001', 'admin', 'active')""");
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate("""
					INSERT INTO sessions (token_hash, system_admin_account_id, client_type, auth_method,
					    authenticated_at, last_activity_at, absolute_expires_at, idp_group_identifiers)
					VALUES (decode(repeat('ab', 32), 'hex'), '00000000-0000-0000-0000-000000000001', 'web-portal',
					    'password', now(), now(), now() + interval '1 hour', ARRAY['g'])"""))
				.withMessageContaining("sessions_idp_fields_oidc_only");
		}
	}

	@Test
	void returnPathMustBeRelative() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO auth_providers (id, name, issuer, client_id)
					VALUES ('00000000-0000-0000-0000-0000000000aa', 'IdP', 'https://idp.example.org', 'oea')""");
			for (String target : List.of("https://evil.example", "//evil.example")) {
				assertThatExceptionOfType(SQLException.class).as(target).isThrownBy(() -> statement.executeUpdate("""
						INSERT INTO oidc_login_states (state_hash, browser_binding_hash, auth_provider_id, nonce,
						    code_verifier_encrypted, code_verifier_key_id, return_to, expires_at)
						VALUES (decode(repeat('01', 32), 'hex'), decode(repeat('02', 32), 'hex'),
						    '00000000-0000-0000-0000-0000000000aa', 'n', 'v', 'k', '%s', now())""".formatted(target)));
			}
		}
	}

	private static List<String> query(String sql) throws SQLException {
		List<String> rows = new ArrayList<>();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			while (rs.next()) {
				rows.add(rs.getString(1));
			}
		}
		return rows;
	}

}
