package io.oea.testsupport.containers;

import io.oea.OeaBackendApplication;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
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
		List<String> args = new ArrayList<>(List.of("--spring.datasource.url=" + postgres.getJdbcUrl(),
				"--spring.datasource.username=oea_app",
				"--spring.datasource.password=" + OeaPostgres.ROLE_PASSWORDS.get("oea_app"),
				"--oea.bootstrap.setup-token-file=" + setupTokenFile, "--spring.main.banner-mode=off"));
		if (webType == WebApplicationType.SERVLET) {
			args.add("--server.port=0");
		}
		args.addAll(List.of(extra));
		return new SpringApplicationBuilder(OeaBackendApplication.class).web(webType).run(args.toArray(String[]::new));
	}

}
