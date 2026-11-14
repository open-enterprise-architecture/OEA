package io.oea.core.access;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Service accounts and their client secrets (V30; design D1, D5 of us-142-service-accounts). A
 * service account is a person with {@code person_type = 'service-account'}; each secret version is
 * a row of {@code machine_credentials} with the stable client ID of the account. A rotation marks
 * the active row {@code rotated} and inserts a new one; at most one row per client ID is active.
 */
@Repository
public class ServiceAccountStore {

	/** The principal that creates, rotates or revokes: a person or the local system admin account. */
	public record Actor(Long personId, UUID adminId) {
	}

	/**
	 * A service account as read by administrators, never with a secret or its hash. The roles are
	 * those of its granted local assignments that are valid at the time of the read;
	 * {@code secretExpiresAt} is null without an active secret.
	 */
	public record Account(UUID clientId, long personId, String name, String description, String status,
			List<String> roles, Instant createdAt, Instant lastUsedAt, Instant rotatedAt, Instant secretExpiresAt) {

		public boolean active() {
			return "active".equals(this.status);
		}
	}

	/** The active secret of a client, for the check at the token endpoint (change s12-client-credentials-token). */
	public record ActiveSecret(UUID id, UUID clientId, long personId, String hash, Instant expiresAt) {
	}

	private final JdbcTemplate jdbc;

	public ServiceAccountStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Inserts the person of a new service account with status {@code active}.
	 * @return the person ID
	 * @throws org.springframework.dao.DuplicateKeyException if a service account has the name,
	 * ignoring case
	 */
	public long insertAccount(String name, String description, Instant now) {
		Long id = this.jdbc.queryForObject("""
				INSERT INTO persons (person_type, name, description, status, status_changed_at, created_at)
				VALUES ('service-account', ?, ?, 'active', ?, ?) RETURNING id""", Long.class, name, description,
				Timestamp.from(now), Timestamp.from(now));
		return id;
	}

	/** Inserts an active secret of the client. The caller has marked the previous one {@code rotated}. */
	public void insertSecret(UUID clientId, long personId, String hash, Instant createdAt, Instant expiresAt,
			Actor actor) {
		this.jdbc.update("""
				INSERT INTO machine_credentials (client_id, person_id, client_secret_hash, status, created_at,
				                                 created_by_person_id, created_by_admin_id, expires_at)
				VALUES (?, ?, ?, 'active', ?, ?, ?, ?)""", clientId, personId, hash, Timestamp.from(createdAt),
				actor.personId(), actor.adminId(), Timestamp.from(expiresAt));
	}

	/** All service accounts, by name; {@code active} null for all, true or false for one status. */
	public List<Account> list(Boolean active, Instant now) {
		String filter = (active == null) ? "" : (active ? " AND p.status = 'active'" : " AND p.status <> 'active'");
		return this.jdbc.query(SELECT + filter + " ORDER BY lower(p.name), p.id", (rs, n) -> account(rs),
				Timestamp.from(now), Timestamp.from(now));
	}

	/** One service account by its client ID. */
	public Optional<Account> find(UUID clientId, Instant now) {
		return this.jdbc
			.query(SELECT + " AND p.id = (SELECT person_id FROM machine_credentials WHERE client_id = ? LIMIT 1)",
					(rs, n) -> account(rs), Timestamp.from(now), Timestamp.from(now), clientId)
			.stream()
			.findFirst();
	}

	/**
	 * Locks the person of a service account for a change, so that concurrent rotations and
	 * deactivations are serialized.
	 * @return the person ID, empty for an unknown client ID
	 */
	public Optional<Long> lock(UUID clientId) {
		return this.jdbc.queryForList("""
				SELECT p.id FROM persons p
				WHERE p.person_type = 'service-account'
				  AND p.id = (SELECT person_id FROM machine_credentials WHERE client_id = ? LIMIT 1)
				FOR UPDATE""", Long.class, clientId).stream().findFirst();
	}

	/**
	 * Changes name and description.
	 * @throws org.springframework.dao.DuplicateKeyException if another service account has the
	 * name, ignoring case
	 */
	public void update(long personId, String name, String description) {
		this.jdbc.update("UPDATE persons SET name = ?, description = ? WHERE id = ? AND person_type = 'service-account'",
				name, description, personId);
	}

	/** Marks the active secret of the client {@code rotated}; it is invalid from then on. */
	public int rotateActive(UUID clientId, Instant now, Actor actor) {
		return endActive(clientId, "rotated", now, actor);
	}

	/** Sets the account {@code inactive} and revokes its active secret. */
	public void deactivate(UUID clientId, long personId, Instant now, Actor actor) {
		endActive(clientId, "revoked", now, actor);
		this.jdbc.update("""
				UPDATE persons SET status = 'inactive', status_changed_at = ?, status_changed_by_person_id = ?,
				       status_changed_by_admin_id = ?
				WHERE id = ? AND person_type = 'service-account'""", Timestamp.from(now), actor.personId(),
				actor.adminId(), personId);
	}

	/** The active secret of an active service account. */
	public Optional<ActiveSecret> activeSecret(UUID clientId) {
		return this.jdbc.query("""
				SELECT c.id, c.client_id, c.person_id, c.client_secret_hash, c.expires_at
				FROM machine_credentials c JOIN persons p ON p.id = c.person_id
				WHERE c.client_id = ? AND c.status = 'active' AND p.status = 'active'""",
				(rs, n) -> new ActiveSecret(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getLong(3),
						rs.getString(4), rs.getTimestamp(5).toInstant()),
				clientId)
			.stream()
			.findFirst();
	}

	/** The status of every secret version of a client, oldest first. */
	public List<String> secretStatuses(UUID clientId) {
		return this.jdbc.queryForList(
				"SELECT status FROM machine_credentials WHERE client_id = ? ORDER BY created_at, id", String.class,
				clientId);
	}

	private int endActive(UUID clientId, String status, Instant now, Actor actor) {
		return this.jdbc.update("""
				UPDATE machine_credentials SET status = ?, revoked_at = ?, revoked_by_person_id = ?, revoked_by_admin_id = ?
				WHERE client_id = ? AND status = 'active'""", status, Timestamp.from(now), actor.personId(),
				actor.adminId(), clientId);
	}

	private static final String SELECT = """
			SELECT (SELECT c.client_id FROM machine_credentials c WHERE c.person_id = p.id LIMIT 1) AS client_id,
			       p.id, p.name, p.description, p.status, p.created_at,
			       ARRAY(SELECT a.role_id FROM role_assignments a
			             WHERE a.person_id = p.id AND a.status = 'granted' AND a.valid_from <= ?
			               AND (a.valid_to IS NULL OR a.valid_to > ?)
			             ORDER BY a.role_id) AS roles,
			       (SELECT max(c.last_used_at) FROM machine_credentials c WHERE c.person_id = p.id) AS last_used_at,
			       (SELECT max(c.revoked_at) FROM machine_credentials c
			        WHERE c.person_id = p.id AND c.status = 'rotated') AS rotated_at,
			       (SELECT c.expires_at FROM machine_credentials c
			        WHERE c.person_id = p.id AND c.status = 'active') AS expires_at
			FROM persons p
			WHERE p.person_type = 'service-account'""";

	private static Account account(ResultSet rs) throws SQLException {
		Array roles = rs.getArray("roles");
		return new Account(rs.getObject("client_id", UUID.class), rs.getLong("id"), rs.getString("name"),
				rs.getString("description"), rs.getString("status"),
				(roles != null) ? Arrays.asList((String[]) roles.getArray()) : List.of(),
				rs.getTimestamp("created_at").toInstant(), instant(rs, "last_used_at"), instant(rs, "rotated_at"),
				instant(rs, "expires_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return (value != null) ? value.toInstant() : null;
	}

}
