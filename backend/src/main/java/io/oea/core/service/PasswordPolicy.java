package io.oea.core.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Effective password policy (REQ-028; design D1, D3 of us-027-password-administration). Special
 * characters are the printable ASCII characters that are neither letters nor digits.
 * @param minLength minimum length, 12 to 128
 * @param expiryDays days after which a self-set password must be changed, 0 = no expiry
 * @param historyCount number of previous passwords, including the current one, that must not be
 * reused
 */
public record PasswordPolicy(int minLength, boolean requireUppercase, boolean requireLowercase, boolean requireDigits,
		boolean requireSpecialChars, int expiryDays, int historyCount) {

	public static final String MIN_LENGTH = "minLength";

	public static final String MAX_LENGTH = "maxLength";

	public static final String REQUIRE_UPPERCASE = "requireUppercase";

	public static final String REQUIRE_LOWERCASE = "requireLowercase";

	public static final String REQUIRE_DIGITS = "requireDigits";

	public static final String REQUIRE_SPECIAL_CHARS = "requireSpecialChars";

	public static final String HISTORY_COUNT = "historyCount";

	/** The defaults of REQ-028 AC5 within REQ-169: 12 characters, every other rule off. */
	public static final PasswordPolicy DEFAULT = new PasswordPolicy(PasswordHasher.MINIMUM_LENGTH, false, false, false,
			false, 0, 0);

	/** Number of required character types. */
	public int requiredTypes() {
		return (this.requireUppercase ? 1 : 0) + (this.requireLowercase ? 1 : 0) + (this.requireDigits ? 1 : 0)
				+ (this.requireSpecialChars ? 1 : 0);
	}

	/** Whether a password can satisfy the policy (REQ-028 AC4). */
	public boolean satisfiable() {
		return this.minLength >= requiredTypes() && this.minLength <= PasswordHasher.MAXIMUM_LENGTH;
	}

	/** The rules a password does not meet, without the history; empty if it meets all. */
	public List<String> violations(String password) {
		List<String> unmet = new ArrayList<>();
		String value = (password != null) ? password : "";
		if (value.length() < Math.max(this.minLength, PasswordHasher.MINIMUM_LENGTH)) {
			unmet.add(MIN_LENGTH);
		}
		if (value.length() > PasswordHasher.MAXIMUM_LENGTH) {
			unmet.add(MAX_LENGTH);
		}
		if (this.requireUppercase && value.chars().noneMatch(PasswordPolicy::isUppercase)) {
			unmet.add(REQUIRE_UPPERCASE);
		}
		if (this.requireLowercase && value.chars().noneMatch(PasswordPolicy::isLowercase)) {
			unmet.add(REQUIRE_LOWERCASE);
		}
		if (this.requireDigits && value.chars().noneMatch(PasswordPolicy::isDigit)) {
			unmet.add(REQUIRE_DIGITS);
		}
		if (this.requireSpecialChars && value.chars().noneMatch(PasswordPolicy::isSpecial)) {
			unmet.add(REQUIRE_SPECIAL_CHARS);
		}
		return unmet;
	}

	static boolean isUppercase(int c) {
		return c >= 'A' && c <= 'Z';
	}

	static boolean isLowercase(int c) {
		return c >= 'a' && c <= 'z';
	}

	static boolean isDigit(int c) {
		return c >= '0' && c <= '9';
	}

	/** Printable ASCII without space, letters and digits: 0x21–0x2F, 0x3A–0x40, 0x5B–0x60, 0x7B–0x7E. */
	static boolean isSpecial(int c) {
		return c >= 0x21 && c <= 0x7E && !isUppercase(c) && !isLowercase(c) && !isDigit(c);
	}

}
