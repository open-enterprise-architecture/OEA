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
 * Migration db/migration/V51__diagram_nesting.sql (us-074-diagram-nesting, Data Model): the container of a
 * shape ({@code ON DELETE SET NULL}), the wider size limits, the hierarchy lines, the setting per viewpoint
 * and the global setting, on an empty database and on one that held shapes before.
 */
class DiagramNestingMigrationTests {

	private static final String INSTANCE_CONFIG = "00000000-0000-0000-0000-000000000001";

	private static OeaDatabase postgres;

	private static long person;

	private static String diagram;

	private static int entity;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		person = Long.parseLong(single(postgres, "INSERT INTO persons (name, email) VALUES ('Kurt', "
				+ "'kurt-nesting@example.org') RETURNING id"));
		String viewpoint = single(postgres, "INSERT INTO viewpoints (id, name, created_by) VALUES (gen_random_uuid(), "
				+ "'Nesting', " + person + ") RETURNING id::text");
		diagram = single(postgres, "INSERT INTO diagrams (name, viewpoint_id, created_by, updated_by) VALUES "
				+ "('Nesting', '" + viewpoint + "', " + person + ", " + person + ") RETURNING id::text");
		String type = single(postgres, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG
				+ "', 'Nesting type') RETURNING id::text");
		entity = Integer.parseInt(single(postgres, "INSERT INTO entities (metatype_id, name, created_by) VALUES ('"
				+ type + "', 'SAP ERP', " + person + ") RETURNING id"));
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	/** width, height, accepted: both sides of 1 and 10 000, two decimals */
	@ParameterizedTest
	@CsvSource({ "1, 1, true", "10000, 10000, true", "9999.99, 2000.01, true", "0.99, 30, false", "30, 0.99, false",
			"10000.01, 30, false", "30, 10000.01, false" })
	void sizesAreBetween1And10000(String width, String height, boolean accepted) throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			if (accepted) {
				UUID id = shape(statement, null, 0, width, height);
				assertThat(single(postgres, "SELECT width_pt FROM diagram_shapes WHERE id = '" + id + "'"))
					.isEqualTo(new java.math.BigDecimal(width).setScale(2).toPlainString());
			}
			else {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> shape(statement, null, 0, width, height))
					.withMessageContaining("diagram_shapes_");
			}
		}
	}

	@Test
	void aShapeLiesInAnotherShapeAndLosesTheContainerWhenItIsRemoved() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			UUID container = shape(statement, null, 0, "300", "200");
			UUID nested = shape(statement, container, 1, "90", "30");
			assertThat(single(postgres, "SELECT parent_shape_id::text FROM diagram_shapes WHERE id = '" + nested + "'"))
				.isEqualTo(container.toString());
			statement.executeUpdate("DELETE FROM diagram_shapes WHERE id = '" + container + "'");
			assertThat(single(postgres, "SELECT count(*) FROM diagram_shapes WHERE id = '" + nested + "'")).isEqualTo("1");
			assertThat(single(postgres, "SELECT parent_shape_id IS NULL FROM diagram_shapes WHERE id = '" + nested + "'"))
				.isEqualTo("t");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> shape(statement, UUID.randomUUID(), 2, "90", "30"))
				.withMessageContaining("parent_shape_id");
		}
	}

	@Test
	void hierarchyLinesJoinTwoDifferentShapesOncePerPairAndGoWithTheirShapes() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			UUID parent = shape(statement, null, 0, "90", "30");
			UUID child = shape(statement, null, 1, "90", "30");
			line(statement, parent, child);
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> line(statement, parent, child))
				.withMessageContaining("diagram_hierarchy_lines_parent_shape_id_child_shape_id_key");
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> line(statement, parent, parent))
				.withMessageContaining("diagram_hierarchy_lines_check");
			assertThat(statement.executeUpdate("UPDATE diagram_hierarchy_lines SET id = gen_random_uuid()")).isPositive();
			statement.executeUpdate("DELETE FROM diagram_shapes WHERE id = '" + child + "'");
			assertThat(single(postgres, "SELECT count(*) FROM diagram_hierarchy_lines WHERE parent_shape_id = '" + parent
					+ "'")).isEqualTo("0");
		}
	}

	@Test
	void theSettingPerViewpointIsTrueFalseOrNone() throws SQLException {
		assertThat(single(postgres, "SELECT count(*) FROM viewpoints WHERE ask_new_parent IS NOT NULL")).isEqualTo("0");
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThat(statement.executeUpdate("UPDATE viewpoints SET ask_new_parent = false")).isPositive();
			assertThat(statement.executeUpdate("UPDATE viewpoints SET ask_new_parent = true")).isPositive();
			assertThat(statement.executeUpdate("UPDATE viewpoints SET ask_new_parent = NULL")).isPositive();
		}
	}

	@Test
	void theGlobalSettingIsOneRowThatCanBeChangedButNotAddedOrDeleted() throws SQLException {
		assertThat(single(postgres, "SELECT ask_new_parent || '/' || version FROM diagram_settings WHERE id = 1"))
			.isEqualTo("true/1");
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThat(statement.executeUpdate("UPDATE diagram_settings SET ask_new_parent = true")).isOne();
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO diagram_settings (id) VALUES (2)"));
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("DELETE FROM diagram_settings"));
		}
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO diagram_settings (id) VALUES (2)"))
				.withMessageContaining("diagram_settings_id_check");
		}
	}

	@Test
	void theTablesAreOwnedByTheOwnerRole() throws SQLException {
		assertThat(single(postgres, "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename IN "
				+ "('diagram_hierarchy_lines', 'diagram_settings') AND tableowner = 'oea_owner'")).isEqualTo("2");
	}

	@Test
	void shapesAndViewpointsOfAnOlderDatabaseKeepTheirDataAndGetTheDefaults() throws Exception {
		OeaDatabase old = OeaDatabases.freshProvisioned();
		try {
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.target("50")
				.load()
				.migrate();
			long kurt = Long.parseLong(single(old, "INSERT INTO persons (name, email) VALUES ('Kurt', "
					+ "'kurt-before-nesting@example.org') RETURNING id"));
			String viewpoint = single(old, "INSERT INTO viewpoints (id, name, created_by) VALUES (gen_random_uuid(), "
					+ "'Old', " + kurt + ") RETURNING id::text");
			String old1 = single(old, "INSERT INTO diagrams (name, viewpoint_id, created_by, updated_by) VALUES "
					+ "('Old', '" + viewpoint + "', " + kurt + ", " + kurt + ") RETURNING id::text");
			String type = single(old, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG
					+ "', 'Application') RETURNING id::text");
			String entityId = single(old, "INSERT INTO entities (metatype_id, name, created_by) VALUES ('" + type
					+ "', 'SAP ERP', " + kurt + ") RETURNING id");
			String shape = single(old, "INSERT INTO diagram_shapes (id, diagram_id, entity_id, x_pt, y_pt, width_pt, "
					+ "height_pt, z_order) VALUES (gen_random_uuid(), '" + old1 + "', " + entityId
					+ ", 1.5, 2.5, 1999.99, 30, 0) RETURNING id::text");
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.load()
				.migrate();
			assertThat(single(old, "SELECT width_pt || '/' || x_pt || '/' || (parent_shape_id IS NULL) FROM diagram_shapes "
					+ "WHERE id = '" + shape + "'")).isEqualTo("1999.99/1.50/true");
			assertThat(single(old, "SELECT ask_new_parent IS NULL FROM viewpoints WHERE id = '" + viewpoint + "'"))
				.isEqualTo("t");
			assertThat(single(old, "SELECT ask_new_parent || '/' || version FROM diagram_settings")).isEqualTo("true/1");
			// a stored width of 10 000.00 is read back
			String wide = single(old, "UPDATE diagram_shapes SET width_pt = 10000 WHERE id = '" + shape
					+ "' RETURNING width_pt::text");
			assertThat(wide).isEqualTo("10000.00");
		}
		finally {
			old.stop();
		}
	}

	private static UUID shape(Statement statement, UUID parent, int z, String width, String height)
			throws SQLException {
		UUID id = UUID.randomUUID();
		statement.executeUpdate("""
				INSERT INTO diagram_shapes (id, diagram_id, entity_id, x_pt, y_pt, width_pt, height_pt, z_order, parent_shape_id)
				VALUES ('%s', '%s', %d, 0, 0, %s, %s, %d, %s)""".formatted(id, diagram, entity, width, height, z,
				(parent != null) ? "'" + parent + "'" : "NULL"));
		return id;
	}

	private static void line(Statement statement, UUID parent, UUID child) throws SQLException {
		statement.executeUpdate("""
				INSERT INTO diagram_hierarchy_lines (id, diagram_id, parent_shape_id, child_shape_id)
				VALUES (gen_random_uuid(), '%s', '%s', '%s')""".formatted(diagram, parent, child));
	}

	private static String single(OeaDatabase database, String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(database); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

}
