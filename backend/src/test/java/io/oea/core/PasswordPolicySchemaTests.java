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

/** Migration db/migration/V11__password_policy.sql (design D1 of us-027-password-administration). */
class PasswordPolicySchemaTests {

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
	void theDefaultsSwitchEveryRuleOff() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement();
				ResultSet rs = statement.executeQuery("""
						SELECT password_min_length, password_require_uppercase, password_require_lowercase,
						       password_require_digits, password_require_special_chars, password_expiry_days,
						       password_history_count, person_password_login
						FROM system_settings""")) {
			rs.next();
			assertThat(rs.getInt(1)).isEqualTo(12);
			assertThat(rs.getBoolean(2) || rs.getBoolean(3) || rs.getBoolean(4) || rs.getBoolean(5)).isFalse();
			assertThat(rs.getInt(6)).isZero();
			assertThat(rs.getInt(7)).isZero();
			assertThat(rs.getBoolean(8)).isTrue();
		}
	}

	@Test
	void theChecksRejectValuesOutsideTheirRanges() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("UPDATE system_settings SET password_min_length = 11"))
				.withMessageContaining("password_min_length");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("UPDATE system_settings SET password_expiry_days = 3651"))
				.withMessageContaining("password_expiry_days");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("UPDATE system_settings SET password_history_count = 25"))
				.withMessageContaining("password_history_count");
		}
	}

}
