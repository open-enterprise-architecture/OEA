package io.oea.core.access;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL on {@code local_credentials}, {@code password_credentials} and {@code totp_credentials}
 * (S17 change 3, ADR-051). The store reads and writes stored hashes and never compares a secret;
 * comparisons stay in the application layer.
 */
@Repository
public class CredentialStore {

	/** A person with the active password of its e-mail address, for the password login. */
	public record PersonLoginCandidate(long personId, String status, String personType, UUID credentialId, String hash,
			String origin, Instant expiresAt, Instant setAt) {
	}

	/** An active password with the login name of its owner. */
	public record CurrentPassword(UUID credentialId, String hash, String loginName) {
	}

	/** The origin and creation time of an active password. */
	public record PasswordState(String origin, Instant setAt) {
	}

	/** An active second factor; never with a secret. */
	public record SecondFactorRow(UUID credentialId, String type, String label, Instant createdAt, Instant lastUsedAt) {
	}

	private final JdbcTemplate jdbc;

	public CredentialStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The persons with the e-mail address (ignoring case) and their active password, if any. */
	public List<PersonLoginCandidate> findPersonLoginCandidates(String email) {
		return this.jdbc.query("""
				SELECT p.id, p.status, p.person_type, c.id, pc.password_hash, pc.origin, pc.expires_at, c.created_at
				FROM persons p
				LEFT JOIN local_credentials c ON c.person_id = p.id AND c.type = 'password' AND c.status = 'active'
				LEFT JOIN password_credentials pc ON pc.credential_id = c.id
				WHERE lower(p.email) = lower(?)""", (rs, n) -> {
			Timestamp expires = rs.getTimestamp(7);
			Timestamp set = rs.getTimestamp(8);
			return new PersonLoginCandidate(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class),
					rs.getString(5), rs.getString(6), (expires != null) ? expires.toInstant() : null,
					(set != null) ? set.toInstant() : null);
		}, email);
	}

	/** Whether the person has an active password. */
	public boolean hasActivePassword(Long personId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM local_credentials WHERE person_id = ? AND type = 'password'
				               AND status = 'active')""", Boolean.class, personId));
	}

	/** Whether the system admin account has an active password. */
	public boolean hasActivePasswordOfAdmin(UUID adminId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM local_credentials WHERE system_admin_account_id = ? AND type = 'password'
				               AND status = 'active')""", Boolean.class, adminId));
	}

	/** The active password of a person with its hash and e-mail address. */
	public Optional<CurrentPassword> findCurrentPassword(long personId) {
		return this.jdbc.query("""
				SELECT c.id, pc.password_hash, p.email FROM local_credentials c
				JOIN password_credentials pc ON pc.credential_id = c.id JOIN persons p ON p.id = c.person_id
				WHERE c.person_id = ? AND c.type = 'password' AND c.status = 'active'""",
				(rs, n) -> new CurrentPassword(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), personId)
			.stream()
			.findFirst();
	}

	/** The active password of the system admin account with its hash and login name. */
	public Optional<CurrentPassword> findCurrentPasswordOfAdmin(UUID adminId) {
		return this.jdbc.query("""
				SELECT c.id, pc.password_hash, a.login_name FROM local_credentials c
				JOIN password_credentials pc ON pc.credential_id = c.id
				JOIN system_admin_accounts a ON a.id = c.system_admin_account_id
				WHERE c.system_admin_account_id = ? AND c.type = 'password' AND c.status = 'active'""",
				(rs, n) -> new CurrentPassword(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), adminId)
			.stream()
			.findFirst();
	}

	/** The active password credentials of the person, locked ({@code FOR UPDATE}). */
	public List<UUID> lockActivePasswordCredentials(long personId) {
		return this.jdbc.queryForList("""
				SELECT id FROM local_credentials WHERE person_id = ? AND type = 'password' AND status = 'active'
				FOR UPDATE""", UUID.class, personId);
	}

	/** The origin and creation time of the active password of the person. */
	public List<PasswordState> activePasswordStates(long personId) {
		return this.jdbc.query("""
				SELECT pc.origin, c.created_at FROM local_credentials c
				JOIN password_credentials pc ON pc.credential_id = c.id
				WHERE c.person_id = ? AND c.type = 'password' AND c.status = 'active'""",
				(rs, n) -> new PasswordState(rs.getString(1), rs.getTimestamp(2).toInstant()), personId);
	}

	/**
	 * The current password and the most recent revoked ones of the owner, newest first, for the
	 * history check.
	 */
	public List<String> passwordHistory(Long personId, UUID systemAdminAccountId, int count) {
		return this.jdbc.queryForList("""
				SELECT pc.password_hash FROM local_credentials c JOIN password_credentials pc ON pc.credential_id = c.id
				WHERE c.type = 'password' AND (c.person_id = ? OR c.system_admin_account_id = ?)
				ORDER BY c.status = 'active' DESC, c.revoked_at DESC NULLS LAST, c.created_at DESC
				LIMIT ?""", String.class, personId, systemAdminAccountId, count);
	}

	/** Inserts an active password credential of a person or of the system admin account. */
	public void insertActivePassword(UUID credentialId, Long personId, UUID systemAdminAccountId, Instant createdAt) {
		this.jdbc.update("""
				INSERT INTO local_credentials (id, person_id, system_admin_account_id, type, status, created_at)
				VALUES (?, ?, ?, 'password', 'active', ?)""", credentialId, personId, systemAdminAccountId,
				Timestamp.from(createdAt));
	}

	/** Stores the hash of a self-set password without expiry. */
	public void insertPasswordHash(UUID credentialId, String hash) {
		this.jdbc.update("INSERT INTO password_credentials (credential_id, password_hash) VALUES (?, ?)", credentialId,
				hash);
	}

	/** Stores the hash of a temporary password with its origin and expiry. */
	public void insertPasswordHash(UUID credentialId, String hash, String origin, Instant expiresAt) {
		this.jdbc.update("""
				INSERT INTO password_credentials (credential_id, password_hash, origin, expires_at)
				VALUES (?, ?, ?, ?)""", credentialId, hash, origin, Timestamp.from(expiresAt));
	}

	/** Replaces the stored hash after a rehash. */
	public void updatePasswordHash(UUID credentialId, String hash) {
		this.jdbc.update("UPDATE password_credentials SET password_hash = ? WHERE credential_id = ?", hash,
				credentialId);
	}

	/** Records the use of a credential. */
	public void markUsed(UUID credentialId, Instant at) {
		this.jdbc.update("UPDATE local_credentials SET last_used_at = ? WHERE id = ?", Timestamp.from(at),
				credentialId);
	}

	/** Revokes a credential with the reason and the principal that did it (both may be {@code null}). */
	public void revoke(UUID credentialId, Instant at, String reason, Long byPersonId, UUID byAdminId) {
		this.jdbc.update("""
				UPDATE local_credentials SET status = 'revoked', revoked_at = ?, revoked_reason = ?,
				       revoked_by_person_id = ?, revoked_by_admin_id = ?
				WHERE id = ?""", Timestamp.from(at), reason, byPersonId, byAdminId, credentialId);
	}

	/** Revokes the active and pending second factors (TOTP, passkey) of a person; returns their number. */
	public int revokeSecondFactors(long personId, Instant at, String reason, Long byPersonId, UUID byAdminId) {
		return this.jdbc.update("""
				UPDATE local_credentials SET status = 'revoked', revoked_at = ?, revoked_reason = ?,
				       revoked_by_person_id = ?, revoked_by_admin_id = ?
				WHERE person_id = ? AND status IN ('active', 'pending') AND type IN ('totp', 'passkey')""",
				Timestamp.from(at), reason, byPersonId, byAdminId, personId);
	}

	/** The active second factors of the owner, ordered by creation. */
	public List<SecondFactorRow> activeSecondFactors(Long personId, UUID systemAdminAccountId) {
		return this.jdbc.query("""
				SELECT c.id, c.type, COALESCE(t.label, p.label), c.created_at, c.last_used_at FROM local_credentials c
				LEFT JOIN totp_credentials t ON t.credential_id = c.id
				LEFT JOIN passkey_credentials p ON p.credential_id = c.id
				WHERE (c.person_id = ? OR c.system_admin_account_id = ?) AND c.status = 'active'
				  AND c.type IN ('totp', 'passkey') ORDER BY c.created_at, c.id""", (rs, n) -> {
			Timestamp used = rs.getTimestamp(5);
			return new SecondFactorRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
					rs.getTimestamp(4).toInstant(), (used != null) ? used.toInstant() : null);
		}, personId, systemAdminAccountId);
	}

	/** The number of active second factors of the person. */
	public int countActiveSecondFactors(long personId) {
		Integer active = this.jdbc.queryForObject("""
				SELECT count(*) FROM local_credentials WHERE person_id = ? AND status = 'active'
				  AND type IN ('totp', 'passkey')""", Integer.class, personId);
		return (active != null) ? active : 0;
	}

	/** The number of active second factors of the person, disregarding one credential ({@code null}: none). */
	public int countActiveSecondFactorsExcept(long personId, UUID ignoredCredential) {
		Integer factors = this.jdbc.queryForObject("""
				SELECT count(*) FROM local_credentials WHERE person_id = ? AND type IN ('totp', 'passkey')
				  AND status = 'active' AND (?::uuid IS NULL OR id <> ?::uuid)""", Integer.class, personId,
				ignoredCredential, ignoredCredential);
		return (factors != null) ? factors : 0;
	}

	/** Revokes the active password of the system admin account (break-glass recovery). */
	public void revokeActivePasswordOfAdmin(UUID adminId, Instant at) {
		this.jdbc.update("""
				UPDATE local_credentials SET status = 'revoked', revoked_at = ?, revoked_reason = 'admin_action'
				WHERE system_admin_account_id = ? AND type = 'password' AND status = 'active'""", Timestamp.from(at),
				adminId);
	}

	/** Revokes the active and pending TOTP credentials of the system admin account (break-glass recovery). */
	public void revokeTotpOfAdmin(UUID adminId, Instant at) {
		this.jdbc.update("""
				UPDATE local_credentials SET status = 'revoked', revoked_at = ?, revoked_reason = 'factor_reset'
				WHERE system_admin_account_id = ? AND type = 'totp' AND status IN ('active', 'pending')""",
				Timestamp.from(at), adminId);
	}

	/** Inserts an active, used TOTP credential of the system admin account (break-glass recovery). */
	public void insertActiveTotpOfAdmin(UUID credentialId, UUID adminId, Instant at) {
		this.jdbc.update("""
				INSERT INTO local_credentials (id, system_admin_account_id, type, status, created_at, last_used_at)
				VALUES (?, ?, 'totp', 'active', ?, ?)""", credentialId, adminId, Timestamp.from(at), Timestamp.from(at));
	}

	/** Stores the encrypted secret of a recovered TOTP credential. */
	public void insertRecoveredTotp(UUID credentialId, String secretEncrypted, String keyId, long acceptedStep) {
		this.jdbc.update("""
				INSERT INTO totp_credentials (credential_id, secret_encrypted, key_id, last_used_time_step, label)
				VALUES (?, ?, ?, ?, 'break-glass recovery')""", credentialId, secretEncrypted, keyId, acceptedStep);
	}

}
