package io.oea.core.service;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * argon2id with the OWASP-recommended minimum parameters (REQ-169): m = 19 MiB, t = 2,
 * p = 1, 16-byte salt, 32-byte hash. Produces PHC strings ({@code $argon2id$v=19$m=19456,t=2,p=1$…}).
 */
@Component
public class PasswordHasher {

	public static final int MINIMUM_LENGTH = 12;

	public static final int MAXIMUM_LENGTH = 128;

	private static final int SALT_LENGTH = 16;

	private static final int HASH_LENGTH = 32;

	private static final int PARALLELISM = 1;

	private static final int MEMORY_KIB = 19456;

	private static final int ITERATIONS = 2;

	private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM,
			MEMORY_KIB, ITERATIONS);

	public String hash(String password) {
		if (password == null || password.length() < MINIMUM_LENGTH || password.length() > MAXIMUM_LENGTH) {
			throw new IllegalArgumentException("password length must be between " + MINIMUM_LENGTH + " and "
					+ MAXIMUM_LENGTH + " characters");
		}
		return this.encoder.encode(password);
	}

	public boolean matches(String password, String hash) {
		return this.encoder.matches(password, hash);
	}

	/** True if the hash uses outdated parameters and must be recomputed at the next login (REQ-169). */
	public boolean needsRehash(String hash) {
		return this.encoder.upgradeEncoding(hash);
	}

}
