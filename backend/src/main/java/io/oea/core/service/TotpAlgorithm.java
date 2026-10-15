package io.oea.core.service;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP with SHA-1, 6 digits and 30-second steps; codes of the previous and the next
 * step are accepted to tolerate clock drift (design D7).
 */
public final class TotpAlgorithm {

	public static final int PERIOD_SECONDS = 30;

	private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

	private static final SecureRandom RANDOM = new SecureRandom();

	private TotpAlgorithm() {
	}

	/** New 160-bit secret, Base32 encoded (as shown to the user and stored encrypted). */
	public static String newSecret() {
		byte[] secret = new byte[20];
		RANDOM.nextBytes(secret);
		return encodeBase32(secret);
	}

	/** The time step whose code matches, or empty. */
	public static OptionalLong matchingStep(String base32Secret, String code, Instant now) {
		if (code == null || !code.matches("[0-9]{6}")) {
			return OptionalLong.empty();
		}
		long current = now.getEpochSecond() / PERIOD_SECONDS;
		for (long step = current - 1; step <= current + 1; step++) {
			if (code(base32Secret, step).equals(code)) {
				return OptionalLong.of(step);
			}
		}
		return OptionalLong.empty();
	}

	public static String code(String base32Secret, long step) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(decodeBase32(base32Secret), "HmacSHA1"));
			byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
			int offset = hash[hash.length - 1] & 0x0f;
			int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
					| ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
			return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static String encodeBase32(byte[] data) {
		StringBuilder out = new StringBuilder();
		int buffer = 0;
		int bits = 0;
		for (byte b : data) {
			buffer = (buffer << 8) | (b & 0xff);
			bits += 8;
			while (bits >= 5) {
				out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
				bits -= 5;
			}
		}
		if (bits > 0) {
			out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
		}
		return out.toString();
	}

	static byte[] decodeBase32(String value) {
		String input = value.replace("=", "").toUpperCase(Locale.ROOT);
		ByteBuffer out = ByteBuffer.allocate(input.length() * 5 / 8);
		int buffer = 0;
		int bits = 0;
		for (char c : input.toCharArray()) {
			int index = BASE32.indexOf(c);
			if (index < 0) {
				throw new IllegalArgumentException("invalid Base32 character");
			}
			buffer = (buffer << 5) | index;
			bits += 5;
			if (bits >= 8) {
				out.put((byte) (buffer >> (bits - 8)));
				bits -= 8;
			}
		}
		return out.array();
	}

}
