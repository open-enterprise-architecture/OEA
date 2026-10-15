package io.oea.config.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.OeaBackendApplication;
import io.oea.config.migration.MigrateCommand;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Spec platform/startup-safety (PostgreSQL version) and platform/database-roles (fail closed). */
class DatabaseStartupChecksTests {

	/** postgres:14-alpine, pinned by digest: one major version below the minimum. */
	private static final DockerImageName POSTGRES_14 = DockerImageName
		.parse("postgres@sha256:4ea9e5ed06591da7ea23eb65465e8d3187fe79f4d5ec3ae976d29a33b013e77a")
		.asCompatibleSubstituteFor("postgres");

	@Test
	void postgresqlOlderThanMinimumIsRefused() {
		try (PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_14)
			.withPassword("pg14-" + UUID.randomUUID())) {
			postgres.start();
			assertThatExceptionOfType(Exception.class)
				.isThrownBy(() -> start(postgres, postgres.getUsername(), postgres.getPassword()))
				.withStackTraceContaining("PostgreSQL 15 or later is required (REQ-156), the server runs major version 14");
		}
	}

	@Test
	void runtimeRoleWithUpdatePrivilegeIsRefused() throws SQLException {
		try (PostgreSQLContainer postgres = provisionedAndMigrated()) {
			asSuperuser(postgres, "GRANT UPDATE ON audit.events TO oea_app");
			assertThatExceptionOfType(Exception.class).isThrownBy(() -> startAsRuntimeRole(postgres))
				.withStackTraceContaining("runtime role oea_app holds privilege UPDATE on audit.events");
		}
	}

	@Test
	void runtimeRoleOwningAnAuditObjectIsRefused() throws SQLException {
		try (PostgreSQLContainer postgres = provisionedAndMigrated()) {
			asSuperuser(postgres, "ALTER TABLE audit.chain_head OWNER TO oea_app");
			assertThatExceptionOfType(Exception.class).isThrownBy(() -> startAsRuntimeRole(postgres))
				.withStackTraceContaining("runtime role oea_app owns audit.chain_head");
		}
	}

	@Test
	void developmentProfileOnlyWarnsAboutAuditPrivileges() throws SQLException {
		try (PostgreSQLContainer postgres = provisionedAndMigrated()) {
			asSuperuser(postgres, "GRANT UPDATE ON audit.events TO oea_app");
			try (ConfigurableApplicationContext context = start(postgres, "oea_app",
					OeaPostgres.ROLE_PASSWORDS.get("oea_app"), "--spring.profiles.active=dev")) {
				assertThat(context.isActive()).isTrue();
			}
		}
	}

	private static PostgreSQLContainer provisionedAndMigrated() {
		PostgreSQLContainer postgres = OeaPostgres.provisioned();
		postgres.start();
		assertThat(MigrateCommand.run(OeaTestDatabase.migrationArguments(postgres))).isZero();
		return postgres;
	}

	private static void asSuperuser(PostgreSQLContainer postgres, String sql) throws SQLException {
		try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()); Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static ConfigurableApplicationContext startAsRuntimeRole(PostgreSQLContainer postgres) {
		return start(postgres, "oea_app", OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
	}

	private static ConfigurableApplicationContext start(PostgreSQLContainer postgres, String username, String password,
			String... extra) {
		String[] base = { "--spring.datasource.url=" + postgres.getJdbcUrl(), "--spring.datasource.username=" + username,
				"--spring.datasource.password=" + password, "--spring.main.banner-mode=off" };
		String[] args = Stream.concat(Arrays.stream(base), Arrays.stream(extra))
			.toArray(String[]::new);
		return new SpringApplicationBuilder(OeaBackendApplication.class).web(WebApplicationType.NONE).run(args);
	}

}
