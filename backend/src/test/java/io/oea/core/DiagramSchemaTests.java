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
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Migration db/migration/V46__diagrams.sql (US-074; design D1 of us-074-diagrams): the owner and
 * the grants, the name check (trimmed, 1 to 200 characters), the unique name ignoring case, the
 * description limit and the foreign key to the viewpoint.
 */
class DiagramSchemaTests {

	private static OeaDatabase postgres;

	private static long kurt;

	private static UUID viewpoint;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			try (ResultSet rs = statement.executeQuery(
					"INSERT INTO persons (name, email) VALUES ('Kurt', 'kurt@example.org') RETURNING id")) {
				rs.next();
				kurt = rs.getLong(1);
			}
			try (ResultSet rs = statement.executeQuery("""
					INSERT INTO viewpoints (id, name, created_by)
					VALUES (gen_random_uuid(), 'Schema Viewpoint', %d) RETURNING id::text""".formatted(kurt))) {
				rs.next();
				viewpoint = UUID.fromString(rs.getString(1));
			}
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	private static void diagram(Statement statement, String name, String description, UUID viewpointId)
			throws SQLException {
		statement.executeUpdate("""
				INSERT INTO diagrams (name, description, viewpoint_id, created_by, updated_by)
				VALUES (%s, %s, '%s', %d, %d)""".formatted(name, description, viewpointId, kurt, kurt));
	}

	@Test
	void tableIsOwnedByTheOwnerRoleAndTheRuntimeRoleMayWriteAndDelete() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("""
						SELECT count(*) FROM pg_tables WHERE schemaname = 'public'
						AND tablename = 'diagrams' AND tableowner = 'oea_owner'""")) {
			rs.next();
			assertThat(rs.getInt(1)).isEqualTo(1);
		}
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			diagram(statement, "'Deletable'", "NULL", viewpoint);
			assertThat(statement.executeUpdate("DELETE FROM diagrams WHERE name = 'Deletable'")).isEqualTo(1);
		}
	}

	@Test
	void namesAreUniqueIgnoringCase() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			diagram(statement, "'Order to Cash'", "NULL", viewpoint);
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> diagram(statement, "'ORDER TO CASH'", "NULL", viewpoint))
				.withMessageContaining("diagrams_name_unique");
			diagram(statement, "'Order to Cash 2'", "NULL", viewpoint);
		}
	}

	@Test
	void theNameOfADeletedDiagramIsFreeAndTheDeletionIsRecordedWithItsActor() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			diagram(statement, "'Deleted and reused'", "NULL", viewpoint);
			assertThat(statement.executeUpdate(
					"UPDATE diagrams SET deleted_at = now(), deleted_by = " + kurt + " WHERE name = 'Deleted and reused'"))
				.isEqualTo(1);
			diagram(statement, "'DELETED AND REUSED'", "NULL", viewpoint);
			assertThatExceptionOfType(SQLException.class).isThrownBy(
					() -> statement.executeUpdate("UPDATE diagrams SET deleted_at = now() WHERE name = 'DELETED AND REUSED'"))
				.withMessageContaining("diagrams_deleted_check");
			assertThatExceptionOfType(SQLException.class).isThrownBy(
					() -> statement.executeUpdate("UPDATE diagrams SET deleted_by = " + kurt + " WHERE name = 'DELETED AND REUSED'"))
				.withMessageContaining("diagrams_deleted_check");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "' Padded '", "''", "'Padded '" })
	void namesAreStoredTrimmedAndNotBlank(String name) throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> diagram(statement, name, "NULL", viewpoint))
				.withMessageContaining("diagrams_name_check");
		}
	}

	@Test
	void nameLengthIsLimitedTo200() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			diagram(statement, "'" + "n".repeat(200) + "'", "NULL", viewpoint);
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> diagram(statement, "'" + "m".repeat(201) + "'", "NULL", viewpoint))
				.withMessageContaining("diagrams_name_check");
		}
	}

	@Test
	void descriptionIsLimitedTo2000() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			diagram(statement, "'Long description'", "'" + "d".repeat(2000) + "'", viewpoint);
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> diagram(statement, "'Too long description'", "'" + "d".repeat(2001) + "'", viewpoint))
				.withMessageContaining("diagrams_description_check");
		}
	}

	@Test
	void theViewpointMustExistAndKeepsItsDiagrams() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> diagram(statement, "'No viewpoint'", "NULL", UUID.randomUUID()))
				.withMessageContaining("diagrams_viewpoint_id_fkey");
			diagram(statement, "'Used viewpoint'", "NULL", viewpoint);
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("DELETE FROM viewpoints WHERE id = '" + viewpoint + "'"))
				.withMessageContaining("diagrams_viewpoint_id_fkey");
		}
	}

}
