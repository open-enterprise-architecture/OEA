package io.oea.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.core.catalog.CatalogQueryStore.KeyType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Encoding and checks of the cursor (design D3 of s9-catalog-query-performance). */
class CatalogCursorsTests {

	private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

	private static final List<KeyType> TYPES = List.of(KeyType.TEXT, KeyType.INTEGER, KeyType.BIGINT, KeyType.DATE,
			KeyType.BOOLEAN, KeyType.TIMESTAMP);

	private static final List<Object> VALUES = Arrays.asList("crm system", 42, 7_000_000_000L,
			LocalDate.of(2026, 11, 7), true, OffsetDateTime.parse("2026-11-07T10:15:30.123456Z"));

	private final CatalogCursors cursors = new CatalogCursors(KEY);

	private final String fingerprint = CatalogCursors.fingerprint(List.of("catalog", "name:asc", "20"));

	@Test
	void roundTripOfEveryKeyType() {
		String cursor = this.cursors.encode(this.fingerprint, 1, VALUES);
		assertThat(this.cursors.decode(cursor, this.fingerprint, 1, TYPES)).contains(VALUES);
		assertThat(cursor).matches("[A-Za-z0-9_-]+");
	}

	@Test
	void emptyValuesStayEmpty() {
		List<Object> values = Arrays.asList(null, 1, null, null, null, null);
		String cursor = this.cursors.encode(this.fingerprint, 3, values);
		assertThat(this.cursors.decode(cursor, this.fingerprint, 3, TYPES)).contains(values);
	}

	@Test
	void anotherQueryOrPageIsRejected() {
		String cursor = this.cursors.encode(this.fingerprint, 1, VALUES);
		assertThat(this.cursors.decode(cursor, CatalogCursors.fingerprint(List.of("catalog", "name:desc", "20")), 1,
				TYPES)).isEmpty();
		assertThat(this.cursors.decode(cursor, this.fingerprint, 0, TYPES)).isEmpty();
		assertThat(this.cursors.decode(cursor, this.fingerprint, 2, TYPES)).isEmpty();
	}

	@Test
	void cursorOfAnotherBackendIsRejected() {
		String foreign = new CatalogCursors().encode(this.fingerprint, 1, VALUES);
		assertThat(this.cursors.decode(foreign, this.fingerprint, 1, TYPES)).isEmpty();
	}

	@Test
	void changedOrGarbageIsRejected() {
		String cursor = this.cursors.encode(this.fingerprint, 1, VALUES);
		byte[] token = Base64.getUrlDecoder().decode(cursor);
		token[10] ^= 1;
		assertThat(this.cursors.decode(encode(token), this.fingerprint, 1, TYPES)).isEmpty();
		assertThat(this.cursors.decode("not a cursor!", this.fingerprint, 1, TYPES)).isEmpty();
		assertThat(this.cursors.decode("", this.fingerprint, 1, TYPES)).isEmpty();
		assertThat(this.cursors.decode(encode(new byte[16]), this.fingerprint, 1, TYPES)).isEmpty();
	}

	@Test
	void signedButMalformedContentIsRejected() {
		// signed with the right key, so only the content checks reject them
		assertThat(decodeSigned("{\"f\":\"%s\",\"p\":1,\"k\":[\"a\",\"1\",\"2\",\"2026-11-07\",\"true\"]}"))
			.as("too few values").isFalse();
		assertThat(decodeSigned(
				"{\"f\":\"%s\",\"p\":1,\"k\":[\"a\",\"1\",\"2\",\"2026-11-07\",\"true\",\"2026-11-07T10:15:30Z\",\"x\"]}"))
			.as("too many values").isFalse();
		assertThat(decodeSigned(
				"{\"f\":\"%s\",\"p\":1,\"k\":[\"a\",\"one\",\"2\",\"2026-11-07\",\"true\",\"2026-11-07T10:15:30Z\"]}"))
			.as("a value of the wrong type").isFalse();
		assertThat(decodeSigned(
				"{\"f\":\"%s\",\"p\":1,\"k\":[\"a\",1,\"2\",\"2026-11-07\",\"true\",\"2026-11-07T10:15:30Z\"]}"))
			.as("a number instead of text").isFalse();
		assertThat(decodeSigned(
				"{\"f\":\"%s\",\"p\":1,\"k\":[\"a\",\"1\",\"2\",\"2026-11-07\",\"yes\",\"2026-11-07T10:15:30Z\"]}"))
			.as("a boolean that is not true or false").isFalse();
		assertThat(decodeSigned("{\"f\":\"%s\",\"p\":\"1\",\"k\":[]}")).as("page as text").isFalse();
		assertThat(decodeSigned("not json %s")).as("no JSON").isFalse();
		assertThat(decodeSigned(
				"{\"f\":\"%s\",\"p\":1,\"k\":[\"a\",\"1\",\"2\",\"2026-11-07\",\"true\",\"2026-11-07T10:15:30Z\"]}"))
			.as("the well-formed control").isTrue();
	}

	@Test
	void lengthAtItsLimit() {
		// the longest accepted cursor is decoded (and fails only its MAC), one more character is not read
		String longest = "A".repeat(CatalogCursors.MAX_LENGTH);
		String tooLong = "A".repeat(CatalogCursors.MAX_LENGTH + 1);
		byte[] payload = Base64.getUrlDecoder().decode(longest);
		assertThat(payload).hasSize(1500);
		assertThat(this.cursors.decode(longest, this.fingerprint, 1, TYPES)).isEmpty();
		String signed = this.cursors.encode(this.fingerprint, 1, Arrays.asList("x".repeat(1300), 1, 2L,
				LocalDate.of(2026, 1, 1), false, OffsetDateTime.parse("2026-01-01T00:00:00Z")));
		assertThat(signed.length()).isLessThanOrEqualTo(CatalogCursors.MAX_LENGTH);
		assertThat(this.cursors.decode(signed, this.fingerprint, 1, TYPES)).isPresent();
		assertThat(this.cursors.decode(tooLong, this.fingerprint, 1, TYPES)).isEmpty();
	}

	@Test
	void fingerprintSeparatesItsParts() {
		assertThat(CatalogCursors.fingerprint(List.of("ab", "c"))).isNotEqualTo(CatalogCursors.fingerprint(List.of("a", "bc")));
		assertThat(CatalogCursors.fingerprint(List.of("a", "b"))).isEqualTo(CatalogCursors.fingerprint(List.of("a", "b")));
	}

	private boolean decodeSigned(String template) {
		byte[] payload = template.formatted(this.fingerprint).getBytes(StandardCharsets.UTF_8);
		byte[] mac;
		try {
			javax.crypto.Mac hmac = javax.crypto.Mac.getInstance("HmacSHA256");
			hmac.init(new javax.crypto.spec.SecretKeySpec(KEY, "HmacSHA256"));
			mac = hmac.doFinal(payload);
		}
		catch (java.security.GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
		byte[] token = Arrays.copyOf(payload, payload.length + 16);
		System.arraycopy(mac, 0, token, payload.length, 16);
		return this.cursors.decode(encode(token), this.fingerprint, 1, TYPES).isPresent();
	}

	private static String encode(byte[] token) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
	}

}
