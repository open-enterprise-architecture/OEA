package io.oea.audit.verify;

import io.oea.config.startup.ConfigurationGuards;
import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * {@code oea audit verify [--checkpoints=<file>]}: verifies the audit hash chain with the
 * read-only auditor role. Exit codes: 0 intact, 1 broken or behind a checkpoint, 2 usage
 * or connection error.
 */
public final class AuditVerifyCommand {

	private static final String CHECKPOINTS = "--checkpoints=";

	private AuditVerifyCommand() {
	}

	public static int run(String... args) {
		return run(System.out, args);
	}

	public static int run(PrintStream out, String... args) {
		List<String> springArgs = new ArrayList<>();
		Path checkpointFile = null;
		for (String arg : args) {
			if (arg.startsWith(CHECKPOINTS)) {
				checkpointFile = Path.of(arg.substring(CHECKPOINTS.length()));
			}
			else {
				springArgs.add(arg);
			}
		}
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(VerifyConfiguration.class)
			.web(WebApplicationType.NONE)
			.bannerMode(Banner.Mode.OFF)
			.logStartupInfo(false)
			.properties(ConfigurationGuards.MODE_PROPERTY + "=audit-verify")
			.run(springArgs.toArray(String[]::new))) {
			AuditVerifyProperties properties = context.getBean(AuditVerifyProperties.class);
			if (properties.url() == null || properties.url().isBlank()) {
				out.println("ERROR oea.audit-verify.url is not set");
				return 2;
			}
			List<Checkpoint> checkpoints = (checkpointFile != null) ? Checkpoint.read(checkpointFile) : List.of();
			VerificationResult result;
			try (Connection connection = DriverManager.getConnection(properties.url(), properties.username(),
					properties.password())) {
				result = new ChainVerifier().verify(connection, checkpoints);
			}
			if (result.intact()) {
				out.println("OK " + result.links() + " links, head " + result.headSeq() + " " + result.headHash()
						+ ((checkpointFile != null) ? ", " + checkpoints.size() + " checkpoint(s) consistent" : ""));
				return 0;
			}
			result.problems().forEach((problem) -> out.println("BROKEN " + problem));
			return 1;
		}
		catch (Exception ex) {
			out.println("ERROR " + ex.getMessage());
			return 2;
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(AuditVerifyProperties.class)
	static class VerifyConfiguration {

	}

}
