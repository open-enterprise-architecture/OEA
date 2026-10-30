package io.oea.app.login;

import io.oea.app.access.PasswordPolicies;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.config.webauthn.PasskeyRejectedException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passwordless login of a person with a passkey (US-009, REQ-009; design D4 of us-022-passkeys):
 * the browser offers the discoverable passkeys of the device, the assertion identifies the
 * person. The session is second-factor-verified (SoD-8, owner decision 2026-10-30). Every
 * failure gives the same answer and counts for the throttling per source address (REQ-168 AC3).
 */
@Service
public class PasskeyLogin {

	private final JdbcTemplate jdbc;

	private final Passkeys passkeys;

	private final PasswordPolicies policies;

	private final PersonSessions personSessions;

	private final LoginThrottle throttle;

	private final AuditWriter audit;

	private final LoginMetrics metrics;

	public PasskeyLogin(JdbcTemplate jdbc, Passkeys passkeys, PasswordPolicies policies, PersonSessions personSessions,
			LoginThrottle throttle, AuditWriter audit, LoginMetrics metrics) {
		this.jdbc = jdbc;
		this.passkeys = passkeys;
		this.policies = policies;
		this.personSessions = personSessions;
		this.throttle = throttle;
		this.audit = audit;
		this.metrics = metrics;
	}

	/** A challenge with its request options. */
	public record Options(UUID challengeId, Map<String, Object> options) {
	}

	/**
	 * Request options without allowed credentials (discoverable passkeys).
	 * @throws PasskeysUnavailableException without a relying party
	 */
	@Transactional
	public Options start() {
		this.passkeys.relyingParty();
		Passkeys.Challenge challenge = this.passkeys.newChallenge("login", null, null, null, Passkeys.LOGIN_VALIDITY);
		return new Options(challenge.id(), this.passkeys.requestOptions(challenge, List.of()));
	}

	/**
	 * Verifies the assertion and starts the session.
	 * @throws InvalidCredentialsException for every failure, also while throttled
	 * @throws PasskeysUnavailableException without a relying party
	 */
	@Transactional(noRollbackFor = InvalidCredentialsException.class)
	public Sessions.NewSession login(UUID challengeId, String assertionJson, LocalLogin.Client client) {
		return this.metrics.time("passkey", () -> passkeyLogin(challengeId, assertionJson, client));
	}

	private Sessions.NewSession passkeyLogin(UUID challengeId, String assertionJson, LocalLogin.Client client) {
		this.passkeys.relyingParty();
		long wait = this.throttle.retryAfterSecondsForAddress(client.address());
		if (wait > 0) {
			throw new InvalidCredentialsException(wait);
		}
		Passkeys.Challenge challenge = this.passkeys.consume(challengeId, "login").orElse(null);
		Passkeys.Verified verified;
		try {
			if (challenge == null) {
				throw new PasskeyRejectedException("unknown or expired challenge");
			}
			verified = this.passkeys.verify(assertionJson, challenge, null);
		}
		catch (PasskeyRejectedException ex) {
			throw fail(client);
		}
		if (!this.policies.personPasswordLoginPossible() || !mayLogIn(verified.personId())) {
			throw fail(client);
		}
		return this.personSessions.start(verified.personId(), "passkey", client);
	}

	/** Human persons in status invited or active (REQ-004). */
	private boolean mayLogIn(long personId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT person_type = 'human' AND status IN ('invited', 'active') FROM persons WHERE id = ?""",
				Boolean.class, personId));
	}

	private InvalidCredentialsException fail(LocalLogin.Client client) {
		this.throttle.recordAddressFailure(client.address());
		// pseudonymous: neither passkey nor person is recorded (ADR-033 §3)
		this.audit.appendIndependently(new AuditEvent("auth.login-failed", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.FAILURE, AuditEvent.Source.API,
				AuditEvent.Actor.unauthenticated(), null, null,
				Map.of("reason", "invalid-credentials", "method", "passkey")));
		return new InvalidCredentialsException(0);
	}

}
