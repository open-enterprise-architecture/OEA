package io.oea.app.security;

import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.domain.auth.LoginSession;
import io.oea.core.domain.auth.SystemAdminAccount;
import io.oea.core.domain.person.Person;
import io.oea.core.repository.LoginSessionRepository;
import io.oea.core.repository.PersonRepository;
import io.oea.core.repository.SystemAdminAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, authenticates and ends sessions (design D1, D2). The cookie value is 256 random
 * bits; only its SHA-256 is stored. The CSRF token is derived from the cookie value.
 */
@Service
public class Sessions {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final LoginSessionRepository sessions;

	private final SystemAdminAccountRepository accounts;

	private final PersonRepository persons;

	private final SessionProperties properties;

	private final AuditWriter audit;

	private final ObjectProvider<IdpSessionRefresh> idpRefresh;

	private final Clock clock;

	public Sessions(LoginSessionRepository sessions, SystemAdminAccountRepository accounts, PersonRepository persons,
			SessionProperties properties, AuditWriter audit, ObjectProvider<IdpSessionRefresh> idpRefresh,
			Clock clock) {
		this.sessions = sessions;
		this.accounts = accounts;
		this.persons = persons;
		this.properties = properties;
		this.audit = audit;
		this.idpRefresh = idpRefresh;
		this.clock = clock;
	}

	/** A new session with its secrets; the token goes into the cookie, never into the database. */
	public record NewSession(String token, String csrfToken, OeaPrincipal principal) {
	}

	@Transactional
	public NewSession createForSystemAdmin(SystemAdminAccount account, boolean secondFactorVerified, String clientIp,
			String userAgent) {
		String token = randomToken();
		Instant now = this.clock.instant();
		LoginSession session = this.sessions.save(LoginSession.forSystemAdmin(account.getId(), hash(token),
				secondFactorVerified ? "password-totp" : "password", secondFactorVerified, now,
				this.properties.absoluteLifetime(), clientIp, userAgent));
		return new NewSession(token, csrfToken(token), principal(session, account));
	}

	/** New session of a person established through an OIDC provider (US-001, design D6). */
	@Transactional
	public NewSession createForPerson(Person person, UUID authProviderId, boolean secondFactorVerified,
			String clientIp, String userAgent) {
		String token = randomToken();
		Instant now = this.clock.instant();
		// flushed at once: the derived roles are written with plain SQL in the same transaction
		LoginSession session = this.sessions.saveAndFlush(LoginSession.forPerson(person.getId(), authProviderId,
				hash(token), secondFactorVerified, now, this.properties.absoluteLifetime(), clientIp, userAgent));
		return new NewSession(token, csrfToken(token), new OeaPrincipal(OeaPrincipal.Type.PERSON, null, person.getId(),
				person.getName(), session.getId(), secondFactorVerified, now));
	}

	/** The principal of an active session, or empty for unknown, revoked or expired sessions. */
	@Transactional
	public Optional<OeaPrincipal> authenticate(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		Instant now = this.clock.instant();
		Optional<LoginSession> found = this.sessions.findByTokenHash(hash(token));
		if (found.isEmpty()) {
			return Optional.empty();
		}
		LoginSession session = found.get();
		if (!session.isActive(now, this.properties.idleTimeout())) {
			if (session.getRevokedAt() == null) {
				session.revoke("expired", null, now);
			}
			return Optional.empty();
		}
		if ("oidc".equals(session.getAuthMethod())) {
			// the session ends with the IdP refresh token (owner decision 2026-10-15, REQ-001)
			boolean usable = !session.isIdpTokenExpired(now);
			if (usable && session.isIdpEvaluationDue(now, IdpRoles.MAXIMUM_AGE)) {
				IdpSessionRefresh refresh = this.idpRefresh.getIfAvailable();
				usable = refresh == null || refresh.refreshIfDue(session);
			}
			if (!usable) {
				end(session, "idp-refresh-failed", now);
				return Optional.empty();
			}
		}
		session.touch(now);
		if (session.getPersonId() != null) {
			return this.persons.findById(session.getPersonId())
				.filter((person) -> person.getStatus() == Person.Status.ACTIVE
						|| person.getStatus() == Person.Status.INVITED)
				.map((person) -> new OeaPrincipal(OeaPrincipal.Type.PERSON, null, person.getId(), person.getName(),
						session.getId(), session.isSecondFactorVerified(), session.getAuthenticatedAt()));
		}
		return this.accounts.findById(session.getSystemAdminAccountId()).map((account) -> principal(session, account));
	}

	private void end(LoginSession session, String reason, Instant now) {
		session.revoke(reason, null, now);
		this.audit.append(new AuditEvent("session.revoked", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, AuditEvent.Actor.system(), "session",
				session.getId().toString(), Map.of("reason", reason)));
	}

	/** Second factor confirmed in the running session (design D7, ADR-042). */
	@Transactional
	public void confirmSecondFactor(UUID sessionId) {
		this.sessions.findById(sessionId).ifPresent((session) -> session.confirmSecondFactor(this.clock.instant()));
	}

	/** Logout (REQ-164 AC1): the session is never accepted again. */
	@Transactional
	public void logout(OeaPrincipal principal) {
		this.sessions.findById(principal.sessionId())
			.ifPresent((session) -> session.revoke("logout", principal.systemAdminAccountId(), principal.personId(),
					this.clock.instant()));
		this.audit.append(new AuditEvent("auth.logout", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, actor(principal), "session",
				principal.sessionId().toString(), Map.of()));
	}

	/** CSRF token of a session: HMAC-SHA256 of the cookie value (signed double submit, D2). */
	public static String csrfToken(String token) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
			return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(mac.doFinal("oea-csrf".getBytes(StandardCharsets.US_ASCII)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static AuditEvent.Actor actor(OeaPrincipal principal) {
		return (principal.type() == OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT)
				? AuditEvent.Actor.systemAdminAccount(principal.systemAdminAccountId())
				: new AuditEvent.Actor("person", principal.personId(), null);
	}

	public Duration absoluteLifetime() {
		return this.properties.absoluteLifetime();
	}

	static byte[] hash(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String randomToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static OeaPrincipal principal(LoginSession session, SystemAdminAccount account) {
		return new OeaPrincipal(OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT, account.getId(), null, account.getLoginName(),
				session.getId(), session.isSecondFactorVerified(), session.getAuthenticatedAt());
	}

}
