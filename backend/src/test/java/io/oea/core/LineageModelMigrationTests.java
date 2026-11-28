package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.config.migration.MigrationSet;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Migration db/migration/V54__lineage_model.sql (change us-065-lineage-model-and-query D3): the one-row setting starts
 * unset on a database migrated from V53, it stays one row, the runtime role only updates it, and a deleted metatype
 * unsets its part.
 */
class LineageModelMigrationTests {

	private static final String INSTANCE_CONFIG = "00000000-0000-0000-0000-000000000001";

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
	void theSettingStartsUnsetAfterTheMigrationOfAnExistingDatabase() throws Exception {
		OeaDatabase old = OeaDatabases.freshProvisioned();
		try {
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.target("53")
				.load()
				.migrate();
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.load()
				.migrate();
			assertThat(single(old, "SELECT count(*) FROM lineage_model WHERE data_object_metatype_id IS NULL "
					+ "AND data_flow_metatype_id IS NULL AND carries_data_metatype_id IS NULL "
					+ "AND data_input_metatype_id IS NULL AND data_output_metatype_id IS NULL AND version = 1"))
				.isEqualTo("1");
		}
		finally {
			old.stop();
		}
	}

	@Test
	void theSettingIsOneRowAndTheRuntimeRoleOnlyUpdatesIt() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO lineage_model (id) VALUES (2)"))
				.withMessageContaining("permission denied");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("DELETE FROM lineage_model"))
				.withMessageContaining("permission denied");
			assertThat(statement.executeUpdate("UPDATE lineage_model SET version = version")).isOne();
		}
		try (Connection owner = OeaDatabases.asSuperuser(postgres); Statement statement = owner.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO lineage_model (id) VALUES (2)"))
				.withMessageContaining("lineage_model_id_check");
		}
	}

	@Test
	void aMetatypeThatIsDeletedUnsetsItsPart() throws SQLException {
		String type = single(postgres, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG + "', 'Short "
				+ UUID.randomUUID() + "') RETURNING id::text");
		try (Connection owner = OeaDatabases.asSuperuser(postgres); Statement statement = owner.createStatement()) {
			statement.executeUpdate("UPDATE lineage_model SET data_object_metatype_id = '" + type + "'");
			assertThat(single(postgres, "SELECT data_object_metatype_id IS NOT NULL FROM lineage_model")).isEqualTo("t");
			statement.executeUpdate("DELETE FROM metatypes WHERE id = '" + type + "'");
			assertThat(single(postgres, "SELECT data_object_metatype_id IS NULL FROM lineage_model")).isEqualTo("t");
		}
	}

	private static String single(OeaDatabase database, String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(database); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

}
