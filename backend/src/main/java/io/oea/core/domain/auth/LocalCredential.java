package io.oea.core.domain.auth;

import io.oea.core.domain.NewAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Local credential of the system admin account or of a person (business object local-credential). */
@Entity
@Table(name = "local_credentials")
public class LocalCredential extends NewAwareEntity<UUID> {

	@Id
	private UUID id;

	@Column(name = "system_admin_account_id", updatable = false)
	private UUID systemAdminAccountId;

	@Column(name = "person_id", updatable = false)
	private Long personId;

	@Column(nullable = false, updatable = false)
	private String type;

	@Column(nullable = false)
	private String status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	protected LocalCredential() {
	}

	private LocalCredential(UUID systemAdminAccountId, Long personId, String type, Instant createdAt) {
		this.id = UUID.randomUUID();
		this.systemAdminAccountId = systemAdminAccountId;
		this.personId = personId;
		this.type = type;
		this.status = "active";
		this.createdAt = createdAt;
	}

	public static LocalCredential password(SystemAdminAccount owner, Instant now) {
		return new LocalCredential(owner.getId(), null, "password", now);
	}

	/**
	 * TOTP credential whose enrollment still has to be confirmed (status {@code pending}).
	 * @param ownerAccountId the owning system admin account, or {@code null}
	 * @param ownerPersonId the owning person, or {@code null}
	 */
	public static LocalCredential pendingTotp(UUID ownerAccountId, Long ownerPersonId, Instant now) {
		LocalCredential credential = new LocalCredential(ownerAccountId, ownerPersonId, "totp", now);
		credential.status = "pending";
		return credential;
	}

	public boolean isPendingSince(Instant now, Duration maximumAge) {
		return "pending".equals(this.status) && this.createdAt.plus(maximumAge).isAfter(now);
	}

	public void activate() {
		this.status = "active";
	}

	public void markUsed(Instant now) {
		this.lastUsedAt = now;
	}

	@Override
	public UUID getId() {
		return this.id;
	}

	public UUID getSystemAdminAccountId() {
		return this.systemAdminAccountId;
	}

	public Long getPersonId() {
		return this.personId;
	}

	public String getType() {
		return this.type;
	}

	public String getStatus() {
		return this.status;
	}

}
