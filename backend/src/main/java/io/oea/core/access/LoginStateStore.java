package io.oea.core.access;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL on the short-lived login state: {@code login_transactions} (second step of a login) and
 * {@code oidc_login_states} (running OIDC logins). Only hashes are stored; the store compares
 * nothing (S17 change 3, ADR-051).
 */
@Repository
public class LoginStateStore {

	/** Owner of a login transaction: the system admin account or a person. */
	public record Owner(UUID systemAdminAccountId, Long personId) {
	}

	/** A login transaction of a person. */
	public record PersonTransaction(UUID id, long personId) {
	}

	/** A running OIDC login as stored; the secrets are still encrypted. */
	public record OidcState(byte[] browserBindingHash, UUID authProviderId, String nonce,
			String codeVerifierEncrypted, String codeVerifierKeyId, String returnTo, Instant expiresAt) {
	}

	private final JdbcTemplate jdbc;

	public LoginStateStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Stores a login transaction. */
	public void insertTransaction(byte[] tokenHash, UUID systemAdminAccountId, Long personId, Instant createdAt,
			Instant expiresAt) {
		this.jdbc.update("""
				INSERT INTO login_transactions (token_hash, system_admin_account_id, person_id, created_at, expires_at)
				VALUES (?, ?, ?, ?, ?)""", tokenHash, systemAdminAccountId, personId, Timestamp.from(createdAt),
				Timestamp.from(expiresAt));
	}

	/** Finds the open transaction by its token hash and locks it ({@code FOR UPDATE}). */
	public Optional<Owner> lockOpenTransaction(byte[] tokenHash, Instant now, int maximumFailedCodes) {
		return this.jdbc.query("""
				SELECT system_admin_account_id, person_id FROM login_transactions
				WHERE token_hash = ? AND expires_at > ? AND failed_codes < ? FOR UPDATE""",
				(rs) -> rs.next() ? Optional.of(new Owner(rs.getObject(1, UUID.class), rs.getObject(2, Long.class)))
						: Optional.<Owner>empty(),
				tokenHash, Timestamp.from(now), maximumFailedCodes);
	}

	/** Finds the open transaction of a person by its token hash and locks it ({@code FOR UPDATE}). */
	public Optional<PersonTransaction> lockOpenPersonTransaction(byte[] tokenHash, Instant now,
			int maximumFailedCodes) {
		return this.jdbc.query("""
				SELECT id, person_id FROM login_transactions
				WHERE token_hash = ? AND person_id IS NOT NULL AND expires_at > ? AND failed_codes < ? FOR UPDATE""",
				(rs) -> rs.next() ? Optional.of(new PersonTransaction(rs.getObject(1, UUID.class), rs.getLong(2)))
						: Optional.<PersonTransaction>empty(),
				tokenHash, Timestamp.from(now), maximumFailedCodes);
	}

	/** The IDs of the open transactions of a person with the token hash (without a lock). */
	public List<UUID> findOpenPersonTransactionIds(byte[] tokenHash, Instant now, int maximumFailedCodes) {
		return this.jdbc.queryForList("""
				SELECT id FROM login_transactions
				WHERE token_hash = ? AND person_id IS NOT NULL AND expires_at > ? AND failed_codes < ?""", UUID.class,
				tokenHash, Timestamp.from(now), maximumFailedCodes);
	}

	/** The person of a transaction. */
	public Long personOfTransaction(UUID transactionId) {
		return this.jdbc.queryForObject("SELECT person_id FROM login_transactions WHERE id = ?", Long.class,
				transactionId);
	}

	/** Counts a wrong second factor on the transaction; returns the new count. */
	public int incrementFailedCodes(byte[] tokenHash) {
		return this.jdbc.queryForObject("""
				UPDATE login_transactions SET failed_codes = failed_codes + 1 WHERE token_hash = ?
				RETURNING failed_codes""", Integer.class, tokenHash);
	}

	/** Deletes the transaction. */
	public void deleteTransaction(byte[] tokenHash) {
		this.jdbc.update("DELETE FROM login_transactions WHERE token_hash = ?", (Object) tokenHash);
	}

	/** Deletes the transactions of the system admin account. */
	public void deleteTransactionsOfAdmin(UUID systemAdminAccountId) {
		this.jdbc.update("DELETE FROM login_transactions WHERE system_admin_account_id = ?", systemAdminAccountId);
	}

	/** Deletes the OIDC login states that expired before the time. */
	public void deleteExpiredOidcStates(Instant now) {
		this.jdbc.update("DELETE FROM oidc_login_states WHERE expires_at < ?", Timestamp.from(now));
	}

	/** Stores a running OIDC login. */
	public void insertOidcState(byte[] stateHash, byte[] browserBindingHash, UUID authProviderId, String nonce,
			String codeVerifierEncrypted, String codeVerifierKeyId, String returnTo, Instant createdAt,
			Instant expiresAt) {
		this.jdbc.update("""
				INSERT INTO oidc_login_states (state_hash, browser_binding_hash, auth_provider_id, nonce,
				    code_verifier_encrypted, code_verifier_key_id, return_to, created_at, expires_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", stateHash, browserBindingHash, authProviderId, nonce,
				codeVerifierEncrypted, codeVerifierKeyId, returnTo, Timestamp.from(createdAt),
				Timestamp.from(expiresAt));
	}

	/** Deletes and returns the running login with the state hash (single use). */
	public Optional<OidcState> consumeOidcState(byte[] stateHash) {
		return this.jdbc.query("""
				DELETE FROM oidc_login_states WHERE state_hash = ?
				RETURNING browser_binding_hash, auth_provider_id, nonce, code_verifier_encrypted,
				          code_verifier_key_id, return_to, expires_at""",
				(rs, n) -> new OidcState(rs.getBytes(1), rs.getObject(2, UUID.class), rs.getString(3),
						rs.getString(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant()),
				(Object) stateHash)
			.stream()
			.findFirst();
	}

}
