package io.oea.app.catalog;

import io.oea.core.catalog.CatalogQueryStore.KeyType;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cursors to the next page of a catalog query (design D3 of s9-catalog-query-performance): the
 * values of the sort keys of the last row of a page, with the fingerprint of the query and the
 * number of the page they lead to, as base64url of a compact JSON document followed by the first
 * 16 bytes of its HMAC-SHA256. The key is random per backend start, so a cursor survives no
 * restart; the client then falls back to {@code page}.
 */
@Component
public class CatalogCursors {

	/** The longest cursor the contract accepts. */
	public static final int MAX_LENGTH = 2000;

	private static final int MAC_LENGTH = 16;

	private static final String ALGORITHM = "HmacSHA256";

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final SecretKeySpec key;

	public CatalogCursors() {
		this(randomKey());
	}

	CatalogCursors(byte[] key) {
		this.key = new SecretKeySpec(key.clone(), ALGORITHM);
	}

	/**
	 * The fingerprint of a query: SHA-256 over its parts, each with its length, so that no two
	 * different lists of parts give the same input.
	 */
	public static String fingerprint(List<String> parts) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (String part : parts) {
				String text = (part != null) ? part : "";
				digest.update((((part != null) ? text.length() : -1) + ":" + text + ";").getBytes(StandardCharsets.UTF_8));
			}
			return ENCODER.encodeToString(digest.digest());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** The cursor to {@code page} of the query with the fingerprint, after a row with these key values. */
	public String encode(String fingerprint, int page, List<Object> values) {
		ObjectNode json = JsonMapper.shared().createObjectNode();
		json.put("f", fingerprint);
		json.put("p", page);
		ArrayNode keys = json.putArray("k");
		for (Object value : values) {
			if (value == null) {
				keys.addNull();
			}
			else {
				keys.add(value.toString());
			}
		}
		byte[] payload = JsonMapper.shared().writeValueAsBytes(json);
		byte[] token = Arrays.copyOf(payload, payload.length + MAC_LENGTH);
		System.arraycopy(mac(payload), 0, token, payload.length, MAC_LENGTH);
		return ENCODER.encodeToString(token);
	}

	/**
	 * The key values of a cursor, if it was issued by this backend for the query with the
	 * fingerprint and for {@code page}, and holds one value of the right type per key; empty
	 * otherwise. No value reaches the database before it parses as its key's type.
	 */
	public Optional<List<Object>> decode(String cursor, String fingerprint, int page, List<KeyType> types) {
		if (cursor == null || cursor.length() > MAX_LENGTH) {
			return Optional.empty();
		}
		byte[] token;
		try {
			token = Base64.getUrlDecoder().decode(cursor);
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
		if (token.length <= MAC_LENGTH) {
			return Optional.empty();
		}
		byte[] payload = Arrays.copyOf(token, token.length - MAC_LENGTH);
		byte[] mac = Arrays.copyOfRange(token, token.length - MAC_LENGTH, token.length);
		if (!MessageDigest.isEqual(mac, Arrays.copyOf(mac(payload), MAC_LENGTH))) {
			return Optional.empty();
		}
		JsonNode json;
		try {
			json = JsonMapper.shared().readTree(payload);
		}
		catch (JacksonException ex) {
			return Optional.empty();
		}
		JsonNode keys = json.path("k");
		if (!fingerprint.equals(json.path("f").asString(null)) || !json.path("p").isInt()
				|| json.path("p").asInt() != page || !keys.isArray() || keys.size() != types.size()) {
			return Optional.empty();
		}
		List<Object> values = new ArrayList<>(types.size());
		for (int i = 0; i < types.size(); i++) {
			JsonNode value = keys.get(i);
			if (value.isNull()) {
				values.add(null);
				continue;
			}
			if (!value.isString()) {
				return Optional.empty();
			}
			Object parsed = parse(types.get(i), value.asString());
			if (parsed == null) {
				return Optional.empty();
			}
			values.add(parsed);
		}
		return Optional.of(values);
	}

	/** The value of a key from its text form, {@code null} if it is not of the type. */
	static Object parse(KeyType type, String text) {
		try {
			return switch (type) {
				case TEXT -> text;
				case INTEGER -> Integer.valueOf(text);
				case BIGINT -> Long.valueOf(text);
				case DATE -> LocalDate.parse(text);
				case BOOLEAN -> "true".equals(text) ? Boolean.TRUE : "false".equals(text) ? Boolean.FALSE : null;
				case TIMESTAMP -> OffsetDateTime.parse(text);
			};
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private byte[] mac(byte[] payload) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(this.key);
			return mac.doFinal(payload);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static byte[] randomKey() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return key;
	}

}
