package io.oea.core.access;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Access tokens of service accounts (V31; design D2, D7 of s12-client-credentials-token, ADR-050):
 * only the SHA-256 hash of a token is stored, with the client, the secret version that obtained
 * it, its issue time, its expiry and its revocation.
 */
@Repository
public class AccessTokenStore {

	/** The lifetime of every token (ADR-050 rule 3, REQ-176). */
	public static final Duration LIFETIME = Duration.ofSeconds(900);

	/** A valid token with the service account it authenticates. */
	public record ValidToken(UUID id, UUID clientId, long personId, String name, Instant issuedAt,
			Instant expiresAt) {
	}

	private final JdbcTemplate jdbc;

	public AccessTokenStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Stores a new token; it expires {@link #LIFETIME} after {@code issuedAt}. */
	public void insert(UUID id, byte[] hash, UUID clientId, UUID credentialId, Instant issuedAt) {
		this.jdbc.update("""
				INSERT INTO access_tokens (id, token_hash, client_id, credential_id, issued_at, expires_at)
				VALUES (?, ?, ?, ?, ?, ?)""", id, hash, clientId, credentialId, Timestamp.from(issuedAt),
				Timestamp.from(issuedAt.plus(LIFETIME)));
	}

	/**
	 * The token with the hash if it is not revoked, expires after {@code now} and belongs to an
	 * active service account.
	 */
	public Optional<ValidToken> findValid(byte[] hash, Instant now) {
		return this.jdbc.query("""
				SELECT t.id, t.client_id, p.id, p.name, t.issued_at, t.expires_at
				FROM access_tokens t
				JOIN machine_credentials c ON c.id = t.credential_id
				JOIN persons p ON p.id = c.person_id
				WHERE t.token_hash = ? AND t.revoked_at IS NULL AND t.expires_at > ?
				  AND p.person_type = 'service-account' AND p.status = 'active'""",
				(rs, n) -> new ValidToken(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getLong(3),
						rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant()),
				hash, Timestamp.from(now))
			.stream()
			.findFirst();
	}

	/**
	 * Revokes every unrevoked token of the client that has not expired at {@code now}.
	 * @return the IDs of the revoked tokens
	 */
	public List<UUID> revokeAll(UUID clientId, String reason, Instant now) {
		return this.jdbc.queryForList("""
				UPDATE access_tokens SET revoked_at = ?, revocation_reason = ?
				WHERE client_id = ? AND revoked_at IS NULL AND expires_at > ?
				RETURNING id""", UUID.class, Timestamp.from(now), reason, clientId, Timestamp.from(now));
	}

	/** Deletes the tokens that expired at or before {@code before}. */
	public int deleteExpiredBefore(Instant before) {
		return this.jdbc.update("DELETE FROM access_tokens WHERE expires_at <= ?", Timestamp.from(before));
	}

	/** Records a successful token grant on the secret version (the last use of the service account). */
	public void markUsed(UUID credentialId, Instant now) {
		this.jdbc.update("UPDATE machine_credentials SET last_used_at = ? WHERE id = ?", Timestamp.from(now), credentialId);
	}

}
