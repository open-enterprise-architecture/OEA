package io.oea.app.login;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.RecentAuthentication;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.config.webauthn.PasskeyRejectedException;
import io.oea.config.webauthn.WebAuthnVerifier;
import io.oea.core.domain.person.Person;
import io.oea.core.repository.PersonRepository;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passkey registration of a person with a local password in the running session (US-022,
 * US-025 AC2, REQ-023; design D3 of us-022-passkeys): a discoverable credential with user
 * verification and attestation {@code none}, verified with WebAuthn4J before it is stored.
 */
@Service
public class PasskeyRegistration {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final JdbcTemplate jdbc;

	private final Passkeys passkeys;

	private final WebAuthnVerifier verifier;

	private final PersonRepository persons;

	private final RecentAuthentication recentAuthentication;

	private final Sessions sessions;

	private final AuditWriter audit;

	private final Clock clock;

	public PasskeyRegistration(JdbcTemplate jdbc, Passkeys passkeys, WebAuthnVerifier verifier, PersonRepository persons,
			RecentAuthentication recentAuthentication, Sessions sessions, AuditWriter audit, Clock clock) {
		this.jdbc = jdbc;
		this.passkeys = passkeys;
		this.verifier = verifier;
		this.persons = persons;
		this.recentAuthentication = recentAuthentication;
		this.sessions = sessions;
		this.audit = audit;
		this.clock = clock;
	}

	/** A started registration: the creation options for the browser. */
	public record Started(UUID registrationId, Map<String, Object> options, Instant expiresAt) {
	}

	/** A registered passkey. */
	public record Registered(UUID credentialId, String label, Instant createdAt) {
	}

	/**
	 * Starts a registration.
	 * @throws PasswordChange.NoLocalPasswordException for principals other than persons with a
	 * local password
	 * @throws PasskeysUnavailableException without a relying party
	 */
	@Transactional
	public Started start(OeaPrincipal principal, String label) {
		long personId = requirePersonWithPassword(principal);
		WebAuthnVerifier.RelyingParty rp = this.passkeys.relyingParty();
		this.recentAuthentication.require(principal);
		Person person = this.persons.findById(personId).orElseThrow();
		byte[] userHandle = userHandle(personId);
		Passkeys.Challenge challenge = this.passkeys.newChallenge("registration", personId, null,
				(label != null && !label.isBlank()) ? label.strip() : null, Passkeys.REGISTRATION_VALIDITY);
		Map<String, Object> options = new LinkedHashMap<>();
		options.put("rp", Map.of("id", rp.id(), "name", rp.name()));
		options.put("user", Map.of("id", Passkeys.base64url(userHandle), "name", person.getEmail(), "displayName",
				person.getName()));
		options.put("challenge", Passkeys.base64url(challenge.value()));
		options.put("pubKeyCredParams", List.of(Map.of("type", "public-key", "alg", -7),
				Map.of("type", "public-key", "alg", -257)));
		options.put("timeout", Passkeys.TIMEOUT_MILLISECONDS);
		options.put("excludeCredentials", Passkeys.descriptors(this.passkeys.credentialIds(personId)));
		options.put("authenticatorSelection",
				Map.of("residentKey", "required", "requireResidentKey", true, "userVerification", "required"));
		options.put("attestation", "none");
		return new Started(challenge.id(), options, challenge.expiresAt());
	}

	/**
	 * Verifies the response and stores the passkey.
	 * @throws NoSuchElementException for an unknown, foreign or expired registration
	 * @throws InvalidPasskeyException for a response that fails the verification
	 */
	@Transactional(noRollbackFor = { NoSuchElementException.class, InvalidPasskeyException.class })
	public Registered confirm(OeaPrincipal principal, UUID registrationId, String responseJson) {
		Passkeys.Challenge challenge = this.passkeys.consume(registrationId, "registration")
			.filter((c) -> principal.personId() != null && principal.personId().equals(c.personId()))
			.orElse(null);
		if (challenge == null) {
			failed(principal, registrationId, "enrollment-expired");
			throw new NoSuchElementException("no pending passkey registration " + registrationId);
		}
		WebAuthnVerifier.Registered verified;
		try {
			verified = this.verifier.verifyRegistration(responseJson, challenge.value());
		}
		catch (PasskeyRejectedException ex) {
			failed(principal, registrationId, "verification-failed");
			throw new InvalidPasskeyException();
		}
		Instant now = this.clock.instant();
		String label = (challenge.label() != null) ? challenge.label()
				: "Passkey (" + now.atOffset(ZoneOffset.UTC).toLocalDate() + ")";
		UUID credentialId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO local_credentials (id, person_id, type, status, created_at)
				VALUES (?, ?, 'passkey', 'active', ?)""", credentialId, principal.personId(), Timestamp.from(now));
		this.jdbc.update("""
				INSERT INTO passkey_credentials (credential_id, webauthn_credential_id, attested_credential_data,
				                                 sign_count, transports, backup_eligible, backed_up, label)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", credentialId, verified.credentialId(),
				verified.attestedCredentialData(), verified.signCount(), verified.transports().toArray(String[]::new),
				verified.backupEligible(), verified.backedUp(), label);
		this.sessions.confirmSecondFactor(principal.sessionId());
		this.audit.append(new AuditEvent("auth.factor-added", AuditEvent.Category.SECURITY, AuditEvent.Severity.HIGH,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "credential",
				credentialId.toString(), Map.of("type", "passkey", "label", label)));
		return new Registered(credentialId, label, now);
	}

	/** The person of the principal with an active local password (D3). */
	private long requirePersonWithPassword(OeaPrincipal principal) {
		if (principal.type() != OeaPrincipal.Type.PERSON) {
			throw new PasswordChange.NoLocalPasswordException();
		}
		Boolean password = this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM local_credentials WHERE person_id = ? AND type = 'password'
				               AND status = 'active')""", Boolean.class, principal.personId());
		if (!Boolean.TRUE.equals(password)) {
			throw new PasswordChange.NoLocalPasswordException();
		}
		return principal.personId();
	}

	/** The WebAuthn user handle of the person, created at the first registration (US-022). */
	private byte[] userHandle(long personId) {
		byte[] existing = this.jdbc.queryForObject("SELECT webauthn_user_handle FROM persons WHERE id = ?",
				byte[].class, personId);
		if (existing != null) {
			return existing;
		}
		byte[] handle = new byte[64];
		RANDOM.nextBytes(handle);
		this.jdbc.update("UPDATE persons SET webauthn_user_handle = ? WHERE id = ? AND webauthn_user_handle IS NULL",
				handle, personId);
		return this.jdbc.queryForObject("SELECT webauthn_user_handle FROM persons WHERE id = ?", byte[].class,
				personId);
	}

	/** REQ-025 AC2: a failed confirmation, in its own transaction. */
	private void failed(OeaPrincipal principal, UUID registrationId, String reason) {
		this.audit.appendIndependently(new AuditEvent("auth.factor-enrollment-failed", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.FAILURE, AuditEvent.Source.API, Sessions.actor(principal),
				"credential", registrationId.toString(), Map.of("type", "passkey", "reason", reason)));
	}

}
