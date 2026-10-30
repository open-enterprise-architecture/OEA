package io.oea.app.login;

import io.oea.config.webauthn.PasskeyRejectedException;
import io.oea.config.webauthn.WebAuthnVerifier;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Challenges and assertions of passkeys (design D2, D4, D5 of us-022-passkeys): the WebAuthn
 * challenges in {@code webauthn_challenges}, the request options, and the verification of an
 * assertion against an active passkey with the counter update.
 */
@Service
class Passkeys {

	static final Duration LOGIN_VALIDITY = Duration.ofMinutes(5);

	static final Duration REGISTRATION_VALIDITY = Duration.ofMinutes(10);

	static final long TIMEOUT_MILLISECONDS = 300_000;

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

	private final JdbcTemplate jdbc;

	private final WebAuthnVerifier verifier;

	private final Clock clock;

	Passkeys(JdbcTemplate jdbc, WebAuthnVerifier verifier, Clock clock) {
		this.jdbc = jdbc;
		this.verifier = verifier;
		this.clock = clock;
	}

	/** A stored challenge. */
	record Challenge(UUID id, byte[] value, Long personId, UUID loginTransactionId, String label, Instant expiresAt) {
	}

	/** The person and the passkey of a verified assertion. */
	record Verified(long personId, UUID credentialId) {
	}

	/**
	 * The relying party.
	 * @throws PasskeysUnavailableException without a relying party (D1)
	 */
	WebAuthnVerifier.RelyingParty relyingParty() {
		return this.verifier.relyingParty().orElseThrow(PasskeysUnavailableException::new);
	}

	boolean available() {
		return this.verifier.relyingParty().isPresent();
	}

	/** Stores a new challenge; expired challenges are deleted at the same time (D2). */
	Challenge newChallenge(String purpose, Long personId, UUID loginTransactionId, String label, Duration validity) {
		Instant now = this.clock.instant();
		this.jdbc.update("DELETE FROM webauthn_challenges WHERE expires_at <= ?", Timestamp.from(now));
		byte[] value = new byte[32];
		RANDOM.nextBytes(value);
		Challenge challenge = new Challenge(UUID.randomUUID(), value, personId, loginTransactionId, label,
				now.plus(validity));
		this.jdbc.update("""
				INSERT INTO webauthn_challenges (id, purpose, challenge, person_id, login_transaction_id, label,
				                                 created_at, expires_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", challenge.id(), purpose, value, personId, loginTransactionId, label,
				Timestamp.from(now), Timestamp.from(challenge.expiresAt()));
		return challenge;
	}

	/** Takes an unexpired challenge of the purpose; it is deleted in any case (single use). */
	Optional<Challenge> consume(UUID id, String purpose) {
		if (id == null) {
			return Optional.empty();
		}
		Instant now = this.clock.instant();
		return this.jdbc.query("""
				DELETE FROM webauthn_challenges WHERE id = ? AND purpose = ?
				RETURNING id, challenge, person_id, login_transaction_id, label, expires_at""",
				(rs, n) -> new Challenge(rs.getObject(1, UUID.class), rs.getBytes(2), rs.getObject(3, Long.class),
						rs.getObject(4, UUID.class), rs.getString(5), rs.getTimestamp(6).toInstant()),
				id, purpose)
			.stream()
			.filter((challenge) -> challenge.expiresAt().isAfter(now))
			.findFirst();
	}

	/** Request options (WebAuthn Level 3 JSON) with user verification required. */
	Map<String, Object> requestOptions(Challenge challenge, List<byte[]> allowCredentials) {
		Map<String, Object> options = new LinkedHashMap<>();
		options.put("challenge", base64url(challenge.value()));
		options.put("rpId", relyingParty().id());
		options.put("timeout", TIMEOUT_MILLISECONDS);
		options.put("userVerification", "required");
		options.put("allowCredentials", descriptors(allowCredentials));
		return options;
	}

	/** The WebAuthn credential IDs of the active passkeys of a person. */
	List<byte[]> credentialIds(long personId) {
		return this.jdbc.queryForList("""
				SELECT p.webauthn_credential_id FROM passkey_credentials p
				JOIN local_credentials c ON c.id = p.credential_id
				WHERE c.person_id = ? AND c.type = 'passkey' AND c.status = 'active' ORDER BY c.created_at""",
				byte[].class, personId);
	}

	boolean hasActivePasskey(long personId) {
		return !credentialIds(personId).isEmpty();
	}

	/**
	 * Verifies an assertion against the active passkey it names, and records its use.
	 * @param expectedPersonId the person the passkey must belong to, or {@code null} for any
	 * @throws PasskeyRejectedException for any failed check
	 */
	Verified verify(String assertionJson, Challenge challenge, Long expectedPersonId) {
		WebAuthnVerifier.Presented presented = this.verifier.presented(assertionJson);
		record Stored(UUID credentialId, long personId, byte[] userHandle, byte[] data, long signCount,
				boolean backupEligible, boolean backedUp) {
		}
		List<Stored> found = this.jdbc.query("""
				SELECT c.id, c.person_id, pe.webauthn_user_handle, p.attested_credential_data, p.sign_count,
				       p.backup_eligible, p.backed_up
				FROM passkey_credentials p JOIN local_credentials c ON c.id = p.credential_id
				JOIN persons pe ON pe.id = c.person_id
				WHERE p.webauthn_credential_id = ? AND c.type = 'passkey' AND c.status = 'active'
				FOR UPDATE OF p""",
				(rs, n) -> new Stored(rs.getObject(1, UUID.class), rs.getLong(2), rs.getBytes(3), rs.getBytes(4),
						rs.getLong(5), rs.getBoolean(6), rs.getBoolean(7)),
				(Object) presented.credentialId());
		if (found.isEmpty()) {
			throw new PasskeyRejectedException("unknown passkey");
		}
		Stored stored = found.getFirst();
		if (expectedPersonId != null && expectedPersonId != stored.personId()) {
			throw new PasskeyRejectedException("passkey of another person");
		}
		if (presented.userHandle() != null && !Arrays.equals(presented.userHandle(), stored.userHandle())) {
			throw new PasskeyRejectedException("user handle does not match");
		}
		WebAuthnVerifier.Asserted asserted = this.verifier.verifyAuthentication(assertionJson, challenge.value(),
				new WebAuthnVerifier.StoredPasskey(stored.data(), stored.signCount(), stored.backupEligible(),
						stored.backedUp()));
		this.jdbc.update("UPDATE passkey_credentials SET sign_count = ?, backed_up = ? WHERE credential_id = ?",
				asserted.signCount(), asserted.backedUp(), stored.credentialId());
		this.jdbc.update("UPDATE local_credentials SET last_used_at = ? WHERE id = ?",
				Timestamp.from(this.clock.instant()), stored.credentialId());
		return new Verified(stored.personId(), stored.credentialId());
	}

	static String base64url(byte[] value) {
		return BASE64URL.encodeToString(value);
	}

	static List<Map<String, Object>> descriptors(List<byte[]> credentialIds) {
		List<Map<String, Object>> descriptors = new ArrayList<>();
		for (byte[] id : credentialIds) {
			descriptors.add(Map.of("type", "public-key", "id", base64url(id)));
		}
		return descriptors;
	}

}
