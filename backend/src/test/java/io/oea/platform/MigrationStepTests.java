package io.oea.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.OeaBackendApplication;
import io.oea.config.migration.MigrateCommand;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestDatabase;
import io.oea.testsupport.containers.OeaTestKeys;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Spec platform/database-roles: Separate migration step. */
class MigrationStepTests {

	private PostgreSQLContainer postgres;

	@BeforeEach
	void startFreshDatabase() {
		this.postgres = OeaPostgres.provisioned();
		this.postgres.start();
	}

	@AfterEach
	void stopDatabase() {
		this.postgres.stop();
	}

	@Test
	void migrationStepAppliesAllLocationsAsTheirOwners() throws SQLException {
		assertThat(MigrateCommand.run(OeaTestDatabase.migrationArguments(this.postgres))).isZero();

		assertThat(query("""
				SELECT schemaname || ':' || tableowner FROM pg_tables
				WHERE tablename = 'flyway_schema_history' ORDER BY schemaname""")).containsExactly("audit:oea_owner",
						"public:oea_owner", "reporting:oea_reporting_owner");
		assertThat(query("SELECT version || ':' || installed_by FROM audit.flyway_schema_history WHERE success ORDER BY installed_rank"))
			.startsWith("1:oea_owner").allMatch((row) -> row.endsWith(":oea_owner"));
		assertThat(query("SELECT version || ':' || installed_by FROM reporting.flyway_schema_history WHERE success"))
			.containsExactly("1:oea_reporting_owner");
	}

	@Test
	void auditTablesAreOwnedByTheOwnerRole() throws SQLException {
		assertThat(MigrateCommand.run(OeaTestDatabase.migrationArguments(this.postgres))).isZero();

		assertThat(query("SELECT tablename || ':' || tableowner FROM pg_tables WHERE schemaname = 'audit' ORDER BY 1"))
			.containsExactly("chain_head:oea_owner", "event_changes:oea_owner", "events:oea_owner",
					"flyway_schema_history:oea_owner", "retention_settings:oea_owner");
		assertThat(query("""
				SELECT nspname || ':' || pg_get_userbyid(nspowner) FROM pg_namespace
				WHERE nspname IN ('reporting', 'reporting_ctx') ORDER BY 1"""))
			.containsExactly("reporting:oea_reporting_owner", "reporting_ctx:oea_reporting_owner");
	}

	@Test
	void migrationStepFailsWithExitCodeOneOnWrongCredentials() {
		String[] arguments = OeaTestDatabase.migrationArguments(this.postgres);
		arguments[1] = "--oea.migration.owner.password=wrong";
		assertThat(MigrateCommand.run(arguments)).isEqualTo(1);
	}

	@Test
	void applicationDoesNotMigrateAndNamesTheMigrationStep() throws SQLException {
		assertThatExceptionOfType(Exception.class).isThrownBy(this::startApplicationAsRuntimeRole)
			.withStackTraceContaining("Run the migration step ('oea migrate')");
		assertThat(query("SELECT count(*)::text FROM pg_tables WHERE schemaname IN ('public', 'audit', 'reporting')"))
			.containsExactly("0");
	}

	@Test
	void applicationStartsAfterTheMigrationStep() {
		assertThat(MigrateCommand.run(OeaTestDatabase.migrationArguments(this.postgres))).isZero();
		try (ConfigurableApplicationContext context = startApplicationAsRuntimeRole()) {
			assertThat(context.isActive()).isTrue();
		}
	}

	private ConfigurableApplicationContext startApplicationAsRuntimeRole() {
		return new SpringApplicationBuilder(OeaBackendApplication.class).web(WebApplicationType.NONE)
			.run("--spring.datasource.url=" + this.postgres.getJdbcUrl(), "--spring.datasource.username=oea_app",
					"--spring.datasource.password=" + OeaPostgres.ROLE_PASSWORDS.get("oea_app"),
					"--oea.security.kek.current=" + OeaTestKeys.sharedKeyFile());
	}

	private List<String> query(String sql) throws SQLException {
		List<String> rows = new ArrayList<>();
		try (Connection connection = DriverManager.getConnection(this.postgres.getJdbcUrl(),
				this.postgres.getUsername(), this.postgres.getPassword());
				Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				rows.add(resultSet.getString(1));
			}
		}
		return rows;
	}

}
