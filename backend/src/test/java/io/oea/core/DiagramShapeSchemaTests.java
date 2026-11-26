package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
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
 * Migration db/migration/V47__diagram_shapes.sql (US-074; design D1 of us-074-diagram-canvas): the
 * owner and the grants, the limits of position, size and drawing order with both sides of each
 * boundary, the foreign keys and the removal of the shapes with their diagram.
 */
class DiagramShapeSchemaTests {

	private static OeaDatabase postgres;

	private static long kurt;

	private static UUID diagram;

	private static int entity;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			kurt = number(statement, "INSERT INTO persons (name, email) VALUES ('Kurt', 'kurt@example.org') RETURNING id");
			UUID viewpoint = UUID.fromString(text(statement, """
					INSERT INTO viewpoints (id, name, created_by) VALUES (gen_random_uuid(), 'Shape Viewpoint', %d)
					RETURNING id::text""".formatted(kurt)));
			diagram = UUID.fromString(text(statement, """
					INSERT INTO diagrams (name, viewpoint_id, created_by, updated_by) VALUES ('Shapes', '%s', %d, %d)
					RETURNING id::text""".formatted(viewpoint, kurt, kurt)));
			UUID metatype = UUID.fromString(text(statement, """
					INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Shape Type')
					RETURNING id::text"""));
			entity = (int) number(statement, "INSERT INTO entities (metatype_id, name, created_by) VALUES ('%s', 'SAP ERP', %d) RETURNING id"
				.formatted(metatype, kurt));
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	private static long number(Statement statement, String sql) throws SQLException {
		try (ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}

	private static String text(Statement statement, String sql) throws SQLException {
		try (ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

	private static void shape(Statement statement, String x, String y, String width, String height, String z)
			throws SQLException {
		shape(statement, diagram.toString(), entity, x, y, width, height, z);
	}

	private static void shape(Statement statement, String diagramId, int entityId, String x, String y, String width,
			String height, String z) throws SQLException {
		statement.executeUpdate("""
				INSERT INTO diagram_shapes (id, diagram_id, entity_id, x_pt, y_pt, width_pt, height_pt, z_order)
				VALUES (gen_random_uuid(), '%s', %d, %s, %s, %s, %s, %s)""".formatted(diagramId, entityId, x, y, width,
				height, z));
	}

	@Test
	void tableIsOwnedByTheOwnerRoleAndTheRuntimeRoleMayWriteAndDelete() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			assertThat(number(statement, """
					SELECT count(*) FROM pg_tables WHERE schemaname = 'public'
					AND tablename = 'diagram_shapes' AND tableowner = 'oea_owner'""")).isEqualTo(1);
		}
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			shape(statement, "1", "2", "90", "30", "0");
			assertThat(statement.executeUpdate("UPDATE diagram_shapes SET x_pt = 3")).isPositive();
			assertThat(statement.executeUpdate("DELETE FROM diagram_shapes")).isPositive();
		}
	}

	/** x, y, width, height, drawing order, accepted */
	@ParameterizedTest
	@CsvSource({ "-100000, 100000, 1, 10000, 0, true", "100000, -100000, 10000, 1, 7, true",
			"-100000.01, 0, 90, 30, 0, false", "100000.01, 0, 90, 30, 0, false", "0, -100000.01, 90, 30, 0, false",
			"0, 100000.01, 90, 30, 0, false", "0, 0, 0.99, 30, 0, false", "0, 0, 10000.01, 30, 0, false",
			"0, 0, 90, 0.99, 0, false", "0, 0, 90, 10000.01, 0, false", "0, 0, 90, 30, -1, false" })
	void limitsOfPositionSizeAndOrder(String x, String y, String width, String height, String z, boolean accepted)
			throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			if (accepted) {
				shape(statement, x, y, width, height, z);
			}
			else {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> shape(statement, x, y, width, height, z))
					.withMessageContaining("diagram_shapes");
			}
		}
	}

	@Test
	void theDiagramAndTheEntityMustExist() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> shape(statement, UUID.randomUUID().toString(), entity, "0", "0", "90", "30", "0"))
				.withMessageContaining("diagram_shapes_diagram_id_fkey");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> shape(statement, diagram.toString(), 999_999_999, "0", "0", "90", "30", "0"))
				.withMessageContaining("diagram_shapes_entity_id_fkey");
		}
	}

	@Test
	void theShapesGoWithTheirDiagramAndTheEntityIsKeptByAForeignKey() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			UUID viewpoint = UUID.fromString(text(statement, """
					SELECT viewpoint_id::text FROM diagrams WHERE id = '%s'""".formatted(diagram)));
			String removable = text(statement, """
					INSERT INTO diagrams (name, viewpoint_id, created_by, updated_by) VALUES ('Removable', '%s', %d, %d)
					RETURNING id::text""".formatted(viewpoint, kurt, kurt));
			shape(statement, removable, entity, "0", "0", "90", "30", "0");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("DELETE FROM entities WHERE id = " + entity))
				.withMessageContaining("diagram_shapes_entity_id_fkey");
			statement.executeUpdate("DELETE FROM diagrams WHERE id = '" + removable + "'");
			assertThat(number(statement, "SELECT count(*) FROM diagram_shapes WHERE diagram_id = '" + removable + "'"))
				.isZero();
		}
	}

}
