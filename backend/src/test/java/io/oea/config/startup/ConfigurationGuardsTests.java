package io.oea.config.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/** Spec platform/startup-safety: configuration guards G4-G6 and the inverse development guard. */
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
		assertThat(startFails(DATASOURCE_URL, "--spring.datasource.password=oea-app-dev-only").getViolations())
			.containsExactly("G4: spring.datasource.password is marked as a development or default value");
		assertThat(output).doesNotContain("Sh0rt!pw").doesNotContain("oea-app-dev-only");
	}

	@Test
	void migrationStepChecksTheOwnerPasswords() {
		assertThat(startFails("--oea.mode=migrate", "--oea.migration.owner.password=" + LISTED_DEV_SECRET,
				"--oea.migration.reporting-owner.password=" + STRONG_SECRET)
			.getViolations()).containsExactly("G4: oea.migration.owner.password is in the known-development-secrets list");
	}

	@Test
	void oidcClientSecretIsChecked() {
		assertThat(startFails("--oea.security.oidc.providers[0].issuer=https://login.example.com",
				"--oea.security.oidc.providers[0].client-secret=secret")
			.getViolations()).containsExactly("G4: oea.security.oidc.providers[0].client-secret is a known default value");
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
	void swaggerUiIsRefused() {
		assertThat(startFails("--springdoc.swagger-ui.enabled=true").getViolations())
			.containsExactly("G6: springdoc.swagger-ui.enabled is enabled; Swagger UI and API docs must be disabled");
	}

	@Test
	void safeProductionSettingsStart() {
		assertThatNoException().isThrownBy(() -> start(DATASOURCE_URL, "--spring.datasource.password=" + STRONG_SECRET,
				"--oea.security.cors.allowed-origins=https://oea.example.org",
				"--oea.security.oidc.providers[0].issuer=https://login.example.com",
				"--oea.security.oidc.providers[0].client-secret=" + STRONG_SECRET).close());
	}

	@Test
	void developmentProfileWithRealIssuerIsRefused() {
		assertThat(startFails("--spring.profiles.active=dev",
				"--oea.security.oidc.providers[0].issuer=http://localhost:8180/realms/oea-dev",
				"--oea.security.oidc.providers[1].issuer=https://login.example.com")
			.getViolations()).containsExactly("oea.security.oidc.providers[1].issuer is not a loopback address, "
					+ "which the profile 'dev' requires (REQ-183 AC5)");
	}

	@Test
	void developmentProfileWithLoopbackIssuersStartsWithRelaxations(CapturedOutput output) {
		assertThatNoException().isThrownBy(() -> start("--spring.profiles.active=dev",
				"--oea.security.oidc.providers[0].issuer=http://localhost:8180/realms/oea-dev",
				"--oea.security.oidc.providers[1].issuer=http://127.0.0.1:8180/realms/oea-dev",
				"--spring.datasource.url=jdbc:postgresql://localhost:55432/oea",
				"--spring.datasource.password=" + LISTED_DEV_SECRET, "--oea.security.cors.allowed-origins=http://localhost:5173")
			.close());
		assertThat(output).contains("Spring profile 'dev' is active");
	}

	private static ConfigurableApplicationContext start(String... args) {
		List<String> arguments = new ArrayList<>(List.of(args));
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
