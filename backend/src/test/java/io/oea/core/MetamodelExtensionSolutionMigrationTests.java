package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Migration db/migration/V34__metamodel_extension_solution.sql (issue #237; design D6 of
 * s13-issue-fixes): it aborts and names extensions without a solution, and otherwise adds the
 * foreign key. The test drops the key of the migrated database and runs the script again.
 */
class MetamodelExtensionSolutionMigrationTests {

	private static final String CONSTRAINT = "metamodel_configurations_solution_fk";

	private static OeaDatabase postgres;

	private static String script;

	@BeforeAll
	static void start() throws IOException {
		postgres = OeaDatabases.freshMigrated();
		try (InputStream in = MetamodelExtensionSolutionMigrationTests.class
			.getResourceAsStream("/db/migration/V34__metamodel_extension_solution.sql")) {
			script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void orphanedExtensionAbortsTheMigration() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			statement.execute("ALTER TABLE metamodel_configurations DROP CONSTRAINT " + CONSTRAINT);
			UUID orphan = UUID.randomUUID();
			statement.execute("""
					INSERT INTO metamodel_configurations (id, scope, parent_id, solution_id, name, edit_mode)
					VALUES ('%s', 'solution', '00000000-0000-0000-0000-000000000001', '%s', 'Orphaned extension',
					        'gui-and-import')""".formatted(UUID.randomUUID(), orphan));
			su.setAutoCommit(false);
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.execute(script))
				.withMessageContaining("Orphaned extension")
				.withMessageContaining(orphan.toString());
			su.rollback();
			su.setAutoCommit(true);
			assertThat(constraintExists(statement)).isFalse();

			// resolved by the operator: the migration adds the key
			statement.execute("DELETE FROM metamodel_configurations WHERE name = 'Orphaned extension'");
			statement.execute(script);
			assertThat(constraintExists(statement)).isTrue();
		}
	}

	private static boolean constraintExists(Statement statement) throws SQLException {
		try (ResultSet rs = statement.executeQuery(
				"SELECT count(*) FROM pg_constraint WHERE conname = '" + CONSTRAINT + "'")) {
			rs.next();
			return rs.getInt(1) == 1;
		}
	}

}
