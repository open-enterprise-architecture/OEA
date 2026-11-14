package io.oea.app.access;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Service accounts (REQ-176; design D3 of us-142-service-accounts): the maximum validity of a
 * client secret in days, 1 to 3650 (default 365). A secret is valid for 90 days unless a shorter
 * or longer validity up to this maximum is requested.
 */
@ConfigurationProperties("oea.service-accounts")
public record ServiceAccountProperties(@DefaultValue("365") int secretMaxValidityDays) {

	/** Upper bound of the configurable maximum, about ten years. */
	public static final int MAXIMUM = 3650;

	public ServiceAccountProperties {
		if (secretMaxValidityDays < 1 || secretMaxValidityDays > MAXIMUM) {
			throw new IllegalArgumentException(
					"oea.service-accounts.secret-max-validity-days must be between 1 and " + MAXIMUM);
		}
	}

}
