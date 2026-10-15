package io.oea.app.login;

import java.util.Collection;
import java.util.Set;

/**
 * Second factor from the {@code amr} claim of an ID token (RFC 8176; design D8,
 * ADR-032 SoD-8): {@code mfa}, or a password together with a possession or biometric factor.
 */
public final class AuthenticationMethods {

	private static final Set<String> SECOND_FACTORS = Set.of("otp", "hwk", "swk", "sc", "fpt", "face", "iris");

	private AuthenticationMethods() {
	}

	public static boolean secondFactor(Collection<String> amr) {
		if (amr == null) {
			return false;
		}
		return amr.contains("mfa") || (amr.contains("pwd") && amr.stream().anyMatch(SECOND_FACTORS::contains));
	}

}
