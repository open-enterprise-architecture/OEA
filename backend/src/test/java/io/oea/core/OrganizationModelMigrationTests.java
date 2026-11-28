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
 * Migration db/migration/V53__organization_model.sql (change us-075-lane-assignments D2, D5): the one-row settings
 * start unset, the checks of the account attribute and of the single row, the unique derived link, the grants of the
 * runtime role, and the existing manual links that stay unchanged.
 */
class OrganizationModelMigrationTests {

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
	void theSettingsStartUnsetAndExistingLinksStayUnchanged() throws Exception {
		OeaDatabase old = OeaDatabases.freshProvisioned();
		try {
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.target("52")
				.load()
				.migrate();
			String metatype = single(old, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG
					+ "', 'Actor') RETURNING id::text");
			String person = single(old, "INSERT INTO persons (name, email) VALUES ('Old Link', 'old-link@example.org') RETURNING id::text");
			String element = single(old, "INSERT INTO entities (metatype_id, name, created_by) VALUES ('" + metatype + "', 'Old', "
					+ person + ") RETURNING id::text");
			single(old, "UPDATE persons SET entity_id = " + element + " WHERE id = " + person + " RETURNING id::text");
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.load()
				.migrate();
			assertThat(single(old, "SELECT entity_id::text FROM persons WHERE id = " + person)).isEqualTo(element);
			assertThat(single(old, "SELECT mapped_entity_id IS NULL FROM persons WHERE id = " + person)).isEqualTo("t");
			assertThat(single(old, "SELECT count(*) FROM organization_model WHERE org_unit_metatype_id IS NULL AND role_metatype_id IS NULL "
					+ "AND person_metatype_id IS NULL AND belongs_to_metatype_id IS NULL AND performs_role_metatype_id IS NULL "
					+ "AND responsible_metatype_id IS NULL AND version = 1")).isEqualTo("1");
			assertThat(single(old, "SELECT count(*) FROM person_link_mapping WHERE property_def_id IS NULL AND account_attribute IS NULL"))
				.isEqualTo("1");
		}
		finally {
			old.stop();
		}
	}

	/** the account attribute, whether the row is accepted */
	@ParameterizedTest
	@CsvSource({ "email, true", "idp-subject, true", "employee-id, false", "external-reference, false" })
	void onlyTheAttributesEmailAndIdpSubjectAreStored(String attribute, boolean accepted) throws SQLException {
		String property = single(postgres, "INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('Mapping "
				+ UUID.randomUUID() + "', 'm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
				+ "', 'string') RETURNING id::text");
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String update = "UPDATE person_link_mapping SET property_def_id = '" + property + "', account_attribute = '" + attribute
					+ "' WHERE id = 1";
			if (accepted) {
				assertThat(statement.executeUpdate(update)).isOne();
				statement.executeUpdate("UPDATE person_link_mapping SET property_def_id = NULL, account_attribute = NULL WHERE id = 1");
			}
			else {
				assertThatExceptionOfType(SQLException.class).isThrownBy(() -> statement.executeUpdate(update))
					.withMessageContaining("person_link_mapping_account_attribute_check");
			}
		}
	}

	@Test
	void eachSettingIsOneRowAndTheRuntimeRoleOnlyUpdatesIt() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			for (String table : new String[] { "organization_model", "person_link_mapping" }) {
				assertThatExceptionOfType(SQLException.class)
					.isThrownBy(() -> statement.executeUpdate("INSERT INTO " + table + " (id) VALUES (2)"))
					.withMessageContaining("permission denied");
				assertThatExceptionOfType(SQLException.class)
					.isThrownBy(() -> statement.executeUpdate("DELETE FROM " + table))
					.withMessageContaining("permission denied");
			}
		}
		try (Connection owner = OeaDatabases.asSuperuser(postgres); Statement statement = owner.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO organization_model (id) VALUES (2)"))
				.withMessageContaining("organization_model_id_check");
		}
	}

	@Test
	void aMetatypeOrPropertyThatIsDeletedUnsetsItsPart() throws SQLException {
		String type = single(postgres, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG + "', 'Short "
				+ UUID.randomUUID() + "') RETURNING id::text");
		try (Connection owner = OeaDatabases.asSuperuser(postgres); Statement statement = owner.createStatement()) {
			statement.executeUpdate("UPDATE organization_model SET role_metatype_id = '" + type + "'");
			assertThat(single(postgres, "SELECT role_metatype_id IS NOT NULL FROM organization_model")).isEqualTo("t");
			statement.executeUpdate("DELETE FROM metatypes WHERE id = '" + type + "'");
			assertThat(single(postgres, "SELECT role_metatype_id IS NULL FROM organization_model")).isEqualTo("t");
		}
	}

	@Test
	void theDerivedLinkIsUniquePerElementAndNeverTheSameAsAnotherAccountsLink() throws SQLException {
		String metatype = single(postgres, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG + "', 'Actor "
				+ UUID.randomUUID() + "') RETURNING id::text");
		String first = single(postgres, "INSERT INTO persons (name, email) VALUES ('First', 'first-" + UUID.randomUUID()
				+ "@example.org') RETURNING id::text");
		String second = single(postgres, "INSERT INTO persons (name, email) VALUES ('Second', 'second-" + UUID.randomUUID()
				+ "@example.org') RETURNING id::text");
		String element = single(postgres, "INSERT INTO entities (metatype_id, name, created_by) VALUES ('" + metatype + "', 'Element', "
				+ first + ") RETURNING id::text");
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThat(statement.executeUpdate("UPDATE persons SET mapped_entity_id = " + element + " WHERE id = " + first)).isOne();
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("UPDATE persons SET mapped_entity_id = " + element + " WHERE id = " + second))
				.withMessageContaining("persons_mapped_entity_id");
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
