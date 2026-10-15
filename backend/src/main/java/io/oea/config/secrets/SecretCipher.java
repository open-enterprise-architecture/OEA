package io.oea.config.secrets;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM encryption of stored secrets (REQ-188). The additional authenticated data
 * binds a ciphertext to its place (table, column, row), so ciphertexts cannot be swapped
 * between rows. Stored form: Base64 of nonce (12 bytes) || ciphertext || tag (16 bytes).
 */
public class SecretCipher {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int NONCE_BYTES = 12;

	private static final int TAG_BITS = 128;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final KeyRing keys;

	public SecretCipher(KeyRing keys) {
		this.keys = keys;
	}

	/** Encrypted value and the ID of the key that encrypted it. */
	public record Encrypted(String ciphertext, String keyId) {
	}

	public Encrypted encrypt(String plaintext, String context) {
		byte[] nonce = new byte[NONCE_BYTES];
		RANDOM.nextBytes(nonce);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, this.keys.current(), new GCMParameterSpec(TAG_BITS, nonce));
			cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
			byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			byte[] stored = ByteBuffer.allocate(NONCE_BYTES + sealed.length).put(nonce).put(sealed).array();
			return new Encrypted(Base64.getEncoder().encodeToString(stored), this.keys.currentKeyId());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("encryption failed", ex);
		}
	}

	public String decrypt(Encrypted value, String context) {
		SecretKey key = this.keys.byId(value.keyId())
			.orElseThrow(() -> new IllegalStateException("key " + value.keyId() + " is not configured"));
		byte[] stored = Base64.getDecoder().decode(value.ciphertext());
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, NONCE_BYTES));
			cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
			return new String(cipher.doFinal(stored, NONCE_BYTES, stored.length - NONCE_BYTES), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("decryption failed: wrong key, context or tampered value", ex);
		}
	}

	/** True if the value was encrypted with a key other than the current one. */
	public boolean needsReencryption(Encrypted value) {
		return !this.keys.currentKeyId().equals(value.keyId());
	}

}
