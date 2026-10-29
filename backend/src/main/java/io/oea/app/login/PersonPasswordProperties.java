package io.oea.app.login;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Local password login of persons (US-011, REQ-011; design D1 of us-011-local-password): off on
 * a new instance; temporary passwords expire after 1 hour to 30 days (default 72 hours,
 * REQ-173 AC1).
 */
@ConfigurationProperties("oea.auth.person-password")
public record PersonPasswordProperties(@DefaultValue("false") boolean enabled,
		@DefaultValue("PT72H") Duration temporaryPasswordValidity) {

	public PersonPasswordProperties {
		if (temporaryPasswordValidity.compareTo(Duration.ofHours(1)) < 0
				|| temporaryPasswordValidity.compareTo(Duration.ofDays(30)) > 0) {
			throw new IllegalArgumentException(
					"oea.auth.person-password.temporary-password-validity must be between 1 hour and 30 days");
		}
	}

}
