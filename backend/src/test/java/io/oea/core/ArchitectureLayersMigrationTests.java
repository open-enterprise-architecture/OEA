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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Migration db/migration/V44__architecture_layers.sql (change s17-architecture-layers D1): the
 * conversion of {@code metatypes.architecture_layer_id} to a reference (a free-text value is
 * cleared), the name unique regardless of case, the checks of name, order and origin, the foreign
 * key from metatypes and the new label type.
 */
class ArchitectureLayersMigrationTests {

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
	void theColumnBecomesAReferenceAndAFreeTextValueIsCleared() throws Exception {
		OeaDatabase old = OeaDatabases.freshProvisioned();
		try {
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.target("43")
				.load()
				.migrate();
			assertThat(single(old, """
					SELECT data_type FROM information_schema.columns
					WHERE table_name = 'metatypes' AND column_name = 'architecture_layer_id'""")).isEqualTo("text");
			String typed = single(old, "INSERT INTO metatypes (config_id, name, architecture_layer_id) VALUES ('"
					+ INSTANCE_CONFIG + "', 'Typed', 'application-layer') RETURNING id::text");
			String untyped = single(old, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG
					+ "', 'Untyped') RETURNING id::text");
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.load()
				.migrate();
			assertThat(single(old, """
					SELECT data_type FROM information_schema.columns
					WHERE table_name = 'metatypes' AND column_name = 'architecture_layer_id'""")).isEqualTo("uuid");
			assertThat(single(old, "SELECT architecture_layer_id IS NULL FROM metatypes WHERE id = '" + typed + "'"))
				.isEqualTo("t");
			assertThat(single(old, "SELECT name FROM metatypes WHERE id = '" + typed + "'")).isEqualTo("Typed");
			assertThat(single(old, "SELECT architecture_layer_id IS NULL FROM metatypes WHERE id = '" + untyped + "'"))
				.isEqualTo("t");
			assertThat(single(old, "SELECT count(*) FROM architecture_layers")).isEqualTo("0");
		}
		finally {
			old.stop();
		}
	}

	@Test
	void layerNamesAreUniqueRegardlessOfCaseAndBlanks() throws SQLException {
		String name = "Unique " + UUID.randomUUID();
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			layer(statement, name, 0);
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> layer(statement, name.toUpperCase(), 1))
				.withMessageContaining("architecture_layers_name_unique");
			// another name is free
			layer(statement, name + " 2", 1);
		}
	}

	@ParameterizedTest
	@CsvSource({ "1, true", "100, true", "101, false", "0, false" })
	void theNameHasOneToHundredCharacters(int length, boolean accepted) throws SQLException {
		String name = "n".repeat(length);
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			if (accepted) {
				layer(statement, name, 0);
			}
			else {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> layer(statement, name, 0))
					.withMessageContaining("architecture_layers_name");
			}
		}
	}

	@Test
	void aNameWithSurroundingBlanksIsRejected() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> layer(statement, " Padded ", 0))
				.withMessageContaining("architecture_layers_name");
		}
	}

	@ParameterizedTest
	@CsvSource({ "0, true", "19, true", "-1, false" })
	void theOrderIsNotNegative(int sortOrder, boolean accepted) throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String name = "Order " + UUID.randomUUID();
			if (accepted) {
				layer(statement, name, sortOrder);
			}
			else {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> layer(statement, name, sortOrder))
					.withMessageContaining("architecture_layers_sort_order");
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "organization, , , true", "imported, starter, 1.0.0, true", "imported, , , false",
			"organization, starter, 1.0.0, false", "built-in, , , false", "imported, starter, , false" })
	void importedExactlyWithAPackage(String scope, String packageId, String packageVersion, boolean accepted)
			throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String sql = "INSERT INTO architecture_layers (name, sort_order, scope, source_package_id, "
					+ "source_package_version) VALUES ('Origin " + UUID.randomUUID() + "', 0, '" + scope + "', "
					+ ((packageId != null) ? "'" + packageId + "'" : "NULL") + ", "
					+ ((packageVersion != null) ? "'" + packageVersion + "'" : "NULL") + ")";
			if (accepted) {
				statement.executeUpdate(sql);
			}
			else {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate(sql))
					.withMessageContaining("architecture_layers_");
			}
		}
	}

	@Test
	void layersBelongToTheInstanceConfiguration() throws SQLException {
		String person = single(postgres, "INSERT INTO persons (name, email) VALUES ('Layer Kurt', 'layer-kurt@example.org') "
				+ "RETURNING id::text");
		String solution = single(postgres, "INSERT INTO solutions (id, name, owner_id, created_by) VALUES "
				+ "(gen_random_uuid(), 'Layer solution', " + person + ", " + person + ") RETURNING id::text");
		String extension = single(postgres, "INSERT INTO metamodel_configurations (scope, parent_id, solution_id, name) "
				+ "VALUES ('solution', '" + INSTANCE_CONFIG + "', '" + solution + "', 'Extension for layers') "
				+ "RETURNING id::text");
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate(
					"INSERT INTO architecture_layers (config_id, name, sort_order) VALUES ('" + extension
							+ "', 'Of an extension', 0)"))
				.withMessageContaining("architecture_layers_instance");
		}
	}

	@Test
	void theReferenceFromMetatypesIsChecked() throws SQLException {
		String layer = single(postgres, "INSERT INTO architecture_layers (name, sort_order) VALUES ('Referenced "
				+ UUID.randomUUID() + "', 0) RETURNING id::text");
		String type = single(postgres, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG
				+ "', 'Layered " + UUID.randomUUID() + "') RETURNING id::text");
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			// a layer that does not exist is rejected, an existing one accepted, and clearing is possible
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement
				.executeUpdate("UPDATE metatypes SET architecture_layer_id = '" + UUID.randomUUID() + "' WHERE id = '"
						+ type + "'"))
				.withMessageContaining("metatypes_architecture_layer");
			assertThat(statement.executeUpdate(
					"UPDATE metatypes SET architecture_layer_id = '" + layer + "' WHERE id = '" + type + "'")).isOne();
			// a layer in use cannot be deleted
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("DELETE FROM architecture_layers WHERE id = '" + layer + "'"))
				.withMessageContaining("metatypes_architecture_layer");
			assertThat(statement
				.executeUpdate("UPDATE metatypes SET architecture_layer_id = NULL WHERE id = '" + type + "'")).isOne();
			assertThat(statement.executeUpdate("DELETE FROM architecture_layers WHERE id = '" + layer + "'")).isOne();
		}
	}

	@Test
	void labelsOfLayersAreAcceptedAndUnknownTypesAreNot() throws SQLException {
		String layer = single(postgres, "INSERT INTO architecture_layers (name, sort_order) VALUES ('Labelled "
				+ UUID.randomUUID() + "', 0) RETURNING id::text");
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThat(statement.executeUpdate("INSERT INTO i18n_entries (entity_type, entity_id, locale, field, value, "
					+ "scope) VALUES ('architecture_layer', '" + layer + "', 'de', 'name', 'Geschäft', 'organization')"))
				.isOne();
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO i18n_entries (entity_type, entity_id, locale, "
						+ "field, value, scope) VALUES ('architecture_layers', '" + layer
						+ "', 'de', 'name', 'x', 'organization')"))
				.withMessageContaining("i18n_entries_entity_type_check");
			// the earlier types still work
			assertThat(statement.executeUpdate("INSERT INTO i18n_entries (entity_type, entity_id, locale, field, value, "
					+ "scope) VALUES ('metatype', gen_random_uuid(), 'de', 'name', 'Typ', 'organization')")).isOne();
		}
	}

	private static void layer(Statement statement, String name, int sortOrder) throws SQLException {
		statement.executeUpdate("INSERT INTO architecture_layers (name, sort_order) VALUES ('" + name + "', " + sortOrder
				+ ")");
	}

	private static String single(OeaDatabase database, String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(database); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

}
