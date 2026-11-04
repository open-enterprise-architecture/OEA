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

/** Migration db/migration/V16__mapping_effective_dates.sql (design D1 of us-137-mapping-effective-date). */
class MappingEffectiveDateSchemaTests {

	private static OeaDatabase postgres;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO metatypes (id, config_id, name)
					VALUES ('00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-000000000001', 'Zone')""");
			statement.executeUpdate("""
					INSERT INTO property_definitions (id, name, sql_column_name, data_type, scope, source_package_id,
					    source_package_version)
					VALUES ('00000000-0000-0000-0000-0000000000b1', 'Imported', 'imported', 'string', 'imported', 'p', '1'),
					       ('00000000-0000-0000-0000-0000000000b2', 'Own', 'own', 'string', 'organization', NULL, NULL)""");
			statement.executeUpdate("""
					INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
					VALUES ('00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000b1', 'optional',
					        DATE '2026-01-01', 'imported'),
					       ('00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000b2', 'optional',
					        DATE '2026-01-01', 'organization')""");
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void anOrganizationEntryMayOverrideAPackageMapping() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
					VALUES ('00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000b1', 'mandatory',
					        DATE '2026-07-01', 'organization')""");
			try (ResultSet rs = statement.executeQuery("""
					SELECT count(*) FROM metatype_property_mappings WHERE effective_applied_at IS NULL""")) {
				rs.next();
				assertThat(rs.getInt(1)).isEqualTo(3);
			}
		}
	}

	@Test
	void theScopeIsRequiredAndChecked() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate("""
					INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
					VALUES ('00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000b2', 'mandatory',
					        DATE '2026-07-01', 'package')""")).withMessageContaining("metatype_property_mappings_scope");
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate("""
					INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from)
					VALUES ('00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000b2', 'mandatory',
					        DATE '2026-08-01')""")).withMessageContaining("scope");
		}
	}

}
