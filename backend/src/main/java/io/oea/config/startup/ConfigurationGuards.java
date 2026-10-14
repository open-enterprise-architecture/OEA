package io.oea.config.startup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.ClassUtils;

/**
 * Configuration guards of ADR-036 §6 on the prepared environment, before any bean or the
 * web server exists. Outside the profile {@code dev}: G4 (secrets), G5 (CORS), G6
 * (Swagger UI). With {@code dev}: every configured issuer must be a loopback address.
 * G1-G3 (issuer and signature rules) follow with OIDC login.
 */
public class ConfigurationGuards implements EnvironmentPostProcessor, Ordered {

	/** Property set by the migration step; selects which secrets are required. */
	public static final String MODE_PROPERTY = "oea.mode";

	static final String PROVIDERS = "oea.security.oidc.providers";

	static final String CORS_ORIGINS = "oea.security.cors.allowed-origins";

	private static final String SPRINGDOC_MARKER = "org.springdoc.core.configuration.SpringDocConfiguration";

	private final Log logger;

	public ConfigurationGuards(DeferredLogFactory logFactory) {
		this.logger = logFactory.getLog(ConfigurationGuards.class);
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		boolean development = environment.acceptsProfiles(Profiles.of("dev"));
		List<String> violations = check(environment, development);
		if (!violations.isEmpty()) {
			throw new StartupGuardException(violations);
		}
		if (development) {
			this.logger.warn("*** Spring profile 'dev' is active: development relaxations of ADR-036 §6 apply. "
					+ "Never use this profile outside a local development environment. ***");
		}
	}

	static List<String> check(ConfigurableEnvironment environment, boolean development) {
		Binder binder = Binder.get(environment);
		List<OidcProviderSettings> providers = binder.bind(PROVIDERS, Bindable.listOf(OidcProviderSettings.class))
			.orElse(List.of());
		List<String> violations = new ArrayList<>();
		if (development) {
			checkDevelopmentIssuers(providers, violations);
		}
		else {
			checkSecrets(environment, providers, violations);
			checkCors(binder, violations);
			checkSwaggerUi(environment, violations);
		}
		return violations;
	}

	/** Inverse guard (REQ-183 AC5): the development profile accepts only loopback issuers. */
	private static void checkDevelopmentIssuers(List<OidcProviderSettings> providers, List<String> violations) {
		for (int i = 0; i < providers.size(); i++) {
			String issuer = providers.get(i).issuer();
			if (issuer != null && !issuer.isBlank() && !LoopbackAddresses.isLoopbackUrl(issuer)) {
				violations.add(PROVIDERS + "[" + i + "].issuer is not a loopback address, which the profile 'dev' "
						+ "requires (REQ-183 AC5)");
			}
		}
	}

	/** G4: database passwords and OIDC client secrets. */
	private static void checkSecrets(ConfigurableEnvironment environment, List<OidcProviderSettings> providers,
			List<String> violations) {
		SecretRules rules = SecretRules.load();
		List<String> secretProperties = new ArrayList<>();
		if ("migrate".equals(environment.getProperty(MODE_PROPERTY))) {
			secretProperties.add("oea.migration.owner.password");
			secretProperties.add("oea.migration.reporting-owner.password");
		}
		else if (environment.containsProperty("spring.datasource.url")) {
			secretProperties.add("spring.datasource.password");
		}
		for (String property : secretProperties) {
			rules.check(environment.getProperty(property))
				.ifPresent((reason) -> violations.add("G4: " + property + " " + reason));
		}
		for (int i = 0; i < providers.size(); i++) {
			if (providers.get(i).clientSecret() != null) {
				String property = PROVIDERS + "[" + i + "].client-secret";
				rules.check(providers.get(i).clientSecret())
					.ifPresent((reason) -> violations.add("G4: " + property + " " + reason));
			}
		}
	}

	/** G5: no wildcard, {@code http} or loopback origin (SEC-24, REQ-187). */
	private static void checkCors(Binder binder, List<String> violations) {
		List<String> origins = binder.bind(CORS_ORIGINS, Bindable.listOf(String.class)).orElse(List.of());
		for (int i = 0; i < origins.size(); i++) {
			String origin = origins.get(i).strip();
			String property = CORS_ORIGINS + "[" + i + "]";
			if (origin.contains("*")) {
				violations.add("G5: " + property + " contains a wildcard");
			}
			else if (origin.toLowerCase(Locale.ROOT).startsWith("http:")) {
				violations.add("G5: " + property + " is an http origin; only https origins are allowed");
			}
			else if (LoopbackAddresses.isLoopbackUrl(origin)) {
				violations.add("G5: " + property + " is a loopback origin");
			}
		}
	}

	/** G6: Swagger UI and the OpenAPI UI endpoint stay off (ADR-013, REQ-183 AC3). */
	private static void checkSwaggerUi(ConfigurableEnvironment environment, List<String> violations) {
		boolean springdocPresent = ClassUtils.isPresent(SPRINGDOC_MARKER, ConfigurationGuards.class.getClassLoader());
		for (String property : List.of("springdoc.swagger-ui.enabled", "springdoc.api-docs.enabled")) {
			if (environment.getProperty(property, Boolean.class, springdocPresent)) {
				violations.add("G6: " + property + " is enabled; Swagger UI and API docs must be disabled");
			}
		}
	}

	/**
	 * One entry of {@code oea.security.oidc.providers}. Introduced for the guards (design
	 * D4); OIDC login uses the same property from change 3 on.
	 */
	record OidcProviderSettings(String issuer, String clientSecret) {
	}

}
