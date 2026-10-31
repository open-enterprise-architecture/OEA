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

/** Migration db/migration/V15__metatype_editing.sql (design D3 of us-032-metatype-editing). */
class MetatypeEditingSchemaTests {

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
	void theGlobalSettingKeepsOrganizationMappings() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement();
				ResultSet rs = statement.executeQuery("SELECT reimport_organization_mappings FROM metamodel_configurations")) {
			rs.next();
			assertThat(rs.getString(1)).isEqualTo("keep");
		}
	}

	@Test
	void aNewMetatypeInheritsItsSettingAndCannotBeItsOwnBaseType() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			try (ResultSet rs = statement.executeQuery("""
					INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Zone')
					RETURNING reimport_organization_mappings, base_metatype_id""")) {
				rs.next();
				assertThat(rs.getString(1)).isEqualTo("inherit");
				assertThat(rs.getObject(2)).isNull();
			}
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("UPDATE metatypes SET base_metatype_id = id WHERE name = 'Zone'"))
				.withMessageContaining("metatypes_base_not_self");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement
					.executeUpdate("UPDATE metatypes SET reimport_organization_mappings = 'drop' WHERE name = 'Zone'"))
				.withMessageContaining("check");
		}
	}

	@Test
	void runtimeRoleMaintainsMarks() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThat(statement.executeUpdate("DELETE FROM entity_mandatory_marks WHERE false")).isZero();
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("UPDATE entity_mandatory_marks SET marked_at = now()"))
				.withMessageContaining("permission denied");
		}
	}

}
