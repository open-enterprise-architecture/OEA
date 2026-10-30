package io.oea.app.login;

import io.oea.app.access.PasswordPolicies;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.domain.person.Person;
import io.oea.core.repository.PersonRepository;
import io.oea.core.service.PasswordPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Session of a person after a local login (US-011, US-010, US-009): activation at the first login,
 * the required password change of a temporary or expired password (REQ-173 AC2, REQ-028 AC2) and
 * the audit event (design D4 of us-021-person-totp, design D4 of us-022-passkeys).
 */
@Service
class PersonSessions {

	private final JdbcTemplate jdbc;

	private final PersonRepository persons;

	private final PasswordPolicies policies;

	private final Sessions sessions;

	private final AuditWriter audit;

	private final Clock clock;

	PersonSessions(JdbcTemplate jdbc, PersonRepository persons, PasswordPolicies policies, Sessions sessions,
			AuditWriter audit, Clock clock) {
		this.jdbc = jdbc;
		this.persons = persons;
		this.policies = policies;
		this.sessions = sessions;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Starts the session; the caller's transaction must have checked the credentials.
	 * @param authMethod {@code password}, {@code password-totp}, {@code password-passkey} or
	 * {@code passkey}
	 */
	Sessions.NewSession start(long personId, String authMethod, LocalLogin.Client client) {
		Instant now = this.clock.instant();
		Person entity = this.persons.findById(personId).orElseThrow();
		if (entity.activateOnFirstLogin(now)) {
			this.audit.append(new AuditEvent("person.activated", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
					AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, new AuditEvent.Actor("person", personId, null),
					"person", Long.toString(personId), Map.of("trigger", "first-login")));
		}
		record Password(String origin, Instant setAt) {
		}
		List<Password> active = this.jdbc.query("""
				SELECT pc.origin, c.created_at FROM local_credentials c
				JOIN password_credentials pc ON pc.credential_id = c.id
				WHERE c.person_id = ? AND c.type = 'password' AND c.status = 'active'""",
				(rs, n) -> new Password(rs.getString(1), rs.getTimestamp(2).toInstant()), personId);
		// REQ-173 AC2, REQ-028 AC2: a temporary or expired password only leads to the password change
		PasswordPolicy policy = this.policies.effective();
		boolean temporary = !active.isEmpty() && !"self-set".equals(active.getFirst().origin());
		boolean expired = !active.isEmpty() && policy.expiryDays() > 0
				&& !active.getFirst().setAt().plus(Duration.ofDays(policy.expiryDays())).isAfter(now);
		String requiredAction = (temporary || expired) ? OeaPrincipal.CHANGE_PASSWORD : null;
		Sessions.NewSession session = this.sessions.createForPersonPassword(entity, requiredAction, authMethod,
				client.address(), client.userAgent());
		this.audit.append(new AuditEvent("auth.login", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, new AuditEvent.Actor("person", personId, null),
				"session", session.principal().sessionId().toString(), Map.of("method", authMethod, "secondFactor",
						!"password".equals(authMethod), "temporaryPassword", temporary, "passwordExpired", expired)));
		return session;
	}

}
