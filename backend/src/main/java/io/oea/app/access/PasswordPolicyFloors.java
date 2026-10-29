package io.oea.app.access;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Floors of the password policy set by the operator (REQ-028; design D1 of
 * us-027-password-administration). Administrators cannot store a weaker policy; the effective
 * value of every rule is the stricter of floor and stored value.
 * @param minLength floor of the minimum length, 12 to 128 (REQ-169)
 * @param maxExpiryDays ceiling of the expiry in days, 0 = no ceiling
 * @param minHistoryCount floor of the history, 0 to 24
 */
@ConfigurationProperties("oea.auth.password-policy")
public record PasswordPolicyFloors(@DefaultValue("12") int minLength, @DefaultValue("false") boolean requireUppercase,
		@DefaultValue("false") boolean requireLowercase, @DefaultValue("false") boolean requireDigits,
		@DefaultValue("false") boolean requireSpecialChars, @DefaultValue("0") int maxExpiryDays,
		@DefaultValue("0") int minHistoryCount) {

	public PasswordPolicyFloors {
		if (minLength < 12 || minLength > 128) {
			throw new IllegalArgumentException("oea.auth.password-policy.min-length must be between 12 and 128");
		}
		if (maxExpiryDays < 0 || maxExpiryDays > 3650) {
			throw new IllegalArgumentException("oea.auth.password-policy.max-expiry-days must be between 0 and 3650");
		}
		if (minHistoryCount < 0 || minHistoryCount > 24) {
			throw new IllegalArgumentException("oea.auth.password-policy.min-history-count must be between 0 and 24");
		}
	}

}
