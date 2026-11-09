package io.oea.audit;

import io.oea.config.secrets.SecretCipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Encryption of the audit values of restricted properties (ADR-033 §3, REQ-160 AC4; design D4 of
 * us-126-role-restricted-enforcement): AES-256-GCM under an audit value key, a random data key
 * stored in {@code audit.value_keys} only wrapped by the key-encryption key of REQ-188. The
 * newest data key encrypts; older ones stay for decryption. Unwrapped keys are held in memory.
 */
@Component
public class AuditValueCipher {

	/** The additional authenticated data of a wrapped key, as {@code oea secrets-reencrypt} forms it. */
	static final String WRAP_CONTEXT = "audit.value_keys.wrapped_key:";

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int NONCE_BYTES = 12;

	private static final int TAG_BITS = 128;

	private static final int KEY_BYTES = 32;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final JdbcTemplate jdbc;

	private final SecretCipher wrapper;

	private final Map<String, SecretKey> unwrapped = new ConcurrentHashMap<>();

	public AuditValueCipher(JdbcTemplate jdbc, SecretCipher wrapper) {
		this.jdbc = jdbc;
		this.wrapper = wrapper;
	}

	/** An encrypted value and the ID of the audit value key. */
	public record Sealed(byte[] ciphertext, String keyId) {
	}

	/** The ID of the audit value key that encrypts new values; created on first use. */
	public synchronized String currentKeyId() {
		List<String> newest = this.jdbc.queryForList(
				"SELECT key_id FROM audit.value_keys ORDER BY created_at DESC, key_id DESC LIMIT 1", String.class);
		if (!newest.isEmpty()) {
			return newest.getFirst();
		}
		byte[] key = new byte[KEY_BYTES];
		RANDOM.nextBytes(key);
		byte[] suffix = new byte[6];
		RANDOM.nextBytes(suffix);
		String keyId = "avk-" + LocalDate.now(ZoneOffset.UTC) + "-" + HexFormat.of().formatHex(suffix);
		SecretCipher.Encrypted wrapped = this.wrapper.encrypt(Base64.getEncoder().encodeToString(key),
				WRAP_CONTEXT + keyId);
		this.jdbc.update("INSERT INTO audit.value_keys (key_id, wrapped_key, kek_id) VALUES (?, ?, ?)", keyId,
				wrapped.ciphertext(), wrapped.keyId());
		this.unwrapped.put(keyId, new SecretKeySpec(key, "AES"));
		return keyId;
	}

	/**
	 * Encrypts a value under the audit value key; {@code context} binds it to its place, e.g.
	 * {@code audit.event_changes:<event>:<property>:old}.
	 */
	public byte[] encrypt(String keyId, String value, String context) {
		byte[] nonce = new byte[NONCE_BYTES];
		RANDOM.nextBytes(nonce);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key(keyId), new GCMParameterSpec(TAG_BITS, nonce));
			cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
			byte[] sealed = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
			return ByteBuffer.allocate(NONCE_BYTES + sealed.length).put(nonce).put(sealed).array();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("audit value encryption failed", ex);
		}
	}

	/** Decrypts a value; fails if key, context or ciphertext do not match. */
	public String decrypt(String keyId, byte[] stored, String context) {
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key(keyId), new GCMParameterSpec(TAG_BITS, stored, 0, NONCE_BYTES));
			cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
			return new String(cipher.doFinal(stored, NONCE_BYTES, stored.length - NONCE_BYTES), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("audit value decryption failed", ex);
		}
	}

	/** The context of an old or new value of a change. */
	static String context(long eventId, Object propertyDefId, boolean old) {
		return "audit.event_changes:" + eventId + ":" + propertyDefId + ":" + (old ? "old" : "new");
	}

	/** Forgets the unwrapped keys, e.g. after a rotation in tests. */
	void forget() {
		this.unwrapped.clear();
	}

	private SecretKey key(String keyId) {
		return this.unwrapped.computeIfAbsent(keyId, (id) -> {
			Map<String, Object> row = this.jdbc
				.queryForMap("SELECT wrapped_key, kek_id FROM audit.value_keys WHERE key_id = ?", id);
			String plain = this.wrapper.decrypt(
					new SecretCipher.Encrypted((String) row.get("wrapped_key"), (String) row.get("kek_id")),
					WRAP_CONTEXT + id);
			return new SecretKeySpec(Base64.getDecoder().decode(plain), "AES");
		});
	}

}
