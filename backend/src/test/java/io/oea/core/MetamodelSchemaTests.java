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

/** Migration db/migration/V5__metamodel.sql: owners, grants, scope constraints and the instance configuration. */
class MetamodelSchemaTests {

	private static final List<String> TABLES = List.of("metamodel_configurations", "metatypes",
			"metatype_connection_constraints", "property_categories", "property_definitions", "property_enum_values",
			"metatype_property_mappings", "metamodel_package_imports");

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
	void runtimeRoleCannotDeleteMetamodelContent() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			for (String table : List.of("metamodel_configurations", "metatypes", "property_categories",
					"property_definitions", "metamodel_package_imports")) {
				assertThatExceptionOfType(SQLException.class).as(table)
					.isThrownBy(() -> statement.executeUpdate("DELETE FROM " + table))
					.withMessageContaining("permission denied");
			}
			for (String table : List.of("metatype_connection_constraints", "property_enum_values",
					"metatype_property_mappings")) {
				assertThat(statement.executeUpdate("DELETE FROM " + table)).as(table).isZero();
			}
		}
	}

	@Test
	void instanceConfigurationExists() throws SQLException {
		assertThat(query("SELECT id::text || ':' || scope FROM metamodel_configurations"))
			.containsExactly("00000000-0000-0000-0000-000000000001:instance");
	}

	@Test
	void importedContentNeedsItsOrigin() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate("""
					INSERT INTO metatypes (config_id, name, scope)
					VALUES ('00000000-0000-0000-0000-000000000001', 'Without origin', 'imported')"""))
				.withMessageContaining("metatypes_origin");
			statement.executeUpdate("""
					INSERT INTO metatypes (config_id, name, scope, source_package_id, source_package_version)
					VALUES ('00000000-0000-0000-0000-000000000001', 'Imported', 'imported', 'pkg', '1.0.0')""");
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate("""
					INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Imported')"""))
				.withMessageContaining("metatypes_name_per_config");
		}
	}

	@Test
	void reservedColumnNamesAreRejected() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate(
					"INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('Name', 'name', 'string')"));
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
