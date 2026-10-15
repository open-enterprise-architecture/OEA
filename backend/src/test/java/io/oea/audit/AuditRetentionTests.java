package io.oea.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaTestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/** Spec audit/retention: default retention and floor (REQ-083, ADR-033 §4). */
class AuditRetentionTests {

	@Test
	void freshlyMigratedInstanceHasTheDefaultRetentionAsFirstChainLink() throws SQLException {
		try (Connection auditor = OeaDatabases.asRole(OeaTestDatabase.migrated(), "oea_auditor");
				Statement statement = auditor.createStatement();
				ResultSet rs = statement.executeQuery("""
						SELECT retention_days, chain_seq, effective_from <= now(), recorded_by_person_id IS NULL
						       AND recorded_by_admin_id IS NULL
						FROM audit.retention_settings ORDER BY chain_seq LIMIT 1""")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getInt(1)).isEqualTo(2555);
			assertThat(rs.getLong(2)).isEqualTo(1);
			assertThat(rs.getBoolean(3)).as("in force").isTrue();
			assertThat(rs.getBoolean(4)).as("seeded by the migration").isTrue();
		}
	}

	@Test
	void retentionBelowTheFloorIsRejectedByTheDatabase() throws SQLException {
		try (Connection app = OeaDatabases.asRole(OeaTestDatabase.migrated(), "oea_app");
				Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(
						"INSERT INTO audit.retention_settings (retention_days, effective_from) VALUES (364, now())"))
				.withMessageContaining("retention_settings_retention_days_check");
			try (ResultSet rs = statement.executeQuery("SELECT count(*), min(retention_days) FROM audit.retention_settings")) {
				rs.next();
				assertThat(rs.getInt(1)).isEqualTo(1);
				assertThat(rs.getInt(2)).isEqualTo(2555);
			}
		}
	}

}
