package io.oea.testsupport.containers;

import java.io.IOException;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * An own database on the shared test server, cloned from the migrated template (design D3 of
 * s2b-backend-hardening). It offers the container methods the tests use, so that a test sees
 * a fresh, provisioned and migrated database as before; {@link #stop()} drops it.
 */
public final class OeaDatabase {

	private final PostgreSQLContainer server;

	private final String name;

	OeaDatabase(PostgreSQLContainer server, String name) {
		this.server = server;
		this.name = name;
	}

	public String getJdbcUrl() {
		return this.server.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + this.name + "$1");
	}

	public String getDatabaseName() {
		return this.name;
	}

	/** Superuser of the shared server. */
	public String getUsername() {
		return this.server.getUsername();
	}

	public String getPassword() {
		return this.server.getPassword();
	}

	/** Runs a command in the shared server's container, e.g. {@code pg_dump} of this database. */
	public ExecResult execInContainer(String... command) throws IOException, InterruptedException {
		return this.server.execInContainer(command);
	}

	/** Drops the database; open connections are terminated. */
	public void stop() {
		OeaDatabases.drop(this.name);
	}

}
