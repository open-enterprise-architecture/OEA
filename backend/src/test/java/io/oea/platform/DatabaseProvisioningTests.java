package io.oea.platform;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.containers.OeaPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Spec platform/database-roles: Roles created by provisioning, not by migrations. */
class DatabaseProvisioningTests {

	private static final List<String> OEA_ROLES = List.of("oea_owner", "oea_reporting_owner", "oea_app",
			"oea_audit_cleanup", "oea_auditor", "oea_analytics");

	private static final PostgreSQLContainer postgres = OeaPostgres.provisioned();

	@BeforeAll
	static void start() {
		postgres.start();
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void rolesExistWithoutElevatedAttributes() throws SQLException {
		List<String> rows = query("""
				SELECT rolname || ' super=' || rolsuper || ' createrole=' || rolcreaterole
				       || ' createdb=' || rolcreatedb || ' bypassrls=' || rolbypassrls || ' login=' || rolcanlogin
				FROM pg_roles WHERE rolname LIKE 'oea\\_%' ORDER BY rolname""");
		assertThat(rows).hasSize(OEA_ROLES.size())
			.allSatisfy((row) -> assertThat(row).endsWith("super=false createrole=false createdb=false bypassrls=false login=true"));
		assertThat(rows).extracting((row) -> row.substring(0, row.indexOf(' ')))
			.containsExactlyInAnyOrderElementsOf(OEA_ROLES);
	}

	@Test
	void rolesAreNotMembersOfEachOther() throws SQLException {
		List<String> memberships = query("""
				SELECT r.rolname || ' in ' || g.rolname FROM pg_auth_members m
				JOIN pg_roles r ON r.oid = m.member JOIN pg_roles g ON g.oid = m.roleid
				WHERE r.rolname LIKE 'oea\\_%' OR g.rolname LIKE 'oea\\_%'""");
		assertThat(memberships).isEmpty();
	}

	@Test
	void schemasHaveTheirOwners() throws SQLException {
		Map<String, String> owners = new TreeMap<>();
		for (String row : query("""
				SELECT nspname || '=' || pg_get_userbyid(nspowner) FROM pg_namespace
				WHERE nspname IN ('public', 'audit', 'reporting', 'reporting_ctx')""")) {
			owners.put(row.substring(0, row.indexOf('=')), row.substring(row.indexOf('=') + 1));
		}
		assertThat(owners).containsExactlyInAnyOrderEntriesOf(Map.of("public", "oea_owner", "audit", "oea_owner",
				"reporting", "oea_reporting_owner", "reporting_ctx", "oea_reporting_owner"));
	}

	@Test
	void everyRoleCanLogInWithItsProvisionedPassword() throws SQLException {
		for (String role : OEA_ROLES) {
			try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), role,
					OeaPostgres.ROLE_PASSWORDS.get(role))) {
				assertThat(connection.isValid(2)).as(role).isTrue();
			}
		}
	}

	private static List<String> query(String sql) throws SQLException {
		List<String> rows = new ArrayList<>();
		try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()); Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				rows.add(resultSet.getString(1));
			}
		}
		return rows;
	}

}
