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
 * Migration db/migration/V41__viewpoint_notations.sql (us-091-viewpoint-notations D1): existing
 * viewpoints become generic and user-defined, the checks of notation and origin, the size and
 * hint checks of a mapping, and the cascade on a deleted metatype.
 */
class ViewpointNotationsMigrationTests {

	private static final String INSTANCE_CONFIG = "00000000-0000-0000-0000-000000000001";

	private static OeaDatabase postgres;

	private static long personId;

	private static String viewpointId;

	private static String metatypeId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		personId = Long.parseLong(single(postgres, "INSERT INTO persons (name, email) VALUES ('Kurt', "
				+ "'kurt-vp@example.org') RETURNING id"));
		viewpointId = single(postgres, "INSERT INTO viewpoints (id, name, created_by) VALUES (gen_random_uuid(), "
				+ "'Checked', " + personId + ") RETURNING id::text");
		metatypeId = metatype("Checked Type");
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void existingViewpointsBecomeGenericAndUserDefined() throws Exception {
		OeaDatabase old = OeaDatabases.freshProvisioned();
		try {
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.target("40")
				.load()
				.migrate();
			long person = Long.parseLong(single(old, "INSERT INTO persons (name, email) VALUES ('Kurt', "
					+ "'kurt-old-vp@example.org') RETURNING id"));
			String id = single(old, "INSERT INTO viewpoints (id, name, created_by) VALUES (gen_random_uuid(), "
					+ "'Integration View', " + person + ") RETURNING id::text");
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.load()
				.migrate();
			assertThat(single(old, "SELECT notation || ':' || viewpoint_type || ':' || coalesce(source_package_id, '-') "
					+ "FROM viewpoints WHERE id = '" + id + "'")).isEqualTo("generic:user-defined:-");
		}
		finally {
			old.stop();
		}
	}

	@ParameterizedTest
	@CsvSource({ "generic, true", "archimate3, true", "uml, true", "bpmn2, true", "visio, false", "ARCHIMATE3, false" })
	void theNotationIsLimited(String notation, boolean accepted) throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String sql = "INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), '"
					+ UUID.randomUUID() + "', '" + notation + "', " + personId + ")";
			if (accepted) {
				statement.executeUpdate(sql);
			}
			else {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate(sql))
					.withMessageContaining("viewpoints_notation_check");
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "system-defined, starter, true", "user-defined, , true", "system-defined, , false",
			"user-defined, starter, false", "package, starter, false" })
	void systemDefinedExactlyWithAPackage(String type, String source, boolean accepted) throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String sql = "INSERT INTO viewpoints (id, name, viewpoint_type, source_package_id, created_by) VALUES "
					+ "(gen_random_uuid(), '" + UUID.randomUUID() + "', '" + type + "', "
					+ ((source != null) ? "'" + source + "'" : "NULL") + ", " + personId + ")";
			if (accepted) {
				statement.executeUpdate(sql);
			}
			else {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate(sql))
					.withMessageContaining("viewpoints_");
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "1, 1, true", "2000, 2000, true", "0, 80, false", "80, 0, false", "2001, 80, false",
			"80, 2001, false" })
	void theSizeBoundaries(int width, int height, boolean accepted) throws SQLException {
		String type = metatype("Size " + UUID.randomUUID());
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String sql = mapping(type, "archimate:Node", width, height, null);
			if (accepted) {
				statement.executeUpdate(sql);
			}
			else {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate(sql))
					.withMessageContaining("default_");
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "#1a2B3c, true", "#000000, true", "red, false", "#1a2b3, false", "#1a2b3c4, false",
			"#1a2b3g, false" })
	void theVisualHintPattern(String hint, boolean accepted) throws SQLException {
		String type = metatype("Hint " + UUID.randomUUID());
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String sql = mapping(type, "archimate:Node", null, null, hint);
			if (accepted) {
				statement.executeUpdate(sql);
			}
			else {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate(sql))
					.withMessageContaining("visual_hint");
			}
		}
	}

	@Test
	void aMetatypeIsMappedOncePerViewpoint() throws SQLException {
		String type = metatype("Once " + UUID.randomUUID());
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate(mapping(type, "archimate:Node", null, null, null));
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(mapping(type, "archimate:Device", null, null, null)))
				.withMessageContaining("viewpoint_mappings_pkey");
		}
	}

	@Test
	void theMappingsGoWithTheViewpointAndTheMetatype() throws SQLException {
		String doomedType = metatype("Doomed " + UUID.randomUUID());
		String doomedViewpoint = single(postgres, "INSERT INTO viewpoints (id, name, created_by) VALUES "
				+ "(gen_random_uuid(), 'Doomed " + UUID.randomUUID() + "', " + personId + ") RETURNING id::text");
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate("INSERT INTO viewpoint_mappings (viewpoint_id, metatype_id, notation_element) "
					+ "VALUES ('" + doomedViewpoint + "', '" + doomedType + "', 'archimate:Node')");
			statement.executeUpdate("INSERT INTO viewpoint_mappings (viewpoint_id, metatype_id, notation_element) "
					+ "VALUES ('" + doomedViewpoint + "', '" + metatypeId + "', 'archimate:Node')");
		}
		single(postgres, "DELETE FROM metatypes WHERE id = '" + doomedType + "' RETURNING id::text");
		assertThat(single(postgres, "SELECT count(*) FROM viewpoint_mappings WHERE viewpoint_id = '" + doomedViewpoint
				+ "'")).isEqualTo("1");
		single(postgres, "DELETE FROM viewpoints WHERE id = '" + doomedViewpoint + "' RETURNING id::text");
		assertThat(single(postgres, "SELECT count(*) FROM viewpoint_mappings WHERE viewpoint_id = '" + doomedViewpoint
				+ "'")).isEqualTo("0");
	}

	private static String mapping(String type, String element, Integer width, Integer height, String hint) {
		return "INSERT INTO viewpoint_mappings (viewpoint_id, metatype_id, notation_element, default_width, "
				+ "default_height, visual_hint) VALUES ('" + viewpointId + "', '" + type + "', '" + element + "', "
				+ ((width != null) ? width : "NULL") + ", " + ((height != null) ? height : "NULL") + ", "
				+ ((hint != null) ? "'" + hint + "'" : "NULL") + ")";
	}

	private static String metatype(String name) throws SQLException {
		return single(postgres, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG + "', '" + name
				+ "') RETURNING id::text");
	}

	private static String single(OeaDatabase database, String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(database); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

}
