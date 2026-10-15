package io.oea.app.bootstrapping;

import io.oea.core.domain.auth.BootstrapSetupToken;
import io.oea.core.domain.settings.SystemSettings;
import io.oea.core.repository.BootstrapSetupTokenRepository;
import io.oea.core.repository.SystemSettingsRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues the one-time setup token while the instance is not bootstrapped (design D1). Only
 * the SHA-256 hash is stored; the token goes to a file inside the container and never to
 * the log (REQ-017, REQ-175 AC3).
 */
@Service
public class SetupTokens {

	private static final Logger logger = LoggerFactory.getLogger(SetupTokens.class);

	private static final SecureRandom RANDOM = new SecureRandom();

	private final BootstrapSetupTokenRepository tokens;

	private final SystemSettingsRepository settings;

	private final BootstrapProperties properties;

	private final Clock clock;

	public SetupTokens(BootstrapSetupTokenRepository tokens, SystemSettingsRepository settings,
			BootstrapProperties properties, Clock clock) {
		this.tokens = tokens;
		this.settings = settings;
		this.properties = properties;
		this.clock = clock;
	}

	/** True while no bootstrapping has completed (the settings row is its marker). */
	@Transactional(readOnly = true)
	public boolean setupRequired() {
		return !this.settings.existsById(SystemSettings.SINGLE_ROW);
	}

	/** Issues a new token if setup is required; a previous token becomes invalid. */
	@Transactional
	public void issueIfRequired() {
		Path file = this.properties.setupTokenFile();
		if (!setupRequired()) {
			deleteFile(file);
			return;
		}
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
		Instant now = this.clock.instant();
		Instant expiresAt = now.plus(this.properties.setupTokenValidity());
		BootstrapSetupToken stored = this.tokens.findById(BootstrapSetupToken.SINGLE_ROW)
			.orElseGet(() -> new BootstrapSetupToken(hash(token), now, expiresAt));
		stored.replace(hash(token), now, expiresAt);
		this.tokens.save(stored);
		try {
			writeOwnerOnly(file, token);
			logger.warn("Setup required: no system admin account exists. Read the one-time setup token inside the "
					+ "backend container (e.g. 'docker compose exec backend cat {}') and open /setup. "
					+ "The token is valid until {}; a restart issues a new one.", file, expiresAt);
		}
		catch (IOException | UnsupportedOperationException ex) {
			logger.error("Setup required, but the setup token file {} cannot be written ({}). Set "
					+ "oea.bootstrap.setup-token-file to a writable location and restart.", file, ex.getMessage());
		}
	}

	/** Deletes the token file after a successful bootstrapping. */
	public void deleteFile() {
		deleteFile(this.properties.setupTokenFile());
	}

	static byte[] hash(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static byte[] randomBytes() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

	private static void writeOwnerOnly(Path file, String token) throws IOException {
		Path directory = file.toAbsolutePath().getParent();
		Files.createDirectories(directory);
		Path temp = Files.createTempFile(directory, ".setup-token", ".tmp",
				PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
		Files.writeString(temp, token + System.lineSeparator(), StandardCharsets.US_ASCII);
		Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static void deleteFile(Path file) {
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException ex) {
			logger.warn("Setup token file {} could not be deleted: {}", file, ex.getMessage());
		}
	}

}
