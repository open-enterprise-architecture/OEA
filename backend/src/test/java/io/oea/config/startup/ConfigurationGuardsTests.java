package io.oea.config.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.oea.testsupport.containers.OeaTestKeys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/** Spec platform/startup-safety: configuration guards G4-G6 (stored issuers: StoredProviderGuardTests). */
@ExtendWith(OutputCaptureExtension.class)
class ConfigurationGuardsTests {

	private static final String DATASOURCE_URL = "--spring.datasource.url=jdbc:postgresql://db.example.org/oea";

	private static final String LISTED_DEV_SECRET = "listed-development-secret-for-guard-test";

	private static final String STRONG_SECRET = "Xq7#vR2!mK9@pL4$wN8%";

	@Test
	void developmentSecretInProductionIsRefusedWithoutItsValue(CapturedOutput output) {
		StartupGuardException ex = startFails(DATASOURCE_URL, "--spring.datasource.password=" + LISTED_DEV_SECRET);
		assertThat(ex.getViolations())
			.containsExactly("G4: spring.datasource.password is in the known-development-secrets list");
		assertThat(ex.getMessage()).doesNotContain(LISTED_DEV_SECRET);
		assertThat(output).doesNotContain(LISTED_DEV_SECRET);
	}

	@Test
	void emptyShortAndDefaultPasswordsAreRefused(CapturedOutput output) {
		assertThat(startFails(DATASOURCE_URL, "--spring.datasource.password=").getViolations())
			.containsExactly("G4: spring.datasource.password is empty");
		assertThat(startFails(DATASOURCE_URL, "--spring.datasource.password=Sh0rt!pw").getViolations())
			.containsExactly("G4: spring.datasource.password is shorter than 16 characters");
		assertThat(startFails(DATASOURCE_URL, "--spring.datasource.password=changeme").getViolations())
			.containsExactly("G4: spring.datasource.password is a known default value");
		assertThat(startFails(DATASOURCE_URL, "--spring.datasource.password=unlisted-value-dev-only").getViolations())
			.containsExactly("G4: spring.datasource.password is marked as a development or default value");
		assertThat(output).doesNotContain("Sh0rt!pw").doesNotContain("unlisted-value-dev-only");
	}

	@Test
	void migrationStepChecksTheOwnerPasswords() {
		assertThat(startFails("--oea.mode=migrate", "--oea.migration.owner.password=" + LISTED_DEV_SECRET,
				"--oea.migration.reporting-owner.password=" + STRONG_SECRET)
			.getViolations()).containsExactly("G4: oea.migration.owner.password is in the known-development-secrets list");
	}

	@Test
	void unsafePublicUrlIsRefused() {
		assertThat(startFails("--oea.web.public-url=http://oea.example.org").getViolations())
			.containsExactly("G5: oea.web.public-url is not an https URL");
		assertThat(startFails("--oea.web.public-url=https://localhost:5173").getViolations())
			.containsExactly("G5: oea.web.public-url is a loopback address");
	}

	@Test
	void unsafeCorsOriginsAreRefused() {
		assertThat(startFails("--oea.security.cors.allowed-origins=*,http://app.example.com,https://localhost:5173,"
				+ "https://127.0.0.1").getViolations())
			.containsExactly("G5: oea.security.cors.allowed-origins[0] contains a wildcard",
					"G5: oea.security.cors.allowed-origins[1] is an http origin; only https origins are allowed",
					"G5: oea.security.cors.allowed-origins[2] is a loopback origin",
					"G5: oea.security.cors.allowed-origins[3] is a loopback origin");
	}

	@Test
	void keyEncryptionKeyIsChecked(@TempDir Path dir) throws Exception {
		String password = "--spring.datasource.password=" + STRONG_SECRET;
		Path missing = dir.resolve("missing.key");
		assertThat(startFails(DATASOURCE_URL, password, "--oea.security.kek.current=" + missing).getViolations())
			.containsExactly("G4: oea.security.kek.current names a file that cannot be read");
		Path shortKey = Files.writeString(dir.resolve("short.key"), Base64.getEncoder().encodeToString(new byte[16]));
		assertThat(startFails(DATASOURCE_URL, password, "--oea.security.kek.current=" + shortKey).getViolations())
			.containsExactly("G4: oea.security.kek.current does not contain a Base64-encoded 256-bit key");
		Path devKey = Path.of("..", "dev", "secrets", "oea-kek-dev-only.key");
		assertThat(startFails(DATASOURCE_URL, password, "--oea.security.kek.current=" + devKey).getViolations())
			.containsExactly("G4: oea.security.kek.current is in the known-development-secrets list");
	}

	@Test
	void missingKeyIsRefusedWithoutValue(CapturedOutput output) {
		List<String> violations = startFails(DATASOURCE_URL, "--spring.datasource.password=" + STRONG_SECRET,
				"--oea.security.kek.current=").getViolations();
		assertThat(violations).containsExactly("G4: oea.security.kek.current is not set");
	}

	@Test
	void swaggerUiIsRefused() {
		assertThat(startFails("--springdoc.swagger-ui.enabled=true").getViolations())
			.containsExactly("G6: springdoc.swagger-ui.enabled is enabled; Swagger UI and API docs must be disabled");
	}

	@Test
	void safeProductionSettingsStart() {
		assertThatNoException().isThrownBy(() -> start(DATASOURCE_URL, "--spring.datasource.password=" + STRONG_SECRET,
				"--oea.security.cors.allowed-origins=https://oea.example.org",
				"--oea.web.public-url=https://oea.example.org").close());
	}

	@Test
	void developmentProfileStartsWithRelaxations(CapturedOutput output) {
		assertThatNoException().isThrownBy(() -> start("--spring.profiles.active=dev",
				"--oea.web.public-url=http://localhost:5173", "--spring.datasource.url=jdbc:postgresql://localhost:55432/oea",
				"--spring.datasource.password=" + LISTED_DEV_SECRET, "--oea.security.cors.allowed-origins=http://localhost:5173")
			.close());
		assertThat(output).contains("Spring profile 'dev' is active");
	}

	private static ConfigurableApplicationContext start(String... args) {
		List<String> arguments = new ArrayList<>(List.of(args));
		if (arguments.stream().noneMatch((arg) -> arg.startsWith("--oea.security.kek.current="))) {
			arguments.add("--oea.security.kek.current=" + OeaTestKeys.sharedKeyFile());
		}
		arguments.add("--spring.main.banner-mode=off");
		return new SpringApplicationBuilder(EmptyConfiguration.class).web(WebApplicationType.NONE)
			.run(arguments.toArray(String[]::new));
	}

	private static StartupGuardException startFails(String... args) {
		try (ConfigurableApplicationContext context = start(args)) {
			throw new AssertionError("Startup was expected to fail");
		}
		catch (StartupGuardException ex) {
			return ex;
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class EmptyConfiguration {

	}

}
