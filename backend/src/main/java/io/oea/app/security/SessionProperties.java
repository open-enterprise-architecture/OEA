package io.oea.app.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Session timeouts (REQ-165): idle 5 min to 8 h (default 30 min), absolute 1 h to 7 days
 * (default 12 h).
 */
@ConfigurationProperties("oea.session")
public record SessionProperties(@DefaultValue("PT30M") Duration idleTimeout,
		@DefaultValue("PT12H") Duration absoluteLifetime) {

	public SessionProperties {
		if (idleTimeout.compareTo(Duration.ofMinutes(5)) < 0 || idleTimeout.compareTo(Duration.ofHours(8)) > 0) {
			throw new IllegalArgumentException("oea.session.idle-timeout must be between 5 minutes and 8 hours");
		}
		if (absoluteLifetime.compareTo(Duration.ofHours(1)) < 0 || absoluteLifetime.compareTo(Duration.ofDays(7)) > 0) {
			throw new IllegalArgumentException("oea.session.absolute-lifetime must be between 1 hour and 7 days");
		}
	}

}
