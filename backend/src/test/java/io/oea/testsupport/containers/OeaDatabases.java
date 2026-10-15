package io.oea.testsupport.containers;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.config.migration.MigrateCommand;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Fresh, provisioned and migrated databases for tests that change data the shared
 * singleton must not see (tampering, concurrency, empty instances).
 */
public final class OeaDatabases {

	private OeaDatabases() {
	}

	/** Starts a new provisioned container and runs the migration step; the caller closes it. */
	public static PostgreSQLContainer freshMigrated() {
		PostgreSQLContainer postgres = OeaPostgres.provisioned();
		postgres.start();
		assertThat(MigrateCommand.run(OeaTestDatabase.migrationArguments(postgres))).isZero();
		return postgres;
	}

	public static Connection asSuperuser(PostgreSQLContainer postgres) throws SQLException {
		return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
	}

	public static Connection asRole(PostgreSQLContainer postgres, String role) throws SQLException {
		return DriverManager.getConnection(postgres.getJdbcUrl(), role, OeaPostgres.ROLE_PASSWORDS.get(role));
	}

}
