package io.oea.app.security;

import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Sensitive actions need an authentication within the last 5 minutes (REQ-172 AC1). */
@Component
public class RecentAuthentication {

	public static final Duration MAXIMUM_AGE = Duration.ofMinutes(5);

	private final Clock clock;

	public RecentAuthentication(Clock clock) {
		this.clock = clock;
	}

	public void require(OeaPrincipal principal) {
		if (principal.authenticatedAt().plus(MAXIMUM_AGE).isBefore(this.clock.instant())) {
			throw new ReauthenticationRequiredException();
		}
	}

}
