package io.oea.app.access;

import io.oea.app.login.PersonPasswordProperties;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.service.PasswordHasher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initial password of a person set by an administrator (US-023, REQ-024; design D7 of
 * us-011-local-password): argon2id, origin {@code initial}, expiring (REQ-173 AC1), never
 * returned or logged.
 */
@Service
public class PersonPasswords {

	private final JdbcTemplate jdbc;

	private final PasswordHasher hasher;

	private final PersonPasswordProperties properties;

	private final PermissionDenials denials;

	private final AuditWriter audit;

	private final Clock clock;

	public PersonPasswords(JdbcTemplate jdbc, PasswordHasher hasher, PersonPasswordProperties properties,
			PermissionDenials denials, AuditWriter audit, Clock clock) {
		this.jdbc = jdbc;
		this.hasher = hasher;
		this.properties = properties;
		this.denials = denials;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Sets the initial password.
	 * @return the time the password expires
	 * @throws NoSuchElementException for an unknown person
	 * @throws AccessConflictException while password login of persons is off
	 * @throws SelfAdministrationException for the caller's own person (SoD-1)
	 * @throws InvalidAccessRequestException for a service account, a person not invited or active,
	 * or a password outside the length limits
	 * @throws PasswordAlreadySetException if the person already has an active password
	 */
	@Transactional
	public Instant setInitial(OeaPrincipal principal, long personId, String password) {
		List<Map<String, Object>> rows = this.jdbc
			.queryForList("SELECT person_type, status FROM persons WHERE id = ? FOR UPDATE", personId);
		if (rows.isEmpty()) {
			throw new NoSuchElementException("no person " + personId);
		}
		if (!this.properties.enabled()) {
			throw new AccessConflictException("Password login of persons is switched off on this instance.");
		}
		if (principal.personId() != null && principal.personId() == personId) {
			this.denials.record(principal, "setInitialPassword", "person", Long.toString(personId),
					PermissionDenials.SELF_ADMINISTRATION);
			throw new SelfAdministrationException();
		}
		String status = (String) rows.getFirst().get("status");
		if (!"human".equals(rows.getFirst().get("person_type"))
				|| !("invited".equals(status) || "active".equals(status))) {
			throw new InvalidAccessRequestException("personId",
					"Only invited or active persons get a password; service accounts never do.");
		}
		if (password == null || password.length() < PasswordHasher.MINIMUM_LENGTH
				|| password.length() > PasswordHasher.MAXIMUM_LENGTH) {
			throw new InvalidAccessRequestException("password", "The password must have 12 to 128 characters.");
		}
		Boolean exists = this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM local_credentials WHERE person_id = ? AND type = 'password'
				               AND status = 'active')""", Boolean.class, personId);
		if (Boolean.TRUE.equals(exists)) {
			throw new PasswordAlreadySetException();
		}
		Instant now = this.clock.instant().truncatedTo(ChronoUnit.MICROS);
		Instant expiresAt = now.plus(this.properties.temporaryPasswordValidity());
		UUID credentialId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO local_credentials (id, person_id, type, status, created_at)
				VALUES (?, ?, 'password', 'active', ?)""", credentialId, personId, Timestamp.from(now));
		this.jdbc.update("""
				INSERT INTO password_credentials (credential_id, password_hash, origin, expires_at)
				VALUES (?, ?, 'initial', ?)""", credentialId, this.hasher.hash(password), Timestamp.from(expiresAt));
		this.audit.append(new AuditEvent("person.password-set", AuditEvent.Category.SECURITY, AuditEvent.Severity.HIGH,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "person",
				Long.toString(personId), Map.of("origin", "initial", "expiresAt", expiresAt.toString())));
		return expiresAt;
	}

}
