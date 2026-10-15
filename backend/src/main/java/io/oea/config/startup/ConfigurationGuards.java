package io.oea.config.startup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
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
 * web server exists. Outside the profile {@code dev}: G4 (secrets), G5 (CORS origins and the
 * public URL), G6 (Swagger UI). The issuer rules G1-G3 apply to the stored providers
 * ({@link StoredProviderGuard}).
 */
public class ConfigurationGuards implements EnvironmentPostProcessor, Ordered {

	/** Property set by the migration step; selects which secrets are required. */
	public static final String MODE_PROPERTY = "oea.mode";

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
		List<String> violations = new ArrayList<>();
		if (!development) {
			checkSecrets(environment, violations);
			checkCors(binder, violations);
			checkPublicUrl(environment, violations);
			checkSwaggerUi(environment, violations);
		}
		return violations;
	}

	/** G4: database passwords and the key-encryption key; stored client secrets are encrypted. */
	private static void checkSecrets(ConfigurableEnvironment environment, List<String> violations) {
		SecretRules rules = SecretRules.load();
		List<String> secretProperties = new ArrayList<>();
		if ("migrate".equals(environment.getProperty(MODE_PROPERTY))) {
			secretProperties.add("oea.migration.owner.password");
			secretProperties.add("oea.migration.reporting-owner.password");
		}
		else if ("audit-verify".equals(environment.getProperty(MODE_PROPERTY))) {
			secretProperties.add("oea.audit-verify.password");
		}
		else if (environment.containsProperty("spring.datasource.url")) {
			secretProperties.add("spring.datasource.password");
		}
		for (String property : secretProperties) {
			rules.check(environment.getProperty(property))
				.ifPresent((reason) -> violations.add("G4: " + property + " " + reason));
		}
		if (environment.containsProperty("spring.datasource.url")
				|| "secrets-reencrypt".equals(environment.getProperty(MODE_PROPERTY))) {
			checkKeyEncryptionKey(environment, rules, violations);
		}
	}

	/** G4 for the key-encryption key file (REQ-188): present, readable, 256 bit, no development key. */
	private static void checkKeyEncryptionKey(ConfigurableEnvironment environment, SecretRules rules,
			List<String> violations) {
		String property = "oea.security.kek.current";
		String path = environment.getProperty(property);
		if (path == null || path.isBlank()) {
			violations.add("G4: " + property + " is not set");
			return;
		}
		String content;
		try {
			content = Files.readString(Path.of(path), StandardCharsets.US_ASCII).strip();
		}
		catch (IOException | RuntimeException ex) {
			violations.add("G4: " + property + " names a file that cannot be read");
			return;
		}
		byte[] key;
		try {
			key = Base64.getDecoder().decode(content);
		}
		catch (IllegalArgumentException ex) {
			key = new byte[0];
		}
		if (key.length != 32) {
			violations.add("G4: " + property + " does not contain a Base64-encoded 256-bit key");
		}
		else if (rules.isKnownDevelopmentSecret(content)) {
			violations.add("G4: " + property + " is in the known-development-secrets list");
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

	/** G5: the public URL of the portal, base of the OIDC redirect URI, is https and not loopback. */
	private static void checkPublicUrl(ConfigurableEnvironment environment, List<String> violations) {
		String publicUrl = environment.getProperty(StoredProviderGuard.PUBLIC_URL);
		if (publicUrl == null || publicUrl.isBlank()) {
			return;
		}
		if (!publicUrl.strip().toLowerCase(Locale.ROOT).startsWith("https://")) {
			violations.add("G5: " + StoredProviderGuard.PUBLIC_URL + " is not an https URL");
		}
		else if (LoopbackAddresses.isLoopbackUrl(publicUrl)) {
			violations.add("G5: " + StoredProviderGuard.PUBLIC_URL + " is a loopback address");
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

}
