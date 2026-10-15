package io.oea.config.secrets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** The current and previous key-encryption keys, identified by key ID. */
public final class KeyRing {

	public static final int KEY_BYTES = 32;

	private final String currentKeyId;

	private final Map<String, SecretKey> keys;

	private KeyRing(String currentKeyId, Map<String, SecretKey> keys) {
		this.currentKeyId = currentKeyId;
		this.keys = Map.copyOf(keys);
	}

	/** Loads the keys of the properties; an unset current key yields an empty ring. */
	public static KeyRing load(KekProperties properties) {
		if (properties == null || properties.current() == null) {
			return new KeyRing(null, Map.of());
		}
		Map<String, SecretKey> keys = new LinkedHashMap<>();
		String currentId = put(keys, readKey(properties.current()));
		for (Path previous : properties.previous()) {
			put(keys, readKey(previous));
		}
		return new KeyRing(currentId, keys);
	}

	/** Reads one key file: Base64 of exactly 256 bits. */
	public static byte[] readKey(Path file) {
		try {
			byte[] key = Base64.getDecoder().decode(Files.readString(file, StandardCharsets.US_ASCII).strip());
			if (key.length != KEY_BYTES) {
				throw new IllegalArgumentException("key file " + file + " does not contain a 256-bit key");
			}
			return key;
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("key file " + file + " cannot be read", ex);
		}
	}

	/** Key ID: first 16 hex digits of the SHA-256 of the key. */
	public static String keyId(byte[] key) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key)).substring(0, 16);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public boolean isConfigured() {
		return this.currentKeyId != null;
	}

	public String currentKeyId() {
		if (this.currentKeyId == null) {
			throw new IllegalStateException("no key-encryption key configured (oea.security.kek.current)");
		}
		return this.currentKeyId;
	}

	SecretKey current() {
		return this.keys.get(currentKeyId());
	}

	Optional<SecretKey> byId(String keyId) {
		return Optional.ofNullable(this.keys.get(keyId));
	}

	private static String put(Map<String, SecretKey> keys, byte[] key) {
		String id = keyId(key);
		keys.put(id, new SecretKeySpec(key, "AES"));
		return id;
	}

}
