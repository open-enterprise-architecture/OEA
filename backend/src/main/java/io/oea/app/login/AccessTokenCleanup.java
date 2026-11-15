package io.oea.app.login;

import io.oea.core.access.AccessTokenStore;
import java.time.Clock;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes access tokens one day after their expiry (ADR-050 rule 2; design D7 of
 * s12-client-credentials-token). The issue of each token is in the audit trail as
 * {@code token.issued}, so the deletion is not audited.
 */
@Component
public class AccessTokenCleanup {

	/** How long an expired token is kept. */
	static final Duration RETENTION = Duration.ofDays(1);

	private final AccessTokenStore tokens;

	private final Clock clock;

	public AccessTokenCleanup(AccessTokenStore tokens, Clock clock) {
		this.tokens = tokens;
		this.clock = clock;
	}

	/** @return the number of deleted tokens */
	@Scheduled(fixedDelayString = "${oea.access-tokens.cleanup-interval:PT1H}",
			initialDelayString = "${oea.access-tokens.cleanup-interval:PT1H}")
	@Transactional
	public int deleteExpired() {
		return this.tokens.deleteExpiredBefore(this.clock.instant().minus(RETENTION));
	}

}
