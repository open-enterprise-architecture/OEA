package io.oea.config.startup;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Guard G4: rejects empty, short, generic weak and known development secrets. The
 * known-development-secrets list contains only SHA-256 hashes (ADR-036 §6).
 */
final class SecretRules {

	static final int MINIMUM_LENGTH = 16;

	static final String KNOWN_DEV_SECRETS = "classpath*:security/known-dev-secrets.sha256";

	private static final Set<String> WEAK_VALUES = Set.of("changeme", "change-me", "change_me", "password", "passw0rd",
			"secret", "admin", "administrator", "postgres", "oea", "root", "test", "default", "123456", "12345678",
			"letmein", "keycloak");

	private static final String[] WEAK_MARKERS = { "dev-only", "test-only", "changeme", "change-me" };

	private final Set<String> knownDevHashes;

	private SecretRules(Set<String> knownDevHashes) {
		this.knownDevHashes = knownDevHashes;
	}

	static SecretRules load() {
		Set<String> hashes = new HashSet<>();
		try {
			for (Resource resource : new PathMatchingResourcePatternResolver().getResources(KNOWN_DEV_SECRETS)) {
				try (InputStream in = resource.getInputStream()) {
					new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
						.map(String::strip)
						.filter((line) -> !line.isEmpty() && !line.startsWith("#"))
						.map((line) -> line.toLowerCase(Locale.ROOT))
						.forEach(hashes::add);
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Known-development-secrets list cannot be read", ex);
		}
		return new SecretRules(Set.copyOf(hashes));
	}

	/** Returns the reason why the value is unsafe, without the value itself. */
	Optional<String> check(String value) {
		if (value == null || value.isBlank()) {
			return Optional.of("is empty");
		}
		if (this.knownDevHashes.contains(sha256(value))) {
			return Optional.of("is in the known-development-secrets list");
		}
		String normalized = value.strip().toLowerCase(Locale.ROOT);
		if (WEAK_VALUES.contains(normalized)) {
			return Optional.of("is a known default value");
		}
		for (String marker : WEAK_MARKERS) {
			if (normalized.contains(marker)) {
				return Optional.of("is marked as a development or default value");
			}
		}
		if (value.length() < MINIMUM_LENGTH) {
			return Optional.of("is shorter than " + MINIMUM_LENGTH + " characters");
		}
		return Optional.empty();
	}

	static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
