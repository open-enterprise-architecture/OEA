package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Migration db/migration/V17__i18n_entries.sql (design D2 and D6 of us-140-dynamic-labels). */
class I18nEntriesSchemaTests {

	private static final String ENTITY = "'00000000-0000-0000-0000-0000000000aa'";

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
	void packageAndOrganizationLabelsAreSeparateRows() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO i18n_entries (entity_type, entity_id, locale, field, value, scope, package_id)
					VALUES ('metatype', %1$s, 'de', 'name', 'Paket', 'imported', 'p'),
					       ('metatype', %1$s, 'de', 'name', 'Eigen', 'organization', NULL)""".formatted(ENTITY));
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate("""
					INSERT INTO i18n_entries (entity_type, entity_id, locale, field, value, scope)
					VALUES ('metatype', %s, 'de', 'name', 'Zweimal', 'organization')""".formatted(ENTITY)))
				.withMessageContaining("i18n_entries_key");
		}
	}

	@Test
	void valuesAreChecked() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			for (String row : new String[] { "'role', %s, 'de', 'name', 'x', 'organization', NULL",
					"'metatype', %s, 'es', 'name', 'x', 'organization', NULL",
					"'metatype', %s, 'de', 'description', 'x', 'organization', NULL",
					"'metatype', %s, 'de', 'name', '', 'organization', NULL",
					"'metatype', %s, 'en', 'name', 'x', 'imported', NULL",
					"'metatype', %s, 'en', 'name', 'x', 'organization', 'p'" }) {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate("""
						INSERT INTO i18n_entries (entity_type, entity_id, locale, field, value, scope, package_id)
						VALUES (%s)""".formatted(row.formatted(ENTITY))));
			}
		}
	}

	@Test
	void theLanguageOfAPersonIsOptionalAndChecked() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate("INSERT INTO persons (name, email, status) VALUES ('Lang', 'lang@example.org', 'active')");
			try (ResultSet rs = statement
				.executeQuery("SELECT ui_language FROM persons WHERE email = 'lang@example.org'")) {
				rs.next();
				assertThat(rs.getString(1)).isNull();
			}
			statement.executeUpdate("UPDATE persons SET ui_language = 'fr' WHERE email = 'lang@example.org'");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement
					.executeUpdate("UPDATE persons SET ui_language = 'es' WHERE email = 'lang@example.org'"))
				.withMessageContaining("persons_ui_language");
			try (ResultSet rs = statement.executeQuery("SELECT count(*) FROM persons WHERE ui_language = 'de'")) {
				rs.next();
				// no person keeps the former column default
				assertThat(rs.getInt(1)).isZero();
			}
		}
	}

}
