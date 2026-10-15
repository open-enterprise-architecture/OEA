package io.oea.config.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** REQ-188: AES-256-GCM with external keys, bound to the place of the value. */
class SecretCipherTests {

	@TempDir
	Path dir;

	@Test
	void roundTripWithTheCurrentKey() throws Exception {
		SecretCipher cipher = new SecretCipher(KeyRing.load(new KekProperties(key("a"), null)));
		SecretCipher.Encrypted encrypted = cipher.encrypt("JBSWY3DPEHPK3PXP", "totp_credentials.secret:1");
		assertThat(encrypted.ciphertext()).doesNotContain("JBSWY3DPEHPK3PXP");
		assertThat(cipher.decrypt(encrypted, "totp_credentials.secret:1")).isEqualTo("JBSWY3DPEHPK3PXP");
		assertThat(cipher.encrypt("JBSWY3DPEHPK3PXP", "totp_credentials.secret:1").ciphertext())
			.as("random nonce")
			.isNotEqualTo(encrypted.ciphertext());
	}

	@Test
	void valuesCannotBeMovedToAnotherRow() throws Exception {
		SecretCipher cipher = new SecretCipher(KeyRing.load(new KekProperties(key("a"), null)));
		SecretCipher.Encrypted encrypted = cipher.encrypt("secret", "auth_providers.client_secret:1");
		assertThatIllegalStateException().isThrownBy(() -> cipher.decrypt(encrypted, "auth_providers.client_secret:2"));
	}

	@Test
	void tamperedValuesAndUnknownKeysAreRejected() throws Exception {
		SecretCipher cipher = new SecretCipher(KeyRing.load(new KekProperties(key("a"), null)));
		SecretCipher.Encrypted encrypted = cipher.encrypt("secret", "ctx");
		byte[] raw = Base64.getDecoder().decode(encrypted.ciphertext());
		raw[raw.length - 1] ^= 1;
		SecretCipher.Encrypted tampered = new SecretCipher.Encrypted(Base64.getEncoder().encodeToString(raw),
				encrypted.keyId());
		assertThatIllegalStateException().isThrownBy(() -> cipher.decrypt(tampered, "ctx"));

		SecretCipher other = new SecretCipher(KeyRing.load(new KekProperties(key("b"), null)));
		assertThatIllegalStateException().isThrownBy(() -> other.decrypt(encrypted, "ctx"))
			.withMessageContaining("is not configured");
	}

	@Test
	void previousKeysDecryptAndAreMarkedForReencryption() throws Exception {
		Path oldKey = key("old");
		SecretCipher old = new SecretCipher(KeyRing.load(new KekProperties(oldKey, null)));
		SecretCipher.Encrypted encrypted = old.encrypt("secret", "ctx");
		SecretCipher rotated = new SecretCipher(KeyRing.load(new KekProperties(key("new"), List.of(oldKey))));
		assertThat(rotated.decrypt(encrypted, "ctx")).isEqualTo("secret");
		assertThat(rotated.needsReencryption(encrypted)).isTrue();
		assertThat(rotated.needsReencryption(rotated.encrypt("secret", "ctx"))).isFalse();
	}

	@Test
	void keyFilesMustHold256Bits() throws Exception {
		Path shortKey = this.dir.resolve("short.key");
		Files.writeString(shortKey, Base64.getEncoder().encodeToString(new byte[16]));
		assertThatIllegalArgumentException().isThrownBy(() -> KeyRing.load(new KekProperties(shortKey, null)))
			.withMessageContaining("256-bit");
	}

	private Path key(String name) throws Exception {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		Path file = this.dir.resolve(name + ".key");
		Files.writeString(file, Base64.getEncoder().encodeToString(key) + "\n");
		return file;
	}

}
