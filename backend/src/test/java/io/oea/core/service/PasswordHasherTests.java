package io.oea.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** REQ-169: argon2id with the OWASP parameters, length 12–128. */
class PasswordHasherTests {

	private final PasswordHasher hasher = new PasswordHasher();

	@Test
	void hashesAreArgon2idPhcStringsWithOwaspParameters() {
		String hash = this.hasher.hash("correct horse battery");
		assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
		assertThat(this.hasher.matches("correct horse battery", hash)).isTrue();
		assertThat(this.hasher.matches("wrong horse battery", hash)).isFalse();
		assertThat(this.hasher.hash("correct horse battery")).as("salted").isNotEqualTo(hash);
	}

	@Test
	void lengthLimitsAreEnforced() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.hasher.hash("eleven-char"));
		assertThatIllegalArgumentException().isThrownBy(() -> this.hasher.hash("x".repeat(129)));
		assertThat(this.hasher.hash("twelve-chars")).startsWith("$argon2id$");
		assertThat(this.hasher.hash("x".repeat(128))).startsWith("$argon2id$");
	}

}
