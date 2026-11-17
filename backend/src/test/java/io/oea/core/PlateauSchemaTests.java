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

/**
 * Migration db/migration/V37__plateaus.sql (design D1 of us-084-plateaus): one baseline at most,
 * the shape of predecessor and basis, the column grants, the foreign keys from solutions and the
 * permission plateau:write.
 */
class PlateauSchemaTests {

	private static OeaDatabase postgres;

	private static String personId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			personId = single(statement,
					"INSERT INTO persons (name, email, status) VALUES ('Kurt', 'kurt@example.org', 'active') RETURNING id::text");
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void oneBaselineAndShapes() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String realized = single(statement, plateau("Old", "realized", "NULL", "NULL"));
			String baseline = single(statement, plateau("Baseline", "baseline", "NULL", "NULL"));
			// a second baseline, while a realized one exists
			expect(statement, plateau("Second", "baseline", "NULL", "NULL"), "23505");
			// a baseline with predecessor or basis, a target without them
			expect(statement, plateau("B2", "baseline", "'" + realized + "'", "NULL"), "23514");
			expect(statement, plateau("B3", "baseline", "NULL", "'catalog'"), "23514");
			expect(statement, plateau("T1", "target", "NULL", "'catalog'"), "23514");
			expect(statement, plateau("T2", "transition", "'" + baseline + "'", "NULL"), "23514");
			expect(statement, plateau("T3", "target", "'" + baseline + "'", "'other'"), "23514");
			String target = single(statement, plateau("Target", "target", "'" + baseline + "'", "'predecessor'"));
			expect(statement, "UPDATE plateaus SET succeeds = id WHERE id = '" + target + "'", "23514");
			// the name is unique case-insensitively
			expect(statement, plateau("target", "target", "'" + baseline + "'", "'catalog'"), "23505");
		}
	}

	@Test
	void runtimeUpdatesNoStatus() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			assertThat(strings(statement, """
					SELECT column_name FROM information_schema.column_privileges
					WHERE table_name = 'plateaus' AND grantee = 'oea_app' AND privilege_type = 'UPDATE'
					ORDER BY column_name""")).containsExactly("basis", "description", "name", "succeeds", "updated_at",
					"updated_by", "valid_from");
		}
	}

	@Test
	void solutionsReferencePlateaus() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			expect(statement, "INSERT INTO solutions (id, name, owner_id, created_by, from_plateau_id, to_plateau_id)"
					+ " VALUES (gen_random_uuid(), 'Unknown', " + personId + ", " + personId
					+ ", gen_random_uuid(), gen_random_uuid())", "23503");
		}
	}

	@Test
	void permissionPlateauWrite() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			assertThat(strings(statement,
					"SELECT id FROM permissions WHERE category = 'plateau' ORDER BY sort_order"))
				.containsExactly("plateau:write", "plateau:go-live");
			assertThat(strings(statement, """
					SELECT role_id FROM role_permissions WHERE permission_id = 'plateau:write' ORDER BY role_id"""))
				.containsExactly("enterprise-architect");
			assertThat(strings(statement,
					"SELECT count(*) - count(DISTINCT sort_order) FROM permissions")).containsExactly("0");
		}
	}

	private static String plateau(String name, String status, String succeeds, String basis) {
		return "INSERT INTO plateaus (id, name, status, succeeds, basis, created_by) VALUES (gen_random_uuid(), '"
				+ name + "', '" + status + "', " + succeeds + ", " + basis + ", " + personId + ") RETURNING id::text";
	}

	private static void expect(Statement statement, String sql, String state) {
		assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.execute(sql))
			.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo(state));
	}

	private static List<String> strings(Statement statement, String sql) throws SQLException {
		List<String> values = new ArrayList<>();
		try (ResultSet rs = statement.executeQuery(sql)) {
			while (rs.next()) {
				values.add(rs.getString(1));
			}
		}
		return values;
	}

	private static String single(Statement statement, String sql) throws SQLException {
		try (ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

}
