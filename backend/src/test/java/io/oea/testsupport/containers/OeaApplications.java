package io.oea.testsupport.containers;

import io.oea.OeaBackendApplication;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Starts the real application against a given test database, as the runtime role. */
public final class OeaApplications {

	private static Path sharedSetupTokenFile;

	private OeaApplications() {
	}

	/** Setup token file of the contexts on the shared test database. */
	public static synchronized String sharedSetupTokenFile() {
		if (sharedSetupTokenFile == null) {
			try {
				sharedSetupTokenFile = Files.createTempDirectory("oea-run").resolve("setup-token");
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
		return sharedSetupTokenFile.toString();
	}

	public static ConfigurableApplicationContext start(PostgreSQLContainer postgres, Path setupTokenFile,
			WebApplicationType webType, String... extra) {
		return start(postgres, setupTokenFile, webType, null, extra);
	}

	/** As {@link #start(PostgreSQLContainer, Path, WebApplicationType, String...)} with the clock as primary bean. */
	public static ConfigurableApplicationContext start(PostgreSQLContainer postgres, Path setupTokenFile,
			WebApplicationType webType, Clock clock, String... extra) {
		return start(postgres.getJdbcUrl(), setupTokenFile, webType, clock, extra);
	}

	public static ConfigurableApplicationContext start(OeaDatabase database, Path setupTokenFile,
			WebApplicationType webType, String... extra) {
		return start(database.getJdbcUrl(), setupTokenFile, webType, null, extra);
	}

	/** As {@link #start(OeaDatabase, Path, WebApplicationType, String...)} with the clock as primary bean. */
	public static ConfigurableApplicationContext start(OeaDatabase database, Path setupTokenFile,
			WebApplicationType webType, Clock clock, String... extra) {
		return start(database.getJdbcUrl(), setupTokenFile, webType, clock, extra);
	}

	private static ConfigurableApplicationContext start(String jdbcUrl, Path setupTokenFile,
			WebApplicationType webType, Clock clock, String... extra) {
		List<String> args = new ArrayList<>(List.of("--spring.datasource.url=" + jdbcUrl,
				"--spring.datasource.username=oea_app",
				"--spring.datasource.password=" + OeaPostgres.ROLE_PASSWORDS.get("oea_app"),
				"--oea.bootstrap.setup-token-file=" + setupTokenFile,
				"--spring.main.banner-mode=off"));
		if (Arrays.stream(extra).noneMatch((arg) -> arg.startsWith("--oea.security.kek.current="))) {
			args.add("--oea.security.kek.current=" + OeaTestKeys.sharedKeyFile());
		}
		if (webType == WebApplicationType.SERVLET) {
			args.add("--server.port=0");
		}
		args.addAll(List.of(extra));
		SpringApplicationBuilder builder = new SpringApplicationBuilder(OeaBackendApplication.class).web(webType);
		if (clock != null) {
			builder.initializers((ConfigurableApplicationContext context) -> ((GenericApplicationContext) context)
				.registerBean("testClock", Clock.class, () -> clock, (definition) -> definition.setPrimary(true)));
		}
		return builder.run(args.toArray(String[]::new));
	}

}
