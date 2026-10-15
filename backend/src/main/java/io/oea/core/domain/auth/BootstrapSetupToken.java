package io.oea.core.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.MessageDigest;
import java.time.Instant;

/** Hash of the one-time setup token while no system admin account exists (REQ-017). */
@Entity
@Table(name = "bootstrap_setup_tokens")
public class BootstrapSetupToken {

	public static final short SINGLE_ROW = 1;

	@Id
	private Short id;

	@Column(name = "token_hash", nullable = false)
	private byte[] tokenHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected BootstrapSetupToken() {
	}

	public BootstrapSetupToken(byte[] tokenHash, Instant createdAt, Instant expiresAt) {
		this.id = SINGLE_ROW;
		this.tokenHash = tokenHash.clone();
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	/** Replaces the stored token (a restart issues a new one). */
	public void replace(byte[] tokenHash, Instant createdAt, Instant expiresAt) {
		this.tokenHash = tokenHash.clone();
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	/** Constant-time comparison of the presented token hash; expired tokens never match. */
	public boolean matches(byte[] presentedHash, Instant now) {
		return now.isBefore(this.expiresAt) && MessageDigest.isEqual(this.tokenHash, presentedHash);
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

}
