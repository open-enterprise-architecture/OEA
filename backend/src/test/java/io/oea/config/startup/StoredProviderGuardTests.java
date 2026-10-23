package io.oea.config.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.oea.config.startup.StoredProviderGuard.StoredProvider;
import io.oea.testsupport.containers.OeaApplications;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.NestedExceptionUtils;

/** Spec platform/startup-safety: issuer guards on the stored providers (ADR-036 §6 G2, G3). */
class StoredProviderGuardTests {

	private static final String PUBLIC_URL = "https://oea.example.org";

	private static OeaDatabase postgres;

	@BeforeAll
	static void start() {
		postgres = OeaDatabases.freshMigrated();
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void unsafeIssuersOfEnabledProvidersAreRefused() {
		List<StoredProvider> providers = List.of(new StoredProvider("1", "Plain", "http://idp.example.org", true),
				new StoredProvider("2", "Local", "https://localhost/realms/x", true),
				new StoredProvider("3", "Corporate", "https://login.example.com/tenant/v2.0", true));
		assertThat(StoredProviderGuard.check(providers, false, PUBLIC_URL)).containsExactly(
				"G3: auth provider 'Plain' (1): issuer http://idp.example.org does not use https",
				"G2: auth provider 'Local' (2): issuer https://localhost/realms/x is a loopback address");
	}

	@Test
	void disabledProvidersAreIgnoredOutsideDevelopment() {
		assertThat(StoredProviderGuard.check(
				List.of(new StoredProvider("1", "Plain", "http://idp.example.org", false)), false, null))
			.isEmpty();
	}

	@Test
	void enabledProviderNeedsThePublicUrl() {
		assertThat(StoredProviderGuard.check(
				List.of(new StoredProvider("1", "Corporate", "https://login.example.com", true)), false, " "))
			.containsExactly("oea.web.public-url is not set; the OIDC login needs it for the redirect URI");
	}

	@Test
	void developmentAcceptsOnlyLoopbackIssuers() {
		List<StoredProvider> providers = List.of(
				new StoredProvider("1", "Keycloak", "http://localhost:8180/realms/oea-dev", true),
				new StoredProvider("2", "Real", "https://login.example.com", false));
		assertThat(StoredProviderGuard.check(providers, true, "http://localhost:5173")).containsExactly(
				"auth provider 'Real' (2): issuer https://login.example.com is not a loopback address, "
						+ "which the profile 'dev' requires (REQ-183 AC5)");
	}

	@Test
	void applicationRefusesToStartWithAStoredUnsafeProvider() throws Exception {
		superuser("DELETE FROM auth_providers");
		superuser("""
				INSERT INTO auth_providers (id, name, issuer, client_id)
				VALUES ('00000000-0000-0000-0000-0000000000b1', 'Plain', 'http://idp.example.org', 'oea')""");
		Throwable failure = catchThrowable(() -> startApplication().close());
		assertThat(NestedExceptionUtils.getMostSpecificCause(failure)).isInstanceOf(StartupGuardException.class)
			.hasMessageContaining("G3: auth provider 'Plain'");

		superuser("UPDATE auth_providers SET active = false");
		assertThatNoException().isThrownBy(() -> startApplication().close());
	}

	private static org.springframework.context.ConfigurableApplicationContext startApplication() throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-guard").resolve("setup-token");
		return OeaApplications.start(postgres, tokenFile, WebApplicationType.SERVLET, "--oea.web.public-url=" + PUBLIC_URL);
	}

	private static void superuser(String sql) throws Exception {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			statement.executeUpdate(sql);
		}
	}

}
