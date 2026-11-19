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
 * Migration db/migration/V45__viewpoint_sizes.sql (s17-viewpoint-default-sizes D1): the sizes
 * stored in mappings move to the new table in pt, the mapping columns are gone, and the table
 * checks the size boundaries and the kind of size.
 */
class ViewpointSizesMigrationTests {

	private static final String INSTANCE_CONFIG = "00000000-0000-0000-0000-000000000001";

	private static OeaDatabase postgres;

	private static long personId;

	private static String viewpointId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		personId = Long.parseLong(single(postgres, "INSERT INTO persons (name, email) VALUES ('Kurt', "
				+ "'kurt-sizes@example.org') RETURNING id"));
		viewpointId = single(postgres, "INSERT INTO viewpoints (id, name, created_by) VALUES (gen_random_uuid(), "
				+ "'Sized', " + personId + ") RETURNING id::text");
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void sizesInMappingsMoveToPt() throws Exception {
		OeaDatabase old = OeaDatabases.freshProvisioned();
		try {
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.target("44")
				.load()
				.migrate();
			long person = Long.parseLong(single(old, "INSERT INTO persons (name, email) VALUES ('Kurt', "
					+ "'kurt-old-sizes@example.org') RETURNING id"));
			String vp = single(old, "INSERT INTO viewpoints (id, name, created_by) VALUES (gen_random_uuid(), "
					+ "'Old Sizes', " + person + ") RETURNING id::text");
			String regular = element(old, "Regular", false);
			String tiny = element(old, "Tiny", false);
			String huge = element(old, "Huge", false);
			String widthOnly = element(old, "Width Only", false);
			String heightOnly = element(old, "Height Only", false);
			String none = element(old, "No Size", false);
			String flow = element(old, "Flow", true);
			String heightFlow = element(old, "Height Flow", true);
			map(old, vp, regular, 120, 55);
			map(old, vp, tiny, 1, 1);
			map(old, vp, huge, 2000, 2000);
			map(old, vp, widthOnly, 200, null);
			map(old, vp, heightOnly, null, 40);
			map(old, vp, none, null, null);
			map(old, vp, flow, 200, null);
			map(old, vp, heightFlow, null, 40);
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.load()
				.migrate();
			assertThat(size(old, vp, regular)).isEqualTo("90:41:-");
			assertThat(size(old, vp, tiny)).isEqualTo("1:1:-");
			assertThat(size(old, vp, huge)).isEqualTo("1500:1500:-");
			assertThat(size(old, vp, widthOnly)).isEqualTo("150:30:-");
			assertThat(size(old, vp, heightOnly)).isEqualTo("90:30:-");
			assertThat(size(old, vp, none)).isNull();
			assertThat(size(old, vp, flow)).isEqualTo("-:-:150");
			assertThat(size(old, vp, heightFlow)).isNull();
			assertThat(single(old, "SELECT count(*) FROM information_schema.columns WHERE table_name = "
					+ "'viewpoint_mappings' AND column_name IN ('default_width', 'default_height')")).isEqualTo("0");
			assertThat(single(old, "SELECT count(*) FROM viewpoint_mappings WHERE viewpoint_id = '" + vp + "'"))
				.isEqualTo("8");
		}
		finally {
			old.stop();
		}
	}

	@ParameterizedTest
	@CsvSource({ "1, 1, true", "2000, 2000, true", "0, 80, false", "80, 0, false", "2001, 80, false",
			"80, 2001, false" })
	void theElementSizeBoundaries(int width, int height, boolean accepted) throws SQLException {
		String type = element(postgres, "Size " + UUID.randomUUID(), false);
		insert("(" + width + ", " + height + ", NULL)", type, accepted, "_pt_check");
	}

	@ParameterizedTest
	@CsvSource({ "1, true", "2000, true", "0, false", "2001, false" })
	void theLengthBoundaries(int length, boolean accepted) throws SQLException {
		String type = element(postgres, "Length " + UUID.randomUUID(), true);
		insert("(NULL, NULL, " + length + ")", type, accepted, "_pt_check");
	}

	@ParameterizedTest
	@CsvSource({ "80, NULL, NULL", "NULL, 30, NULL", "NULL, NULL, NULL", "90, 30, 90", "NULL, 30, 90" })
	void aSizeIsAnElementOrAConnectionSize(String width, String height, String length) throws SQLException {
		String type = element(postgres, "Kind " + UUID.randomUUID(), false);
		insert("(" + width + ", " + height + ", " + length + ")", type, false, "viewpoint_sizes_check");
	}

	@Test
	void aMetatypeHasOneSizePerViewpointAndTheSizesGoWithThem() throws SQLException {
		String type = element(postgres, "Once " + UUID.randomUUID(), false);
		String doomedType = element(postgres, "Doomed " + UUID.randomUUID(), false);
		String doomed = single(postgres, "INSERT INTO viewpoints (id, name, created_by) VALUES (gen_random_uuid(), "
				+ "'Doomed " + UUID.randomUUID() + "', " + personId + ") RETURNING id::text");
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate(sql(viewpointId, type, "(80, 30, NULL)"));
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(sql(viewpointId, type, "(81, 31, NULL)")))
				.withMessageContaining("viewpoint_sizes_pkey");
			statement.executeUpdate(sql(doomed, type, "(80, 30, NULL)"));
			statement.executeUpdate(sql(doomed, doomedType, "(80, 30, NULL)"));
		}
		single(postgres, "DELETE FROM metatypes WHERE id = '" + doomedType + "' RETURNING id::text");
		assertThat(single(postgres, "SELECT count(*) FROM viewpoint_sizes WHERE viewpoint_id = '" + doomed + "'"))
			.isEqualTo("1");
		single(postgres, "DELETE FROM viewpoints WHERE id = '" + doomed + "' RETURNING id::text");
		assertThat(single(postgres, "SELECT count(*) FROM viewpoint_sizes WHERE viewpoint_id = '" + doomed + "'"))
			.isEqualTo("0");
	}

	private static void insert(String values, String type, boolean accepted, String constraint) throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String sql = sql(viewpointId, type, values);
			if (accepted) {
				statement.executeUpdate(sql);
			}
			else {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate(sql))
					.withMessageContaining(constraint);
			}
		}
	}

	private static String sql(String viewpoint, String type, String values) {
		return "INSERT INTO viewpoint_sizes (viewpoint_id, metatype_id, width_pt, height_pt, length_pt) VALUES ('"
				+ viewpoint + "', '" + type + "', " + values.substring(1);
	}

	private static String element(OeaDatabase database, String name, boolean connection) throws SQLException {
		return single(database, "INSERT INTO metatypes (config_id, name, is_connection) VALUES ('" + INSTANCE_CONFIG
				+ "', '" + name + "', " + connection + ") RETURNING id::text");
	}

	private static void map(OeaDatabase database, String viewpoint, String type, Integer width, Integer height)
			throws SQLException {
		single(database, "INSERT INTO viewpoint_mappings (viewpoint_id, metatype_id, notation_element, default_width, "
				+ "default_height) VALUES ('" + viewpoint + "', '" + type + "', 'archimate:Node', "
				+ ((width != null) ? width : "NULL") + ", " + ((height != null) ? height : "NULL")
				+ ") RETURNING metatype_id::text");
	}

	/** {@code width:height:length} of the size row with {@code -} for a null, or {@code null} without a row. */
	private static String size(OeaDatabase database, String viewpoint, String type) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(database); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("SELECT coalesce(width_pt::text, '-') || ':' || "
						+ "coalesce(height_pt::text, '-') || ':' || coalesce(length_pt::text, '-') FROM viewpoint_sizes "
						+ "WHERE viewpoint_id = '" + viewpoint + "' AND metatype_id = '" + type + "'")) {
			return rs.next() ? rs.getString(1) : null;
		}
	}

	private static String single(OeaDatabase database, String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(database); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

}
