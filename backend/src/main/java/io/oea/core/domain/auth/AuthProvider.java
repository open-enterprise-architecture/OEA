package io.oea.core.domain.auth;

import io.oea.core.domain.NewAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An OIDC identity provider (business object auth-provider); the client secret is encrypted (REQ-188). */
@Entity
@Table(name = "auth_providers")
public class AuthProvider extends NewAwareEntity<UUID> {

	@Id
	private UUID id;

	@Column(name = "provider_type", nullable = false)
	private String providerType;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String issuer;

	@Column(name = "client_id", nullable = false)
	private String clientId;

	@Column(name = "client_secret_encrypted")
	private String clientSecretEncrypted;

	@Column(name = "client_secret_key_id")
	private String clientSecretKeyId;

	@Column(nullable = false)
	private String scopes;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	protected AuthProvider() {
	}

	public AuthProvider(UUID id, Instant now) {
		this.id = id;
		this.createdAt = now;
	}

	/** Additional authenticated data that binds the encrypted client secret to this row. */
	public static String encryptionContext(UUID id) {
		return "auth_providers.client_secret_encrypted:" + id;
	}

	public void configure(String providerType, String name, String issuer, String clientId, String scopes,
			boolean active, Instant now) {
		this.providerType = providerType;
		this.name = name;
		this.issuer = issuer;
		this.clientId = clientId;
		this.scopes = scopes;
		this.active = active;
		if (!isNew()) {
			this.updatedAt = now;
		}
	}

	public void storeClientSecret(String encrypted, String keyId) {
		this.clientSecretEncrypted = encrypted;
		this.clientSecretKeyId = keyId;
	}

	@Override
	public UUID getId() {
		return this.id;
	}

	public String getProviderType() {
		return this.providerType;
	}

	public String getName() {
		return this.name;
	}

	public String getIssuer() {
		return this.issuer;
	}

	public String getClientId() {
		return this.clientId;
	}

	public String getScopes() {
		return this.scopes;
	}

	public boolean isActive() {
		return this.active;
	}

	public String getClientSecretEncrypted() {
		return this.clientSecretEncrypted;
	}

	public String getClientSecretKeyId() {
		return this.clientSecretKeyId;
	}

	public boolean hasClientSecret() {
		return this.clientSecretEncrypted != null;
	}

}
