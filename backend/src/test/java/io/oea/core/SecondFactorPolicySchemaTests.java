package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Migration db/migration/V14__second_factor_policy.sql (design D1 of us-031-second-factor-enforcement). */
class SecondFactorPolicySchemaTests {

	private static OeaDatabase postgres;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate("INSERT INTO system_settings (id, instance_slug) VALUES (1, 'test-instance')");
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void theEnforcementIsOffWithoutExemptRoles() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement();
				ResultSet rs = statement.executeQuery(
						"SELECT second_factor_enforced, cardinality(second_factor_exempt_roles) FROM system_settings")) {
			rs.next();
			assertThat(rs.getBoolean(1)).isFalse();
			assertThat(rs.getInt(2)).isZero();
		}
	}

}
