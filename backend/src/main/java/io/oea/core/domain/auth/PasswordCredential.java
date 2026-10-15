package io.oea.core.domain.auth;

import io.oea.core.domain.NewAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Password of a local credential: argon2id PHC string only (REQ-169). */
@Entity
@Table(name = "password_credentials")
public class PasswordCredential extends NewAwareEntity<UUID> {

	@Id
	@Column(name = "credential_id")
	private UUID credentialId;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false, updatable = false)
	private String origin;

	protected PasswordCredential() {
	}

	private PasswordCredential(UUID credentialId, String passwordHash, String origin) {
		if (!passwordHash.startsWith("$argon2id$")) {
			throw new IllegalArgumentException("only argon2id hashes are stored (REQ-169)");
		}
		this.credentialId = credentialId;
		this.passwordHash = passwordHash;
		this.origin = origin;
	}

	/** A password chosen by the owner; no expiry (REQ-173 applies to passwords set by others). */
	public static PasswordCredential selfSet(LocalCredential credential, String passwordHash) {
		return new PasswordCredential(credential.getId(), passwordHash, "self-set");
	}

	@Override
	public UUID getId() {
		return this.credentialId;
	}

	/** Rehash with current parameters after a successful login (REQ-169). */
	public void rehash(String passwordHash) {
		if (!passwordHash.startsWith("$argon2id$")) {
			throw new IllegalArgumentException("only argon2id hashes are stored (REQ-169)");
		}
		this.passwordHash = passwordHash;
	}

	public UUID getCredentialId() {
		return this.credentialId;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public String getOrigin() {
		return this.origin;
	}

}
