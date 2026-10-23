package io.oea.testsupport.containers;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.config.migration.MigrateCommand;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Fresh, provisioned and migrated databases for tests that change data the shared database
 * must not see (tampering, concurrency, empty instances). They are cloned on the shared test
 * server from the template {@value #TEMPLATE}, which is provisioned and migrated once per JVM
 * (design D3 of s2b-backend-hardening); the provisioning script sets the database-level
 * privileges of every clone, because {@code CREATE DATABASE … TEMPLATE} does not copy them.
 */
public final class OeaDatabases {

	static final String TEMPLATE = "oea_template";

	private static final AtomicInteger counter = new AtomicInteger();

	private static boolean templateReady;

	private OeaDatabases() {
	}

	/** A new database cloned from the migrated template; the caller drops it with {@link OeaDatabase#stop()}. */
	public static OeaDatabase freshMigrated() {
		PostgreSQLContainer server = OeaTestDatabase.migrated();
		String name = "oea_t" + counter.incrementAndGet();
		synchronized (OeaDatabases.class) {
			if (!templateReady) {
				createTemplate(server);
				templateReady = true;
			}
			execute(server, "CREATE DATABASE " + name + " TEMPLATE " + TEMPLATE);
		}
		provision(server, name);
		return new OeaDatabase(server, name);
	}

	public static Connection asSuperuser(PostgreSQLContainer postgres) throws SQLException {
		return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
	}

	public static Connection asSuperuser(OeaDatabase database) throws SQLException {
		return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
	}

	public static Connection asRole(PostgreSQLContainer postgres, String role) throws SQLException {
		return DriverManager.getConnection(postgres.getJdbcUrl(), role, OeaPostgres.ROLE_PASSWORDS.get(role));
	}

	public static Connection asRole(OeaDatabase database, String role) throws SQLException {
		return DriverManager.getConnection(database.getJdbcUrl(), role, OeaPostgres.ROLE_PASSWORDS.get(role));
	}

	static void drop(String name) {
		execute(OeaTestDatabase.migrated(), "DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
	}

	/** Provisions and migrates the template with the production script and migration step. */
	private static void createTemplate(PostgreSQLContainer server) {
		execute(server, "CREATE DATABASE " + TEMPLATE);
		provision(server, TEMPLATE);
		OeaDatabase template = new OeaDatabase(server, TEMPLATE);
		assertThat(MigrateCommand.run(OeaTestDatabase.migrationArguments(template.getJdbcUrl()))).isZero();
		// no connection may remain open, otherwise the template cannot be copied
		execute(server, "ALTER DATABASE " + TEMPLATE + " WITH ALLOW_CONNECTIONS false");
		execute(server, "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '" + TEMPLATE + "'");
	}

	/** Runs the provisioning script for the database: database-level privileges and schemas (ADR-037). */
	private static void provision(PostgreSQLContainer server, String database) {
		try {
			ExecResult result = server.execInContainer("env", "OEA_DB_NAME=" + database, "bash",
					"/docker-entrypoint-initdb.d/10-oea-roles.sh");
			if (result.getExitCode() != 0) {
				throw new IllegalStateException("provisioning of " + database + " failed: " + result.getStderr());
			}
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private static void execute(PostgreSQLContainer server, String sql) {
		try (Connection connection = asSuperuser(server); Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
		catch (SQLException ex) {
			throw new IllegalStateException(sql, ex);
		}
	}

}
