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

/** Migration db/migration/V9__role_assignments.sql (design D1 of us-144-local-role-assignments). */
class RoleAssignmentSchemaTests {

	private static OeaDatabase postgres;

	private static long anna;

	private static long max;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			anna = insertPerson(statement, "Anna");
			max = insertPerson(statement, "Max");
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
						"SELECT tableowner FROM pg_tables WHERE schemaname = 'public' AND tablename = 'role_assignments'")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getString(1)).isEqualTo("oea_owner");
		}
	}

	@Test
	void constraintsRejectInconsistentAssignments() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String columns = "INSERT INTO role_assignments (person_id, role_id, granted_by_person_id, granted_by_admin_id, "
					+ "valid_from, valid_to, status, revoked_at, revoked_by_person_id) VALUES ";
			// a valid assignment for reference
			statement.executeUpdate(columns + "(" + anna + ", 'viewer', " + max + ", NULL, now(), NULL, 'granted', NULL, NULL)");
			for (String values : new String[] {
					// two granters
					"(" + anna + ", 'viewer', " + max + ", gen_random_uuid(), now(), NULL, 'granted', NULL, NULL)",
					// no granter
					"(" + anna + ", 'viewer', NULL, NULL, now(), NULL, 'granted', NULL, NULL)",
					// validTo not after validFrom
					"(" + anna + ", 'viewer', " + max + ", NULL, now(), now(), 'granted', NULL, NULL)",
					// granted to oneself (SoD-1)
					"(" + anna + ", 'viewer', " + anna + ", NULL, now(), NULL, 'granted', NULL, NULL)",
					// revoked without revocation time
					"(" + anna + ", 'viewer', " + max + ", NULL, now(), NULL, 'revoked', NULL, NULL)",
					// revocation time while granted
					"(" + anna + ", 'viewer', " + max + ", NULL, now(), NULL, 'granted', now(), " + max + ")" }) {
				assertThatExceptionOfType(SQLException.class).as(values)
					.isThrownBy(() -> statement.executeUpdate(columns + values))
					.withMessageContaining("violates check constraint");
			}
		}
	}

	@Test
	void runtimeRoleCannotDeleteAssignments() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("DELETE FROM role_assignments"))
				.withMessageContaining("permission denied");
		}
	}

	private static long insertPerson(Statement statement, String name) throws SQLException {
		try (ResultSet rs = statement
			.executeQuery("INSERT INTO persons (name, status) VALUES ('" + name + "', 'active') RETURNING id")) {
			rs.next();
			return rs.getLong(1);
		}
	}

}
