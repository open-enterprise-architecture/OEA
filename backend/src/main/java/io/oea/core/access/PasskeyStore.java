package io.oea.core.access;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL on {@code passkey_credentials} (with the {@code local_credentials} base row of a passkey)
 * and {@code webauthn_challenges} (S17 change 3, ADR-051).
 */
@Repository
public class PasskeyStore {

	/** A stored WebAuthn challenge. */
	public record Challenge(UUID id, byte[] value, Long personId, UUID loginTransactionId, String label,
			Instant expiresAt) {
	}

	/** An active passkey with its owner, for the verification of an assertion. */
	public record StoredPasskey(UUID credentialId, long personId, byte[] userHandle, byte[] attestedCredentialData,
			long signCount, boolean backupEligible, boolean backedUp) {
	}

	private final JdbcTemplate jdbc;

	public PasskeyStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Deletes the challenges that expired at or before the time. */
	public void deleteExpiredChallenges(Instant now) {
		this.jdbc.update("DELETE FROM webauthn_challenges WHERE expires_at <= ?", Timestamp.from(now));
	}

	/** Stores a challenge. */
	public void insertChallenge(Challenge challenge, String purpose, Instant createdAt) {
		this.jdbc.update("""
				INSERT INTO webauthn_challenges (id, purpose, challenge, person_id, login_transaction_id, label,
				                                 created_at, expires_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", challenge.id(), purpose, challenge.value(), challenge.personId(),
				challenge.loginTransactionId(), challenge.label(), Timestamp.from(createdAt),
				Timestamp.from(challenge.expiresAt()));
	}

	/** Deletes and returns the challenge of the purpose (single use); the caller checks its expiry. */
	public Optional<Challenge> consumeChallenge(UUID id, String purpose) {
		return this.jdbc.query("""
				DELETE FROM webauthn_challenges WHERE id = ? AND purpose = ?
				RETURNING id, challenge, person_id, login_transaction_id, label, expires_at""",
				(rs, n) -> new Challenge(rs.getObject(1, UUID.class), rs.getBytes(2), rs.getObject(3, Long.class),
						rs.getObject(4, UUID.class), rs.getString(5), rs.getTimestamp(6).toInstant()),
				id, purpose)
			.stream()
			.findFirst();
	}

	/** The WebAuthn credential IDs of the active passkeys of a person. */
	public List<byte[]> activeCredentialIds(long personId) {
		return this.jdbc.queryForList("""
				SELECT p.webauthn_credential_id FROM passkey_credentials p
				JOIN local_credentials c ON c.id = p.credential_id
				WHERE c.person_id = ? AND c.type = 'passkey' AND c.status = 'active' ORDER BY c.created_at""",
				byte[].class, personId);
	}

	/** Finds the active passkey with the WebAuthn credential ID and locks its row ({@code FOR UPDATE OF p}). */
	public Optional<StoredPasskey> lockActivePasskey(byte[] webauthnCredentialId) {
		return this.jdbc.query("""
				SELECT c.id, c.person_id, pe.webauthn_user_handle, p.attested_credential_data, p.sign_count,
				       p.backup_eligible, p.backed_up
				FROM passkey_credentials p JOIN local_credentials c ON c.id = p.credential_id
				JOIN persons pe ON pe.id = c.person_id
				WHERE p.webauthn_credential_id = ? AND c.type = 'passkey' AND c.status = 'active'
				FOR UPDATE OF p""",
				(rs, n) -> new StoredPasskey(rs.getObject(1, UUID.class), rs.getLong(2), rs.getBytes(3), rs.getBytes(4),
						rs.getLong(5), rs.getBoolean(6), rs.getBoolean(7)),
				(Object) webauthnCredentialId)
			.stream()
			.findFirst();
	}

	/** Records the counter and the backup state after a verified assertion. */
	public void updateAssertion(UUID credentialId, long signCount, boolean backedUp) {
		this.jdbc.update("UPDATE passkey_credentials SET sign_count = ?, backed_up = ? WHERE credential_id = ?",
				signCount, backedUp, credentialId);
	}

	/** Inserts an active passkey: the base credential row and the passkey row. */
	public void insertPasskey(UUID credentialId, long personId, Instant createdAt, byte[] webauthnCredentialId,
			byte[] attestedCredentialData, long signCount, List<String> transports, boolean backupEligible,
			boolean backedUp, String label) {
		this.jdbc.update("""
				INSERT INTO local_credentials (id, person_id, type, status, created_at)
				VALUES (?, ?, 'passkey', 'active', ?)""", credentialId, personId, Timestamp.from(createdAt));
		this.jdbc.update("""
				INSERT INTO passkey_credentials (credential_id, webauthn_credential_id, attested_credential_data,
				                                 sign_count, transports, backup_eligible, backed_up, label)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", credentialId, webauthnCredentialId, attestedCredentialData,
				signCount, transports.toArray(String[]::new), backupEligible, backedUp, label);
	}

}
