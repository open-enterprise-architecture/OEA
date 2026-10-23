package io.oea.testsupport.containers;

import io.oea.config.migration.MigrateCommand;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * JVM-wide PostgreSQL singleton for integration tests: provisioned by the real init script
 * and migrated once by the real migration step. The application connects as the runtime
 * role {@code oea_app}, exactly as in production.
 */
public final class OeaTestDatabase {

	private static final PostgreSQLContainer CONTAINER = OeaPostgres.provisioned();

	private static boolean migrated;

	private OeaTestDatabase() {
	}

	public static synchronized PostgreSQLContainer migrated() {
		if (!CONTAINER.isRunning()) {
			CONTAINER.start();
		}
		if (!migrated) {
			int exitCode = MigrateCommand.run(migrationArguments(CONTAINER));
			if (exitCode != 0) {
				throw new IllegalStateException("Migration step failed for the test database, exit code " + exitCode);
			}
			migrated = true;
		}
		return CONTAINER;
	}

	/** Registers the runtime data source ({@code oea_app}) of the migrated singleton. */
	public static void registerRuntimeDataSource(DynamicPropertyRegistry registry) {
		PostgreSQLContainer container = migrated();
		registry.add("spring.datasource.url", container::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
	}

	/** Command-line arguments of {@code oea migrate} for the given provisioned container. */
	public static String[] migrationArguments(PostgreSQLContainer container) {
		return migrationArguments(container.getJdbcUrl());
	}

	/** Command-line arguments of {@code oea migrate} for the given provisioned database. */
	public static String[] migrationArguments(String jdbcUrl) {
		return new String[] { "--oea.migration.url=" + jdbcUrl,
				"--oea.migration.owner.password=" + OeaPostgres.ROLE_PASSWORDS.get("oea_owner"),
				"--oea.migration.reporting-owner.password=" + OeaPostgres.ROLE_PASSWORDS.get("oea_reporting_owner") };
	}

}
