package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.contract.Contract;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Migration db/migration/V3__identity_and_sessions.sql: owners, grants and the seeded catalog. */
class IdentitySchemaTests {

	private static final List<String> TABLES = List.of("persons", "auth_providers", "person_identities", "permissions",
			"authorization_roles", "role_permissions", "idp_group_mappings", "sessions", "totp_credentials",
			"login_transactions", "auth_throttle");

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
	void tablesAreOwnedByTheOwnerRole() throws SQLException {
		assertThat(query("SELECT tablename || ':' || tableowner FROM pg_tables WHERE schemaname = 'public' "
				+ "AND tablename IN ('" + String.join("','", TABLES) + "')"))
			.hasSize(TABLES.size())
			.allMatch((row) -> row.endsWith(":oea_owner"));
	}

	@Test
	void runtimeRoleCannotDeleteDurableIdentityData() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			for (String table : List.of("persons", "auth_providers", "authorization_roles", "role_permissions",
					"idp_group_mappings", "sessions", "totp_credentials", "permissions")) {
				assertThatExceptionOfType(SQLException.class).as(table)
					.isThrownBy(() -> statement.executeUpdate("DELETE FROM " + table))
					.withMessageContaining("permission denied");
			}
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO permissions (id, category, admin_class) VALUES ('x:y', 'x', false)"))
				.withMessageContaining("permission denied");
			for (String table : List.of("person_identities", "login_transactions", "auth_throttle")) {
				assertThat(statement.executeUpdate("DELETE FROM " + table)).as(table).isZero();
			}
		}
	}

	@Test
	void seededCatalogEqualsTheContract() throws SQLException {
		JsonNode contract = Contract.root();
		Map<String, Boolean> contractPermissions = new TreeMap<>();
		contract.path("x-oea-permissions")
			.forEach((p) -> contractPermissions.put(p.path("id").asString(), p.path("adminClass").asBoolean()));
		Map<String, Boolean> dbPermissions = new TreeMap<>();
		query("SELECT id || '=' || admin_class FROM permissions")
			.forEach((row) -> dbPermissions.put(row.split("=")[0], Boolean.parseBoolean(row.split("=")[1])));
		assertThat(dbPermissions).hasSize(20).isEqualTo(contractPermissions);

		Set<String> contractRoles = new TreeSet<>();
		contract.path("x-oea-builtin-roles").forEach((role) -> {
			contractRoles.add(role.path("id").asString() + "|service=" + role.path("assignableToServiceAccounts").asBoolean());
			role.path("permissions").forEach((p) -> contractRoles.add(role.path("id").asString() + ">" + p.asString()));
		});
		Set<String> dbRoles = new TreeSet<>(query(
				"SELECT id || '|service=' || assignable_to_service_accounts FROM authorization_roles WHERE is_builtin"));
		dbRoles.addAll(query("SELECT role_id || '>' || permission_id FROM role_permissions"));
		assertThat(dbRoles).isEqualTo(contractRoles);
		assertThat(query("SELECT count(*)::text FROM authorization_roles")).containsExactly("9");
	}

	@Test
	void endOfTheSetupPhaseIsIrreversible() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate("INSERT INTO system_settings (id, instance_slug) VALUES (1, 'acme-corp')");
			statement.executeUpdate("UPDATE system_settings SET setup_phase_ended_at = now(), "
					+ "setup_phase_ended_by_action = 'idp-group-mapping'");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(
						"UPDATE system_settings SET setup_phase_ended_at = NULL, setup_phase_ended_by_action = NULL"))
				.withMessageContaining("irreversible");
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
