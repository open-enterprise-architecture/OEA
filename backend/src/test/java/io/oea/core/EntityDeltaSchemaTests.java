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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Migration db/migration/V26__entity_deltas.sql: owner, grants, shape checks and unique indexes. */
class EntityDeltaSchemaTests {

	private static OeaDatabase postgres;

	private static String personId;

	private static String metatypeId;

	private static String propertyId;

	private static String entityId;

	private static String solutionId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			personId = single(statement,
					"INSERT INTO persons (name, email, status) VALUES ('Michael', 'michael@example.org', 'active') RETURNING id::text");
			metatypeId = single(statement, """
					INSERT INTO metatypes (config_id, name, is_connection)
					VALUES ('00000000-0000-0000-0000-000000000001', 'Delta Application', false) RETURNING id::text""");
			propertyId = single(statement, """
					INSERT INTO property_definitions (name, sql_column_name, data_type)
					VALUES ('Delta Users', 'delta_users', 'integer') RETURNING id::text""");
			entityId = single(statement, "INSERT INTO entities (metatype_id, name, created_by) VALUES ('" + metatypeId
					+ "', 'CRM-Legacy', " + personId + ") RETURNING id::text");
			solutionId = single(statement, "INSERT INTO solutions (id, name, owner_id, created_by) VALUES (gen_random_uuid(), "
					+ "'Schema', " + personId + ", " + personId + ") RETURNING id::text");
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@ParameterizedTest
	@ValueSource(strings = { "entity_deltas", "entity_delta_property_values" })
	void ownerAndGrants(String table) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			assertThat(single(statement,
					"SELECT tableowner FROM pg_tables WHERE schemaname = 'public' AND tablename = '" + table + "'"))
				.isEqualTo("oea_owner");
			List<String> privileges = new ArrayList<>();
			try (ResultSet rs = statement.executeQuery("""
					SELECT privilege_type FROM information_schema.role_table_grants
					WHERE table_name = '%s' AND grantee = 'oea_app' ORDER BY privilege_type""".formatted(table))) {
				while (rs.next()) {
					privileges.add(rs.getString(1));
				}
			}
			assertThat(privileges).containsExactly("DELETE", "INSERT", "SELECT");
		}
	}

	@Test
	void shapeOfNewAndRetiring() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.execute(newDelta("Shape New"));
			statement.execute(retiring(entityId));
			// new without a name, new with an entity, retiring without an entity, retiring with a name
			for (String invalid : List.of(insert("'new'", "NULL", "'" + metatypeId + "'", "NULL"),
					insert("'new'", entityId, "'" + metatypeId + "'", "'With entity'"),
					insert("'retiring'", "NULL", "NULL", "NULL"),
					insert("'retiring'", entityId, "'" + metatypeId + "'", "'Named'"),
					insert("'changed'", entityId, "NULL", "NULL"))) {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.execute(invalid))
					.satisfies((ex) -> assertThat(ex.getSQLState()).isIn("23514", "23505"));
			}
		}
	}

	@Test
	void oneDeltaPerEntityAndNewName() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.execute(newDelta("Salesforce"));
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.execute(newDelta("SALESFORCE")))
				.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23505"));
		}
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			String second = single(statement, "INSERT INTO entities (metatype_id, name, created_by) VALUES ('"
					+ metatypeId + "', 'ERP-Core', " + personId + ") RETURNING id::text");
			statement.execute(retiring(second));
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.execute(retiring(second)))
				.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23505"));
		}
	}

	@Test
	void lengthOfNameAndDescription() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.execute(newDelta("n".repeat(255)));
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.execute(newDelta("m".repeat(256))))
				.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23514"));
			statement.execute(newDelta("Described", "d".repeat(2000)));
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.execute(newDelta("Too described", "d".repeat(2001))))
				.satisfies((ex) -> assertThat(ex.getSQLState()).isEqualTo("23514"));
		}
	}

	@Test
	void exactlyOneValueAndCascade() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String delta = single(statement, newDelta("Valued") + " RETURNING id::text");
			statement.execute(value(delta, "after_value_int", "250"));
			for (String invalid : List.of(value(delta, "after_value_text", "NULL"),
					"INSERT INTO entity_delta_property_values (id, delta_id, property_def_id, after_value_int, after_value_text)"
							+ " VALUES (gen_random_uuid(), '" + delta + "', '" + propertyId + "', 1, 'x')")) {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.execute(invalid))
					.satisfies((ex) -> assertThat(ex.getSQLState()).isIn("23514", "23505"));
			}
			statement.execute("DELETE FROM entity_deltas WHERE id = '" + delta + "'");
			assertThat(single(statement,
					"SELECT count(*)::text FROM entity_delta_property_values WHERE delta_id = '" + delta + "'"))
				.isEqualTo("0");
		}
	}

	private static String newDelta(String name) {
		return insert("'new'", "NULL", "'" + metatypeId + "'", "'" + name + "'");
	}

	private static String newDelta(String name, String description) {
		return "INSERT INTO entity_deltas (id, solution_id, delta_type, metatype_id, name, description, created_by)"
				+ " VALUES (gen_random_uuid(), '" + solutionId + "', 'new', '" + metatypeId + "', '" + name + "', '"
				+ description + "', " + personId + ")";
	}

	private static String retiring(String entity) {
		return insert("'retiring'", entity, "NULL", "NULL");
	}

	private static String insert(String type, String entity, String metatype, String name) {
		return "INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, metatype_id, name, created_by)"
				+ " VALUES (gen_random_uuid(), '" + solutionId + "', " + type + ", " + entity + ", " + metatype + ", "
				+ name + ", " + personId + ")";
	}

	private static String value(String delta, String column, String literal) {
		return "INSERT INTO entity_delta_property_values (id, delta_id, property_def_id, " + column
				+ ") VALUES (gen_random_uuid(), '" + delta + "', '" + propertyId + "', " + literal + ")";
	}

	private static String single(Statement statement, String sql) throws SQLException {
		try (ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

}
