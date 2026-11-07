package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.testsupport.containers.OeaDatabase;
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

/** Migration db/migration/V23__property_visibility_roles.sql: owner, grants, key and checks. */
class PropertyVisibilitySchemaTests {

	private static OeaDatabase postgres;

	private static String propertyId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("""
						INSERT INTO property_definitions (name, sql_column_name, data_type)
						VALUES ('Investment Cost', 'investment_cost', 'integer') RETURNING id::text""")) {
			rs.next();
			propertyId = rs.getString(1);
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void ownerAndGrants() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			try (ResultSet rs = statement.executeQuery(
					"SELECT tableowner FROM pg_tables WHERE schemaname = 'public' AND tablename = 'property_visibility_roles'")) {
				assertThat(rs.next()).isTrue();
				assertThat(rs.getString(1)).isEqualTo("oea_owner");
			}
			List<String> privileges = new ArrayList<>();
			try (ResultSet rs = statement.executeQuery("""
					SELECT privilege_type FROM information_schema.role_table_grants
					WHERE table_name = 'property_visibility_roles' AND grantee = 'oea_app' ORDER BY privilege_type""")) {
				while (rs.next()) {
					privileges.add(rs.getString(1));
				}
			}
			assertThat(privileges).containsExactly("DELETE", "INSERT", "SELECT");
		}
	}

	@Test
	void keyAndForeignKeys() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.execute(insert(propertyId, "auditor", "read"));
			statement.execute(insert(propertyId, "auditor", "write"));
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.execute(insert(propertyId, "auditor", "read")))
				.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23505"));
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.execute(insert(propertyId, "no-such-role", "read")))
				.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23503"));
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.execute(insert("00000000-0000-0000-0000-000000000099", "auditor", "read")))
				.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23503"));
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.execute(insert(propertyId, "viewer", "admin")))
				.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23514"));
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.execute(
						"UPDATE property_visibility_roles SET access = 'read' WHERE role_id = 'auditor' AND access = 'write'"))
				.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("42501"));
			statement.execute("DELETE FROM property_visibility_roles WHERE property_def_id = '" + propertyId + "'");
		}
	}

	private static String insert(String property, String role, String access) {
		return "INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES ('%s', '%s', '%s')"
			.formatted(property, role, access);
	}

}
