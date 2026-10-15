package io.oea.core.domain.auth;

import io.oea.core.domain.NewAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** TOTP secret of a local credential, encrypted with the key-encryption key (REQ-188, REQ-170). */
@Entity
@Table(name = "totp_credentials")
public class TotpCredential extends NewAwareEntity<UUID> {

	@Id
	@Column(name = "credential_id")
	private UUID credentialId;

	@Column(name = "secret_encrypted", nullable = false)
	private String secretEncrypted;

	@Column(name = "key_id", nullable = false)
	private String keyId;

	@Column(nullable = false, updatable = false)
	private String algorithm;

	@Column(nullable = false, updatable = false)
	private short digits;

	@Column(name = "period_seconds", nullable = false, updatable = false)
	private short periodSeconds;

	@Column(name = "last_used_time_step")
	private Long lastUsedTimeStep;

	private String label;

	protected TotpCredential() {
	}

	public TotpCredential(UUID credentialId, String secretEncrypted, String keyId, String label) {
		this.credentialId = credentialId;
		this.secretEncrypted = secretEncrypted;
		this.keyId = keyId;
		this.algorithm = "SHA1";
		this.digits = 6;
		this.periodSeconds = 30;
		this.label = label;
	}

	/** Additional authenticated data that binds the encrypted secret to this row. */
	public static String encryptionContext(UUID credentialId) {
		return "totp_credentials.secret_encrypted:" + credentialId;
	}

	/** True if the time step was not used before (REQ-170); records it. */
	public boolean acceptTimeStep(long timeStep) {
		if (this.lastUsedTimeStep != null && timeStep <= this.lastUsedTimeStep) {
			return false;
		}
		this.lastUsedTimeStep = timeStep;
		return true;
	}

	public void reencrypt(String secretEncrypted, String keyId) {
		this.secretEncrypted = secretEncrypted;
		this.keyId = keyId;
	}

	@Override
	public UUID getId() {
		return this.credentialId;
	}

	public String getSecretEncrypted() {
		return this.secretEncrypted;
	}

	public String getKeyId() {
		return this.keyId;
	}

	public String getLabel() {
		return this.label;
	}

}
