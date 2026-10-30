package io.oea.core.service;

import java.security.SecureRandom;
import java.util.function.IntPredicate;
import org.springframework.stereotype.Component;

/**
 * Generator of passwords that satisfy the effective policy (REQ-027; design D5 of
 * us-027-password-administration): CSPRNG, printable ASCII without space, at least 16
 * characters, one character of every required type, shuffled.
 */
@Component
public class PasswordGenerator {

	static final int MINIMUM_GENERATED_LENGTH = 16;

	private static final String ALPHABET = alphabet((c) -> true);

	private static final String UPPERCASE = alphabet(PasswordPolicy::isUppercase);

	private static final String LOWERCASE = alphabet(PasswordPolicy::isLowercase);

	private static final String DIGITS = alphabet(PasswordPolicy::isDigit);

	private static final String SPECIAL = alphabet(PasswordPolicy::isSpecial);

	private final SecureRandom random = new SecureRandom();

	/** Thrown if no password can satisfy the policy (409 {@code password-policy-unsatisfiable}). */
	public static class PolicyUnsatisfiableException extends RuntimeException {

		public PolicyUnsatisfiableException() {
			super("The password policy cannot be satisfied: the minimum length is shorter than the required character types.");
		}

	}

	public String generate(PasswordPolicy policy) {
		if (!policy.satisfiable()) {
			throw new PolicyUnsatisfiableException();
		}
		int length = Math.min(Math.max(policy.minLength(), MINIMUM_GENERATED_LENGTH), PasswordHasher.MAXIMUM_LENGTH);
		char[] password = new char[length];
		int next = 0;
		if (policy.requireUppercase()) {
			password[next++] = pick(UPPERCASE);
		}
		if (policy.requireLowercase()) {
			password[next++] = pick(LOWERCASE);
		}
		if (policy.requireDigits()) {
			password[next++] = pick(DIGITS);
		}
		if (policy.requireSpecialChars()) {
			password[next++] = pick(SPECIAL);
		}
		while (next < length) {
			password[next++] = pick(ALPHABET);
		}
		// Fisher–Yates with the same CSPRNG, so the required characters have no fixed position
		for (int i = length - 1; i > 0; i--) {
			int j = this.random.nextInt(i + 1);
			char swap = password[i];
			password[i] = password[j];
			password[j] = swap;
		}
		return new String(password);
	}

	private char pick(String characters) {
		return characters.charAt(this.random.nextInt(characters.length()));
	}

	private static String alphabet(IntPredicate filter) {
		StringBuilder characters = new StringBuilder();
		for (char c = 0x21; c <= 0x7E; c++) {
			if (filter.test(c)) {
				characters.append(c);
			}
		}
		return characters.toString();
	}

}
